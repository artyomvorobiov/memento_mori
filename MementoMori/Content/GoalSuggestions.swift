import Foundation

/// Подсказки при создании цели: похожие идеи, категория и страна по тексту.
enum GoalSuggestions {
    /// Идеи из библиотеки, похожие на введённый текст.
    static func ideas(matching text: String, excluding existingTitles: Set<String> = [], limit: Int = 3) -> [Idea] {
        let query = normalized(text)
        guard query.count >= 3 else { return [] }
        let words = query.split(whereSeparator: { !$0.isLetter && !$0.isNumber })
            .map(String.init)
            .filter { $0.count >= 3 && !stopWords.contains($0) }
        let excluded = Set(existingTitles.map(normalized))
        let scored: [(idea: Idea, score: Int)] = IdeaLibrary.all.compactMap { idea in
            let title = normalized(idea.title)
            guard title != query, !excluded.contains(title) else { return nil }
            if title.contains(query) { return (idea, 100) }
            // Слова запроса с учётом окончаний: «японию» находит «Японии».
            let score = words.filter { title.contains(stem($0)) }.count
            return score > 0 ? (idea, score) : nil
        }
        // Чем больше совпавших слов, тем выше; при равенстве — порядок библиотеки.
        let ranked = scored.enumerated().sorted { lhs, rhs in
            lhs.element.score != rhs.element.score ? lhs.element.score > rhs.element.score : lhs.offset < rhs.offset
        }
        return ranked.prefix(limit).map(\.element.idea)
    }

    /// Слова, которые есть почти в каждой цели и ничего не говорят о ней.
    private static let stopWords: Set<String> = [
        "поехать", "съездить", "побывать", "посетить", "увидеть", "сделать", "хочу", "очень",
        "свой", "свою", "свое", "чтобы", "когда", "года", "году", "лет", "раз",
    ]

    /// Категория по ключевым словам: «Прочитать…» → книги, «Пробежать…» → тело.
    static func category(for text: String) -> GoalCategory? {
        let value = normalized(text)
        guard value.count >= 3 else { return nil }
        for (category, stems) in rules where stems.contains(where: { value.contains($0) }) {
            return category
        }
        if country(in: text) != nil {
            return .travel
        }
        return nil
    }

    /// Страна, упомянутая в тексте: «Поехать в Японию» → JP.
    static func country(in text: String) -> String? {
        let words = normalized(text)
            .split(whereSeparator: { !$0.isLetter })
            .map(String.init)
        guard !words.isEmpty else { return nil }
        for word in words {
            if let code = aliases[word] { return code }
        }
        for entry in countryStems where words.contains(where: { $0.hasPrefix(entry.stem) }) {
            return entry.code
        }
        return nil
    }

    // MARK: - Внутреннее

    static func normalized(_ text: String) -> String {
        text.lowercased()
            .replacingOccurrences(of: "ё", with: "е")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Основа слова без окончания: «японию» → «япон».
    private static func stem(_ word: String) -> String {
        word.count > 5 ? String(word.prefix(word.count - 2)) : word
    }

    /// Порядок важен: «Снять фильм» — это «Создать», а не «Фильмы».
    private static let rules: [(GoalCategory, [String])] = [
        (.create, ["написать", "создать", "своими руками", "запустить свое", "построить", "посадить", "нарисовать",
                   "сочинить", "снять фильм", "снять корот", "записать песн", "открыть свое", "собрать альбом"]),
        (.movies, ["фильм", "кино", "сериал", "мультфильм", "пересмотреть"]),
        (.books, ["прочитать", "прочесть", "перечитать", "книг", "роман"]),
        (.travel, ["поехать", "съездить", "побывать", "посетить", "слетать", "путешеств", "поездк", "отпуск",
                   "автостоп", "кругосвет"]),
        (.skills, ["научиться", "выучить", "освоить", "изучить", "курс", "права", "язык", "получить диплом"]),
        (.body, ["пробежать", "марафон", "подтянуться", "отжаться", "похудеть", "бегать", "трениров", "шпагат",
                 "йог", "пресс", "проплыть", "покорить", "вершин", "подняться на", "спорт", "зож"]),
        (.people, ["родител", "маме", "маму", "папе", "папу", "бабушк", "дедушк", "друз", "семь", "детьми",
                   "сыном", "дочерью", "помирить", "поблагодарить", "позвонить", "свидани"]),
        (.experience, ["прыгнуть", "нырнуть", "полетать", "попробовать", "рассвет", "закат", "концерт", "фестивал",
                       "увидеть", "встретить", "переночевать", "прокатиться"]),
    ]

    /// Короткие и разговорные названия, которые проверяем целым словом.
    private static let aliases: [String: String] = [
        "сша": "US", "америку": "US", "америке": "US", "америка": "US", "штаты": "US",
        "англию": "GB", "англии": "GB", "англия": "GB", "британию": "GB", "лондон": "GB",
        "оаэ": "AE", "эмираты": "AE", "дубай": "AE",
        "голландию": "NL", "голландии": "NL",
        "чехию": "CZ", "чехии": "CZ",
        "корею": "KR", "корее": "KR", "корея": "KR",
        "кубу": "CU", "кубе": "CU", "куба": "CU",
        "перу": "PE", "чили": "CL", "оман": "OM", "лаос": "LA", "иран": "IR", "ирак": "IQ", "катар": "QA",
        "фиджи": "FJ", "бали": "ID", "париж": "FR", "рим": "IT", "риме": "IT", "токио": "JP",
    ]

    /// Основы названий стран, длинные — первыми, чтобы «Южная Корея» не перехватывалась чем-то короче.
    private static let countryStems: [(code: String, stem: String)] = {
        CountryCatalog.all
            .compactMap { country -> (code: String, stem: String)? in
                let name = normalized(country.name)
                guard !name.contains("-"), !name.contains("("), !name.contains("’") else { return nil }
                let word = name.split(separator: " ").map(String.init).max { $0.count < $1.count } ?? name
                guard word.count >= 5 else { return nil }
                return (country.code, String(word.prefix(max(4, word.count - 2))))
            }
            .sorted { $0.stem.count > $1.stem.count }
    }()
}

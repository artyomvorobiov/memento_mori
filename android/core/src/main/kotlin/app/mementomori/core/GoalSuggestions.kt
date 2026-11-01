package app.mementomori.core

/** Подсказки при создании цели: похожие идеи, категория и страна по тексту. */
object GoalSuggestions {
    /** Идеи из библиотеки, похожие на введённый текст. */
    fun ideas(text: String, excludingTitles: Set<String> = emptySet(), limit: Int = 3): List<Idea> {
        val query = normalized(text)
        if (query.length < 3) return emptyList()
        val words = query.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in stopWords }
        val excluded = excludingTitles.map(::normalized).toSet()
        val scored = IdeaLibrary.all.mapIndexedNotNull { index, idea ->
            val title = normalized(idea.title)
            if (title == query || title in excluded) return@mapIndexedNotNull null
            if (title.contains(query)) return@mapIndexedNotNull Triple(idea, 100, index)
            // Слова запроса с учётом окончаний: «японию» находит «Японии».
            val score = words.count { title.contains(stem(it)) }
            if (score > 0) Triple(idea, score, index) else null
        }
        // Чем больше совпавших слов, тем выше; при равенстве — порядок библиотеки.
        return scored.sortedWith(compareByDescending<Triple<Idea, Int, Int>> { it.second }.thenBy { it.third })
            .take(limit)
            .map { it.first }
    }

    /** Слова, которые есть почти в каждой цели и ничего не говорят о ней. */
    private val stopWords = setOf(
        "поехать", "съездить", "побывать", "посетить", "увидеть", "сделать", "хочу", "очень",
        "свой", "свою", "свое", "чтобы", "когда", "года", "году", "лет", "раз",
    )

    /** Категория по ключевым словам: «Прочитать…» → книги, «Пробежать…» → тело. */
    fun category(text: String): GoalCategory? {
        val value = normalized(text)
        if (value.length < 3) return null
        for ((category, stems) in rules) {
            if (stems.any { value.contains(it) }) return category
        }
        return if (country(text) != null) GoalCategory.TRAVEL else null
    }

    /** Страна, упомянутая в тексте: «Поехать в Японию» → JP. */
    fun country(text: String): String? {
        val words = normalized(text).split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        for (word in words) aliases[word]?.let { return it }
        for ((code, stem) in countryStems) {
            if (words.any { it.startsWith(stem) }) return code
        }
        return null
    }

    fun normalized(text: String): String = text.lowercase(Format.locale).replace('ё', 'е').trim()

    /** Основа слова без окончания: «японию» → «япон». */
    private fun stem(word: String): String = if (word.length > 5) word.dropLast(2) else word

    /** Порядок важен: «Снять фильм» — это «Создать», а не «Фильмы». */
    private val rules: List<Pair<GoalCategory, List<String>>> = listOf(
        GoalCategory.CREATE to listOf(
            "написать", "создать", "своими руками", "запустить свое", "построить", "посадить", "нарисовать",
            "сочинить", "снять фильм", "снять корот", "записать песн", "открыть свое", "собрать альбом",
        ),
        GoalCategory.MOVIES to listOf("фильм", "кино", "сериал", "мультфильм", "пересмотреть"),
        GoalCategory.BOOKS to listOf("прочитать", "прочесть", "перечитать", "книг", "роман"),
        GoalCategory.TRAVEL to listOf(
            "поехать", "съездить", "побывать", "посетить", "слетать", "путешеств", "поездк", "отпуск",
            "автостоп", "кругосвет",
        ),
        GoalCategory.SKILLS to listOf("научиться", "выучить", "освоить", "изучить", "курс", "права", "язык", "получить диплом"),
        GoalCategory.BODY to listOf(
            "пробежать", "марафон", "подтянуться", "отжаться", "похудеть", "бегать", "трениров", "шпагат",
            "йог", "пресс", "проплыть", "покорить", "вершин", "подняться на", "спорт", "зож",
        ),
        GoalCategory.PEOPLE to listOf(
            "родител", "маме", "маму", "папе", "папу", "бабушк", "дедушк", "друз", "семь", "детьми",
            "сыном", "дочерью", "помирить", "поблагодарить", "позвонить", "свидани",
        ),
        GoalCategory.EXPERIENCE to listOf(
            "прыгнуть", "нырнуть", "полетать", "попробовать", "рассвет", "закат", "концерт", "фестивал",
            "увидеть", "встретить", "переночевать", "прокатиться",
        ),
    )

    /** Короткие и разговорные названия, которые проверяем целым словом. */
    private val aliases = mapOf(
        "сша" to "US", "америку" to "US", "америке" to "US", "америка" to "US", "штаты" to "US",
        "англию" to "GB", "англии" to "GB", "англия" to "GB", "британию" to "GB", "лондон" to "GB",
        "оаэ" to "AE", "эмираты" to "AE", "дубай" to "AE",
        "голландию" to "NL", "голландии" to "NL",
        "чехию" to "CZ", "чехии" to "CZ",
        "корею" to "KR", "корее" to "KR", "корея" to "KR",
        "кубу" to "CU", "кубе" to "CU", "куба" to "CU",
        "перу" to "PE", "чили" to "CL", "оман" to "OM", "лаос" to "LA", "иран" to "IR", "ирак" to "IQ", "катар" to "QA",
        "фиджи" to "FJ", "бали" to "ID", "париж" to "FR", "рим" to "IT", "риме" to "IT", "токио" to "JP",
    )

    /** Основы названий стран, длинные — первыми, чтобы «Южная Корея» не перехватывалась чем-то короче. */
    private val countryStems: List<Pair<String, String>> by lazy {
        CountryCatalog.all.mapNotNull { country ->
            val name = normalized(country.name)
            if (name.contains("-") || name.contains("(") || name.contains("’")) return@mapNotNull null
            val word = name.split(" ").maxByOrNull { it.length } ?: name
            if (word.length < 5) return@mapNotNull null
            country.code to word.take(maxOf(4, word.length - 2))
        }.sortedByDescending { it.second.length }
    }
}

import SwiftUI

struct GoalEditorView: View {
    enum DeadlineMode: String, CaseIterable, Identifiable {
        case none
        case age
        case date

        var id: String { rawValue }

        var title: String {
            switch self {
            case .none: "Без срока"
            case .age: "К возрасту"
            case .date: "К дате"
            }
        }
    }

    /// Быстрый выбор срока: «до конца года», «через год», «к 40 годам».
    struct DeadlinePreset: Identifiable {
        let id: String
        let title: String
        let mode: DeadlineMode
        var date: Date? = nil
        var age: Int? = nil
    }

    private enum Field: Hashable {
        case title
        case newStep
    }

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var draft: Goal
    @State private var deadlineMode: DeadlineMode
    @State private var targetAge: Int
    @State private var targetDate: Date
    /// Категорию выбрали руками — больше не угадываем её по тексту.
    @State private var categoryTouched: Bool
    @State private var dismissedCountry: String?
    @State private var newStepTitle = ""
    @State private var showDeleteConfirmation = false
    @FocusState private var focusedField: Field?
    private let isNew: Bool

    init(goal: Goal?, presetCategory: GoalCategory? = nil, initialTitle: String = "") {
        let initial = goal ?? Goal(title: initialTitle, category: presetCategory ?? .experience)
        _draft = State(initialValue: initial)
        let mode: DeadlineMode = initial.targetAge != nil ? .age : (initial.targetDate != nil ? .date : .none)
        _deadlineMode = State(initialValue: mode)
        _targetAge = State(initialValue: initial.targetAge ?? 0)
        let nextYear = Calendar.memento.date(byAdding: .year, value: 1, to: Date()) ?? Date()
        _targetDate = State(initialValue: initial.targetDate ?? nextYear)
        _categoryTouched = State(initialValue: goal != nil || presetCategory != nil)
        isNew = goal == nil
    }

    var body: some View {
        NavigationStack {
            Form {
                titleSection
                categorySection
                deadlineSection
                stepsSection
                Section("Зачем?") {
                    TextField("Почему это важно для тебя", text: $draft.note, axis: .vertical)
                        .lineLimit(2...8)
                }
                if !draft.isCompleted {
                    Section {
                        Toggle("В фокусе", isOn: $draft.isFocused)
                            .disabled(!draft.isFocused && !model.data.canFocus(excluding: draft.id))
                    } footer: {
                        Text(focusFooter)
                    }
                }
                if !isNew {
                    Section {
                        Button("Удалить цель", role: .destructive) {
                            showDeleteConfirmation = true
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle(isNew ? "Новая цель" : "Цель")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Сохранить", action: save)
                        .disabled(trimmedTitle.isEmpty)
                }
            }
            .confirmationDialog("Удалить цель?", isPresented: $showDeleteConfirmation, titleVisibility: .visible) {
                Button("Удалить", role: .destructive) {
                    model.deleteGoal(id: draft.id)
                    dismiss()
                }
            }
            .onChange(of: draft.title) { _, title in
                guard !categoryTouched, let category = GoalSuggestions.category(for: title) else { return }
                draft.category = category
            }
            .onAppear {
                if targetAge == 0 { targetAge = roundAges.first ?? 40 }
                if isNew, !categoryTouched, let category = GoalSuggestions.category(for: draft.title) {
                    draft.category = category
                }
                if isNew && DebugLaunch.newGoalTitle == nil { focusedField = .title }
            }
        }
    }

    // MARK: Название и подсказки

    private var titleSection: some View {
        Section {
            TextField("Что ты хочешь успеть?", text: $draft.title, axis: .vertical)
                .lineLimit(1...4)
                .focused($focusedField, equals: .title)
            ForEach(suggestions) { idea in
                Button {
                    apply(idea)
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "lightbulb")
                            .foregroundStyle(Theme.accent)
                        Text(idea.title)
                            .foregroundStyle(Color.white)
                            .multilineTextAlignment(.leading)
                        Spacer(minLength: 8)
                        Image(systemName: idea.category.symbol)
                            .foregroundStyle(Theme.tertiaryText)
                    }
                    .font(.subheadline)
                }
            }
            if let code = detectedCountry {
                HStack {
                    Button {
                        attachCountry(code)
                    } label: {
                        Label("Привязать страну: \(flagEmoji(for: code)) \(Country.name(for: code))", systemImage: "mappin.and.ellipse")
                            .font(.subheadline)
                    }
                    Spacer()
                    Button {
                        dismissedCountry = code
                    } label: {
                        Image(systemName: "xmark")
                            .font(.caption)
                            .foregroundStyle(Theme.tertiaryText)
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel("Не привязывать")
                }
            }
        } footer: {
            if !suggestions.isEmpty {
                Text("Похожие идеи из библиотеки — нажми, чтобы подставить.")
            }
        }
    }

    private var suggestions: [Idea] {
        guard isNew else { return [] }
        return GoalSuggestions.ideas(matching: draft.title, excluding: Set(model.data.goals.map(\.title)))
    }

    private var detectedCountry: String? {
        guard draft.countryCode == nil,
              let code = GoalSuggestions.country(in: draft.title),
              code != dismissedCountry
        else { return nil }
        return code
    }

    private func apply(_ idea: Idea) {
        draft.title = idea.title
        draft.category = idea.category
        categoryTouched = true
    }

    private func attachCountry(_ code: String) {
        draft.countryCode = code
        draft.category = .travel
        categoryTouched = true
    }

    // MARK: Категория

    private var categorySection: some View {
        Section {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(GoalCategory.allCases) { category in
                        categoryChip(category)
                    }
                }
                .padding(.vertical, 2)
            }
            if draft.category == .travel {
                NavigationLink {
                    CountryPickerView(selection: countryBinding)
                } label: {
                    HStack {
                        Text("Страна")
                        Spacer()
                        Text(countryLabel)
                            .foregroundStyle(Theme.secondaryText)
                    }
                }
            }
        } header: {
            Text("Категория")
        }
    }

    private func categoryChip(_ category: GoalCategory) -> some View {
        let isSelected = draft.category == category
        return Button {
            draft.category = category
            categoryTouched = true
        } label: {
            HStack(spacing: 6) {
                Image(systemName: category.symbol)
                    .font(.caption)
                Text(category.title)
                    .font(.subheadline)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(isSelected ? Theme.accent : Color.white.opacity(0.08), in: Capsule())
            .foregroundStyle(isSelected ? Color.black : Color.white)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    // MARK: Срок

    private var deadlineSection: some View {
        Section {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(presets) { preset in
                        presetChip(preset)
                    }
                }
                .padding(.vertical, 2)
            }
            Picker("Срок", selection: $deadlineMode) {
                ForEach(DeadlineMode.allCases) { mode in
                    Text(mode.title).tag(mode)
                }
            }
            .pickerStyle(.segmented)

            switch deadlineMode {
            case .none:
                EmptyView()
            case .age:
                Stepper(value: $targetAge, in: 1...120) {
                    Text(Format.byAge(targetAge).capitalizedFirst)
                        .monospacedDigit()
                }
            case .date:
                DatePicker("Дата", selection: $targetDate, displayedComponents: .date)
            }
        } header: {
            Text("Срок")
        } footer: {
            Text(deadlineFooter)
        }
    }

    private func presetChip(_ preset: DeadlinePreset) -> some View {
        let isSelected = isActive(preset)
        return Button {
            deadlineMode = preset.mode
            if let date = preset.date { targetDate = date }
            if let age = preset.age { targetAge = age }
        } label: {
            Text(preset.title)
                .font(.subheadline)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(isSelected ? Theme.accent : Color.white.opacity(0.08), in: Capsule())
                .foregroundStyle(isSelected ? Color.black : Color.white)
        }
        .buttonStyle(.plain)
    }

    private func isActive(_ preset: DeadlinePreset) -> Bool {
        guard preset.mode == deadlineMode else { return false }
        if let age = preset.age { return age == targetAge }
        if let date = preset.date { return Calendar.memento.isDate(date, inSameDayAs: targetDate) }
        return false
    }

    /// Ближайшие «круглые» возрасты впереди: 35 и 40, если сейчас 33.
    private var roundAges: [Int] {
        let age = model.stats()?.ageYears ?? 30
        let byFive = (age / 5 + 1) * 5
        var byTen = (age / 10 + 1) * 10
        if byTen == byFive { byTen += 10 }
        return [byFive, byTen].filter { $0 <= 120 }
    }

    private var presets: [DeadlinePreset] {
        let calendar = Calendar.memento
        let now = Date()
        var result: [DeadlinePreset] = []
        if let endOfYear = calendar.date(from: DateComponents(year: calendar.component(.year, from: now), month: 12, day: 31)) {
            result.append(DeadlinePreset(id: "year-end", title: "До конца года", mode: .date, date: endOfYear))
        }
        for (years, title) in [(1, "Через год"), (3, "Через 3 года"), (5, "Через 5 лет")] {
            if let date = calendar.date(byAdding: .year, value: years, to: calendar.startOfDay(for: now)) {
                result.append(DeadlinePreset(id: "in-\(years)", title: title, mode: .date, date: date))
            }
        }
        for age in roundAges {
            result.append(DeadlinePreset(id: "age-\(age)", title: Format.byAge(age).capitalizedFirst, mode: .age, age: age))
        }
        return result
    }

    private var deadlineFooter: String {
        guard let stats = model.stats() else { return "" }
        let deadline: Date
        switch deadlineMode {
        case .none:
            return "Цель со сроком сбывается чаще. Выбери вариант выше — или назначь свой."
        case .age:
            guard let date = Calendar.memento.date(byAdding: .year, value: targetAge, to: stats.birthDate) else { return "" }
            deadline = date
        case .date:
            deadline = targetDate
        }
        guard deadline > stats.now else {
            return "Этот срок уже прошёл — выбери другой."
        }
        let weeks = stats.weekStart(deadline).timeIntervalSince(stats.weekStart(stats.now)) / (7 * LifeStats.secondsPerDay)
        let weeksLeft = max(0, Int(weeks.rounded()))
        var text = "Это \(Format.monthYear(deadline)), \(Format.relative(from: stats.now, to: deadline))"
        if stats.remainingWeeks > 0 {
            let share = Double(weeksLeft) / Double(stats.remainingWeeks)
            let shareText = share < 0.01 ? "меньше 1%" : Format.percent(share, digits: 0)
            text += " — \(shareText) оставшейся жизни"
        }
        if deadlineMode == .date {
            text += ". Тебе будет \(Format.years(stats.age(at: deadline)))"
        }
        return text + "."
    }

    // MARK: Шаги

    private var stepsSection: some View {
        Section {
            ForEach($draft.steps) { $step in
                HStack(spacing: 10) {
                    Button {
                        step.isDone.toggle()
                    } label: {
                        Image(systemName: step.isDone ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(step.isDone ? Theme.accent : Theme.tertiaryText)
                    }
                    .buttonStyle(.borderless)
                    TextField("Шаг", text: $step.title, axis: .vertical)
                }
            }
            .onDelete { offsets in
                draft.steps.remove(atOffsets: offsets)
            }
            HStack(spacing: 10) {
                Image(systemName: "plus.circle.fill")
                    .foregroundStyle(Theme.accent)
                TextField("Добавить шаг", text: $newStepTitle)
                    .focused($focusedField, equals: .newStep)
                    .submitLabel(.next)
                    .onSubmit(addStep)
            }
            if draft.steps.isEmpty, !stepTemplates.isEmpty {
                Button {
                    draft.steps = stepTemplates.map { GoalStep(title: $0) }
                } label: {
                    Label("Предложить шаги", systemImage: "wand.and.stars")
                        .font(.subheadline)
                }
            }
        } header: {
            Text("Шаги")
        } footer: {
            Text("Большую цель проще начать с маленького шага. Ближайший шаг будет виден на главном экране и в виджете.")
        }
    }

    private var stepTemplates: [String] {
        switch draft.category {
        case .travel: ["Выбрать даты", "Купить билеты", "Забронировать жильё", "Составить маршрут"]
        case .skills: ["Найти курс или учителя", "Заниматься 3 раза в неделю", "Первый маленький результат"]
        case .body: ["Составить план тренировок", "Первая тренировка", "Промежуточная цель"]
        case .create: ["Набросать план", "Сделать первый черновик", "Показать кому-нибудь"]
        case .people: ["Выбрать день", "Договориться о встрече"]
        case .experience: ["Найти, где это можно сделать", "Выбрать дату", "Записаться"]
        case .movies, .books, .other: []
        }
    }

    private func addStep() {
        let title = newStepTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else { return }
        draft.steps.append(GoalStep(title: title))
        newStepTitle = ""
        focusedField = .newStep
    }

    // MARK: Остальное

    private var trimmedTitle: String {
        draft.title.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var countryLabel: String {
        guard let code = draft.countryCode else { return "Не выбрана" }
        return "\(flagEmoji(for: code)) \(Country.name(for: code))"
    }

    private var countryBinding: Binding<String?> {
        Binding(
            get: { draft.countryCode },
            set: { newCode in
                let previousName = draft.countryCode.map { Country.name(for: $0) }
                draft.countryCode = newCode
                if let newCode, trimmedTitle.isEmpty || trimmedTitle == previousName {
                    draft.title = Country.name(for: newCode)
                }
            }
        )
    }

    private var focusFooter: String {
        if !draft.isFocused && !model.data.canFocus(excluding: draft.id) {
            return "В фокусе уже три цели. Сначала убери одну из них."
        }
        return "Цели в фокусе видны на главном экране и в виджете. Не больше трёх."
    }

    private func save() {
        addStep()
        var goal = draft
        goal.title = trimmedTitle
        switch deadlineMode {
        case .none:
            goal.targetAge = nil
            goal.targetDate = nil
        case .age:
            goal.targetAge = targetAge
            goal.targetDate = nil
        case .date:
            goal.targetAge = nil
            goal.targetDate = targetDate
        }
        if goal.category != .travel {
            goal.countryCode = nil
        }
        model.saveGoal(goal)
        dismiss()
    }
}

struct CountryPickerView: View {
    @Binding var selection: String?
    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""

    var body: some View {
        List {
            if selection != nil {
                Button("Без страны", role: .destructive) {
                    selection = nil
                    dismiss()
                }
            }
            ForEach(filteredCountries) { country in
                Button {
                    selection = country.code
                    dismiss()
                } label: {
                    HStack(spacing: 12) {
                        Text(country.flag)
                            .font(.title3)
                        Text(country.name)
                            .foregroundStyle(Color.white)
                        Spacer()
                        if selection == country.code {
                            Image(systemName: "checkmark")
                                .foregroundStyle(Theme.accent)
                        }
                    }
                }
            }
        }
        .searchable(text: $searchText, prompt: "Найти страну")
        .navigationTitle("Страна")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var filteredCountries: [Country] {
        let query = searchText.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return CountryCatalog.all }
        return CountryCatalog.all.filter { $0.name.localizedCaseInsensitiveContains(query) }
    }
}

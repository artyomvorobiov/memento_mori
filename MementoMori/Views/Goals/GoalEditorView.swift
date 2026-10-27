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

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var draft: Goal
    @State private var deadlineMode: DeadlineMode
    @State private var targetAge: Int
    @State private var targetDate: Date
    @State private var showDeleteConfirmation = false
    @FocusState private var isTitleFocused: Bool
    private let isNew: Bool

    init(goal: Goal?, presetCategory: GoalCategory? = nil) {
        let initial = goal ?? Goal(title: "", category: presetCategory ?? .experience)
        _draft = State(initialValue: initial)
        let mode: DeadlineMode = initial.targetAge != nil ? .age : (initial.targetDate != nil ? .date : .none)
        _deadlineMode = State(initialValue: mode)
        _targetAge = State(initialValue: initial.targetAge ?? 0)
        let nextYear = Calendar.memento.date(byAdding: .year, value: 1, to: Date()) ?? Date()
        _targetDate = State(initialValue: initial.targetDate ?? nextYear)
        isNew = goal == nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Что ты хочешь успеть?", text: $draft.title, axis: .vertical)
                        .lineLimit(1...4)
                        .focused($isTitleFocused)
                }

                Section("Категория") {
                    Picker("Категория", selection: $draft.category) {
                        ForEach(GoalCategory.allCases) { category in
                            Label(category.title, systemImage: category.symbol)
                                .tag(category)
                        }
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
                }

                Section {
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
            .onAppear {
                if targetAge == 0 { targetAge = defaultTargetAge }
                if isNew { isTitleFocused = true }
            }
        }
    }

    // MARK: Вспомогательное

    private var trimmedTitle: String {
        draft.title.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var defaultTargetAge: Int {
        let age = model.stats()?.ageYears ?? 30
        return min(120, age + 5)
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

    private var deadlineFooter: String {
        switch deadlineMode {
        case .none:
            return "Срок необязателен, но цель со сроком сбывается чаще."
        case .age:
            guard let stats = model.stats(),
                  let deadline = Calendar.memento.date(byAdding: .year, value: targetAge, to: stats.birthDate)
            else { return "" }
            if deadline < Date() {
                return "Этот возраст уже позади — выбери другой."
            }
            return "Это \(Format.monthYear(deadline)), \(Format.relative(from: Date(), to: deadline))."
        case .date:
            guard let stats = model.stats() else { return "" }
            return "Тебе будет \(Format.years(stats.age(at: targetDate)))."
        }
    }

    private var focusFooter: String {
        if !draft.isFocused && !model.data.canFocus(excluding: draft.id) {
            return "В фокусе уже три цели. Сначала убери одну из них."
        }
        return "Цели в фокусе видны на главном экране и в виджете. Не больше трёх."
    }

    private func save() {
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

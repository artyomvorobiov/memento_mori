import SwiftUI

enum GoalSegment: String, CaseIterable, Identifiable {
    case active
    case done

    var id: String { rawValue }
}

struct GoalsView: View {
    @Environment(AppModel.self) private var model
    @State private var segment: GoalSegment = .active
    @State private var categoryFilter: GoalCategory?
    @State private var searchText = ""
    @State private var showNewGoal = false
    @State private var showIdeas = false
    @State private var showCountries = false
    @State private var completingGoal: Goal?
    @State private var showFocusLimit = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("Список", selection: $segment) {
                        Text("Впереди · \(model.data.activeGoals.count)").tag(GoalSegment.active)
                        Text("Сделано · \(model.data.completedGoals.count)").tag(GoalSegment.done)
                    }
                    .pickerStyle(.segmented)
                    .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                    .listRowBackground(Color.clear)

                    CategoryChips(selection: $categoryFilter, counts: categoryCounts)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }
                .listRowSeparator(.hidden)

                switch segment {
                case .active:
                    activeRows
                case .done:
                    doneSections
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("Цели")
            .searchable(text: $searchText, prompt: "Найти цель")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    addMenu
                }
            }
            .navigationDestination(for: UUID.self) { id in
                GoalDetailView(goalID: id)
            }
            .sheet(isPresented: $showNewGoal) {
                GoalEditorView(goal: nil, presetCategory: categoryFilter)
            }
            .sheet(isPresented: $showIdeas) {
                IdeasView()
            }
            .sheet(isPresented: $showCountries) {
                CountriesView()
            }
            .sheet(item: $completingGoal) { goal in
                CompleteGoalSheet(goal: goal)
            }
            .alert("В фокусе уже три цели", isPresented: $showFocusLimit) {
                Button("Понятно", role: .cancel) {}
            } message: {
                Text("Сначала убери звёздочку с одной из них: фокус работает, только когда целей мало.")
            }
            .overlay {
                if model.data.goals.isEmpty {
                    emptyState
                }
            }
        }
    }

    // MARK: Списки

    @ViewBuilder
    private var activeRows: some View {
        let goals = filtered(model.activeGoals)
        if goals.isEmpty && !model.data.goals.isEmpty {
            filterEmptyRow(model.data.activeGoals.isEmpty
                           ? "Всё выполнено. Самое время мечтать дальше."
                           : "Ничего не найдено.")
        }
        ForEach(goals) { goal in
            NavigationLink(value: goal.id) {
                GoalRow(goal: goal, birthDate: model.profile?.birthDate)
            }
            .listRowBackground(Color.clear)
            .swipeActions(edge: .leading, allowsFullSwipe: true) {
                Button {
                    completingGoal = goal
                } label: {
                    Label("Готово", systemImage: "checkmark")
                }
                .tint(Theme.accent)
            }
            .swipeActions(edge: .trailing) {
                Button(role: .destructive) {
                    model.deleteGoal(id: goal.id)
                } label: {
                    Label("Удалить", systemImage: "trash")
                }
                Button {
                    toggleFocus(goal)
                } label: {
                    Label(goal.isFocused ? "Из фокуса" : "В фокус", systemImage: goal.isFocused ? "star.slash" : "star")
                }
                .tint(.gray)
            }
            .contextMenu {
                Button {
                    completingGoal = goal
                } label: {
                    Label("Выполнено", systemImage: "checkmark")
                }
                Button {
                    toggleFocus(goal)
                } label: {
                    Label(goal.isFocused ? "Убрать из фокуса" : "В фокус", systemImage: goal.isFocused ? "star.slash" : "star")
                }
                Button(role: .destructive) {
                    model.deleteGoal(id: goal.id)
                } label: {
                    Label("Удалить", systemImage: "trash")
                }
            }
        }
    }

    @ViewBuilder
    private var doneSections: some View {
        let goals = filtered(model.completedGoals)
        let groups = Dictionary(grouping: goals) { goal in
            Calendar.memento.component(.year, from: goal.completedAt ?? Date())
        }
        let years = groups.keys.sorted(by: >)
        if goals.isEmpty && !model.data.goals.isEmpty {
            filterEmptyRow(model.data.completedGoals.isEmpty
                           ? "Здесь появятся выполненные цели — с датой, заметкой и фото."
                           : "Ничего не найдено.")
        }
        ForEach(years, id: \.self) { year in
            Section {
                ForEach(groups[year] ?? []) { goal in
                    NavigationLink(value: goal.id) {
                        DoneGoalRow(goal: goal)
                    }
                    .listRowBackground(Color.clear)
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            model.deleteGoal(id: goal.id)
                        } label: {
                            Label("Удалить", systemImage: "trash")
                        }
                    }
                }
            } header: {
                HStack {
                    Text(String(year))
                        .font(.headline)
                        .foregroundStyle(Color.white)
                    Spacer()
                    Text(Format.goals(groups[year]?.count ?? 0))
                        .font(.caption)
                        .foregroundStyle(Theme.tertiaryText)
                }
            }
        }
    }

    private func filterEmptyRow(_ text: String) -> some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(Theme.secondaryText)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.vertical, 32)
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
    }

    private var emptyState: some View {
        ContentUnavailableView {
            Label("Список пуст", systemImage: "list.bullet")
        } description: {
            Text("Что ты хочешь успеть? Начни с одной цели или загляни в библиотеку идей.")
        } actions: {
            Button("Новая цель") { showNewGoal = true }
                .buttonStyle(.borderedProminent)
                .foregroundStyle(Color.black)
            Button("Библиотека идей") { showIdeas = true }
        }
    }

    private var addMenu: some View {
        Menu {
            Button {
                showNewGoal = true
            } label: {
                Label("Новая цель", systemImage: "square.and.pencil")
            }
            Button {
                showIdeas = true
            } label: {
                Label("Библиотека идей", systemImage: "lightbulb")
            }
            Button {
                showCountries = true
            } label: {
                Label("Страны мира", systemImage: "globe.europe.africa")
            }
        } label: {
            Image(systemName: "plus")
        }
        .accessibilityLabel("Добавить")
    }

    // MARK: Фильтры

    private var categoryCounts: [GoalCategory: Int] {
        let goals = segment == .active ? model.data.activeGoals : model.data.completedGoals
        return Dictionary(grouping: goals, by: \.category).mapValues(\.count)
    }

    private func filtered(_ goals: [Goal]) -> [Goal] {
        let query = searchText.trimmingCharacters(in: .whitespaces)
        return goals.filter { goal in
            let matchesCategory = categoryFilter == nil || goal.category == categoryFilter
            let matchesQuery = query.isEmpty
                || goal.title.localizedCaseInsensitiveContains(query)
                || goal.note.localizedCaseInsensitiveContains(query)
            return matchesCategory && matchesQuery
        }
    }

    private func toggleFocus(_ goal: Goal) {
        if !model.toggleFocus(id: goal.id) {
            showFocusLimit = true
        }
    }
}

// MARK: - Строки

struct GoalRow: View {
    let goal: Goal
    let birthDate: Date?

    var body: some View {
        HStack(spacing: 12) {
            GoalIcon(goal: goal)
            VStack(alignment: .leading, spacing: 3) {
                Text(goal.title)
                    .foregroundStyle(Color.white)
                    .lineLimit(3)
                if let summary = DeadlineText.summary(for: goal, birthDate: birthDate) {
                    Text(summary)
                        .font(.caption)
                        .foregroundStyle(DeadlineText.isOverdue(goal, birthDate: birthDate) ? Theme.accent : Theme.secondaryText)
                }
            }
            Spacer(minLength: 8)
            if goal.isFocused {
                Image(systemName: "star.fill")
                    .font(.caption)
                    .foregroundStyle(Theme.accent)
            }
        }
        .padding(.vertical, 4)
    }
}

struct DoneGoalRow: View {
    @Environment(AppModel.self) private var model
    let goal: Goal

    var body: some View {
        HStack(spacing: 12) {
            thumbnail
            VStack(alignment: .leading, spacing: 3) {
                Text(goal.title)
                    .foregroundStyle(Color.white)
                    .lineLimit(2)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(Theme.secondaryText)
                if !goal.reflection.isEmpty {
                    Text(goal.reflection)
                        .font(.caption)
                        .foregroundStyle(Theme.tertiaryText)
                        .lineLimit(1)
                }
            }
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private var thumbnail: some View {
        if let image = model.thumbnail(for: goal) {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(width: 44, height: 44)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        } else {
            GoalIcon(goal: goal)
                .frame(width: 44, height: 44)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
    }

    private var subtitle: String {
        guard let date = goal.completedAt else { return "" }
        var text = Format.date(date)
        if let stats = model.stats(at: date) {
            text += " · " + Format.atAge(stats.age(at: date))
        }
        return text
    }
}

struct CategoryChips: View {
    @Binding var selection: GoalCategory?
    let counts: [GoalCategory: Int]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip(title: "Все", symbol: nil, isSelected: selection == nil) {
                    selection = nil
                }
                ForEach(visibleCategories) { category in
                    chip(title: category.title, symbol: category.symbol, isSelected: selection == category) {
                        selection = selection == category ? nil : category
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
        }
    }

    private var visibleCategories: [GoalCategory] {
        GoalCategory.allCases.filter { (counts[$0] ?? 0) > 0 || selection == $0 }
    }

    private func chip(title: String, symbol: String?, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let symbol {
                    Image(systemName: symbol)
                        .font(.caption)
                }
                Text(title)
                    .font(.subheadline)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(isSelected ? Theme.accent : Theme.card, in: Capsule())
            .foregroundStyle(isSelected ? Color.black : Color.white)
        }
        .buttonStyle(.plain)
    }
}

import SwiftUI

/// Библиотека идей: добавление в один тап.
struct IdeasView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""

    var body: some View {
        NavigationStack {
            List {
                ForEach(GoalCategory.allCases) { category in
                    let categoryIdeas = filteredIdeas(in: category)
                    if !categoryIdeas.isEmpty {
                        Section {
                            ForEach(categoryIdeas) { idea in
                                ideaRow(idea)
                            }
                        } header: {
                            Label(category.title, systemImage: category.symbol)
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .searchable(text: $searchText, prompt: "Поиск идей")
            .navigationTitle("Идеи")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
            .sensoryFeedback(.impact(weight: .light), trigger: model.data.goals.count)
        }
    }

    private func ideaRow(_ idea: Idea) -> some View {
        let isAdded = model.hasGoal(titled: idea.title)
        return Button {
            model.addIdea(idea)
        } label: {
            HStack(spacing: 12) {
                Text(idea.title)
                    .foregroundStyle(isAdded ? Theme.secondaryText : Color.white)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: isAdded ? "checkmark.circle.fill" : "plus.circle")
                    .font(.title3)
                    .foregroundStyle(isAdded ? Theme.accent : Theme.secondaryText)
            }
        }
        .disabled(isAdded)
        .accessibilityLabel(isAdded ? "\(idea.title), уже в списке" : "Добавить: \(idea.title)")
    }

    private func filteredIdeas(in category: GoalCategory) -> [Idea] {
        let all = IdeaLibrary.ideas(in: category)
        let query = searchText.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return all }
        return all.filter { $0.title.localizedCaseInsensitiveContains(query) }
    }
}

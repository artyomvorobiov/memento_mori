import SwiftUI

/// Страны мира: отмечай, где уже был и куда хочешь. «Хочу» создаёт цель-путешествие.
struct CountriesView: View {
    enum Filter: String, CaseIterable, Identifiable {
        case all
        case wish
        case visited

        var id: String { rawValue }

        var title: String {
            switch self {
            case .all: "Все"
            case .wish: "Хочу"
            case .visited: "Посещённые"
            }
        }
    }

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""
    @State private var filter: Filter = .all

    var body: some View {
        NavigationStack {
            List {
                Section {
                    summary
                        .listRowBackground(Color.clear)
                    Picker("Фильтр", selection: $filter) {
                        ForEach(Filter.allCases) { item in
                            Text(item.title).tag(item)
                        }
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)
                }
                .listRowSeparator(.hidden)

                Section {
                    if countries.isEmpty {
                        Text(filter == .all ? "Ничего не найдено." : "Пока пусто.")
                            .foregroundStyle(Theme.secondaryText)
                            .listRowBackground(Color.clear)
                    }
                    ForEach(countries) { country in
                        CountryRow(
                            country: country,
                            isWished: model.wishGoal(forCountry: country.code) != nil,
                            isVisited: model.isVisited(country.code),
                            onToggleWish: { model.toggleWish(country) },
                            onToggleVisited: { model.toggleVisited(country) }
                        )
                        .listRowBackground(Color.clear)
                    }
                } footer: {
                    Text("☆ — добавить страну в цели, ◯ — отметить, что уже был(а). Если страна была в целях, цель закроется.")
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .searchable(text: $searchText, prompt: "Найти страну")
            .navigationTitle("Страны")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
            .sensoryFeedback(.selection, trigger: model.data.visitedCountries.count)
        }
    }

    private var summary: some View {
        let sovereign = Set(CountryCatalog.sovereignCodes)
        let visited = model.data.visitedCountries
        let visitedCountries = visited.filter { sovereign.contains($0) }.count
        let visitedTerritories = visited.count - visitedCountries
        let total = CountryCatalog.sovereignCodes.count
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("\(visitedCountries)")
                    .font(.system(size: 44, weight: .thin))
                    .monospacedDigit()
                Text("из \(total) \(RussianPlural.form(total, "страны", "стран", "стран"))")
                    .foregroundStyle(Theme.secondaryText)
                Spacer()
                Text(Format.percent(Double(visitedCountries) / Double(total), digits: 0))
                    .font(.title3.weight(.light))
                    .foregroundStyle(Theme.accent)
            }
            ThinProgressBar(progress: Double(visitedCountries) / Double(total), fill: Theme.accent)
            if visitedTerritories > 0 {
                Text("и ещё \(visitedTerritories) \(RussianPlural.form(visitedTerritories, "территория", "территории", "территорий"))")
                    .font(.caption)
                    .foregroundStyle(Theme.secondaryText)
            }
        }
        .padding(.vertical, 8)
    }

    private var countries: [Country] {
        let query = searchText.trimmingCharacters(in: .whitespaces)
        return CountryCatalog.all.filter { country in
            if !query.isEmpty && !country.name.localizedCaseInsensitiveContains(query) {
                return false
            }
            switch filter {
            case .all:
                return true
            case .wish:
                return model.wishGoal(forCountry: country.code) != nil
            case .visited:
                return model.isVisited(country.code)
            }
        }
    }
}

struct CountryRow: View {
    let country: Country
    let isWished: Bool
    let isVisited: Bool
    let onToggleWish: () -> Void
    let onToggleVisited: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Text(country.flag)
                .font(.title2)
            VStack(alignment: .leading, spacing: 2) {
                Text(country.name)
                    .foregroundStyle(Color.white)
                if isWished {
                    Text("в списке целей")
                        .font(.caption)
                        .foregroundStyle(Theme.accent)
                }
            }
            Spacer()
            Button(action: onToggleWish) {
                Image(systemName: isWished ? "star.fill" : "star")
                    .font(.title3)
                    .foregroundStyle(isWished ? Theme.accent : Theme.tertiaryText)
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(isWished ? "Убрать из целей" : "Хочу побывать")
            Button(action: onToggleVisited) {
                Image(systemName: isVisited ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isVisited ? Theme.accent : Theme.tertiaryText)
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(isVisited ? "Снять отметку о посещении" : "Уже был(а)")
        }
        .padding(.vertical, 2)
    }
}

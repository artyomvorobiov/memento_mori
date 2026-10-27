import SwiftUI

/// Выбор страны для режима «средняя по стране»: у каждой страны видна её средняя продолжительность жизни.
struct ExpectancyCountryList: View {
    @Binding var selection: String
    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""

    var body: some View {
        List {
            Section {
                ForEach(filteredItems) { item in
                    Button {
                        selection = item.code
                        dismiss()
                    } label: {
                        HStack(spacing: 12) {
                            Text(item.flag)
                                .font(.title3)
                            Text(item.name)
                                .foregroundStyle(Color.white)
                            Spacer()
                            Text(Format.decimal(item.years))
                                .monospacedDigit()
                                .foregroundStyle(Theme.secondaryText)
                            if selection == item.code {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(Theme.accent)
                            }
                        }
                    }
                }
            } footer: {
                Text("Средняя продолжительность жизни при рождении, лет. \(LifeExpectancyData.attribution)")
            }
        }
        .searchable(text: $searchText, prompt: "Найти страну")
        .navigationTitle("Страна")
        .navigationBarTitleDisplayMode(.inline)
    }

    /// «🇷🇺 Россия» или «🌍 Весь мир».
    static func label(for code: String) -> String {
        if code == LifeExpectancyData.worldCode {
            return "🌍 Весь мир"
        }
        return "\(flagEmoji(for: code)) \(Country.name(for: code))"
    }

    private struct Item: Identifiable {
        let code: String
        let name: String
        let flag: String
        let years: Double

        var id: String { code }
    }

    private static let items: [Item] = {
        let world = Item(code: LifeExpectancyData.worldCode, name: "Весь мир", flag: "🌍", years: LifeExpectancyData.world)
        let countries = CountryCatalog.all.compactMap { country -> Item? in
            guard let years = LifeExpectancyData.value(for: country.code) else { return nil }
            return Item(code: country.code, name: country.name, flag: country.flag, years: years)
        }
        return [world] + countries
    }()

    private var filteredItems: [Item] {
        let query = searchText.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return Self.items }
        return Self.items.filter { $0.name.localizedCaseInsensitiveContains(query) }
    }
}

import Foundation

/// Статистика путешествий: страны, части света, ближайшая «круглая» отметка.
struct TravelStats {
    static let milestones = [1, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 125, 150, 175, 195]

    let visited: Set<String>
    let wished: Set<String>

    init(visited: some Sequence<String>, wished: some Sequence<String>) {
        self.visited = Set(visited)
        self.wished = Set(wished)
    }

    init(data: AppData) {
        self.init(
            visited: data.visitedCountries,
            wished: data.activeGoals.compactMap(\.countryCode)
        )
    }

    private static let sovereign = Set(CountryCatalog.sovereignCodes)

    /// Посещённые страны — члены ООН и наблюдатели.
    var visitedCountries: Int { visited.filter { Self.sovereign.contains($0) }.count }
    /// Посещённые территории (Гонконг, Пуэрто-Рико…).
    var visitedTerritories: Int { visited.count - visitedCountries }
    var totalCountries: Int { CountryCatalog.sovereignCodes.count }
    var worldShare: Double { Double(visitedCountries) / Double(totalCountries) }
    var wishedCount: Int { wished.subtracting(visited).count }

    var continentsVisited: Int {
        Set(visited.compactMap { Continent.of($0) }).count
    }

    /// Сколько стран части света посещено и сколько их всего (без территорий).
    func progress(for continent: Continent) -> (visited: Int, total: Int) {
        let codes = CountryCatalog.sovereignCodes.filter { Continent.of($0) == continent }
        return (codes.filter { visited.contains($0) }.count, codes.count)
    }

    /// Ближайшая отметка впереди: «до 15 стран — ещё 3».
    var nextMilestone: (target: Int, left: Int)? {
        guard let target = Self.milestones.first(where: { $0 > visitedCountries }) else { return nil }
        return (target, target - visitedCountries)
    }

    func status(of code: String) -> CountryStatus {
        if visited.contains(code) { return .visited }
        if wished.contains(code) { return .wished }
        return .none
    }
}

enum CountryStatus {
    case none
    case wished
    case visited
}

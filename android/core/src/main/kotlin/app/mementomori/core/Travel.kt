package app.mementomori.core

enum class CountryStatus { NONE, WISHED, VISITED }

/** Статистика путешествий: страны, части света, ближайшая «круглая» отметка. */
class TravelStats(visited: Collection<String>, wished: Collection<String>) {
    val visited: Set<String> = visited.toSet()
    val wished: Set<String> = wished.toSet()

    constructor(data: AppData) : this(data.visitedCountries, data.activeGoals.mapNotNull { it.countryCode })

    /** Посещённые страны — члены ООН и наблюдатели. */
    val visitedCountries: Int get() = visited.count { it in sovereign }

    /** Посещённые территории (Гонконг, Пуэрто-Рико…). */
    val visitedTerritories: Int get() = visited.size - visitedCountries
    val totalCountries: Int get() = CountryCatalog.sovereignCodes.size
    val worldShare: Double get() = visitedCountries.toDouble() / totalCountries
    val wishedCount: Int get() = (wished - visited).size
    val continentsVisited: Int get() = visited.mapNotNull { Continent.of(it) }.toSet().size

    /** Сколько стран части света посещено и сколько их всего (без территорий). */
    fun progress(continent: Continent): Pair<Int, Int> {
        val codes = CountryCatalog.sovereignCodes.filter { Continent.of(it) == continent }
        return codes.count { it in visited } to codes.size
    }

    /** Ближайшая отметка впереди: «до 15 стран — ещё 3». */
    val nextMilestone: Pair<Int, Int>?
        get() = MILESTONES.firstOrNull { it > visitedCountries }?.let { it to it - visitedCountries }

    fun status(code: String): CountryStatus = when (code) {
        in visited -> CountryStatus.VISITED
        in wished -> CountryStatus.WISHED
        else -> CountryStatus.NONE
    }

    companion object {
        val MILESTONES = listOf(1, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 125, 150, 175, 195)
        private val sovereign = CountryCatalog.sovereignCodes.toSet()
    }
}

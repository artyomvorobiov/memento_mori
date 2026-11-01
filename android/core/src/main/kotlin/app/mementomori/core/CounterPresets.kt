package app.mementomori.core

data class CounterPreset(val title: String, val emoji: String, val perYear: Double, val years: Int?) {
    val hint: String
        get() {
            val perYearText = if (perYear == Math.rint(perYear)) perYear.toInt().toString() else Format.decimal(perYear)
            return "$perYearText в год" + (years?.let { ", " + Format.years(it) } ?: "")
        }

    fun makeCounter() = LifeCounter(title = title, emoji = emoji, perYear = perYear, years = years)
}

/** Готовые счётчики «Осталось» — в духе эссе Тима Урбана «The Tail End». */
object CounterPresets {
    val all = listOf(
        CounterPreset("встреч с родителями", "❤️", 5.0, 20),
        CounterPreset("прочитанных книг", "📚", 12.0, null),
        CounterPreset("отпусков", "🏖️", 2.0, null),
        CounterPreset("встреч с друзьями детства", "🫂", 3.0, null),
        CounterPreset("походов в театр", "🎭", 4.0, null),
        CounterPreset("горнолыжных сезонов", "⛷️", 1.0, 30),
        CounterPreset("летних отпусков с детьми", "🧒", 1.0, 15),
    )

    /** Счётчики, которые появляются после знакомства. */
    val defaults: List<LifeCounter>
        get() = all.filter { it.title == "прочитанных книг" || it.title == "отпусков" }.map { it.makeCounter() }
}

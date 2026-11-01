@file:UseSerializers(InstantSerializer::class)

package app.mementomori.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.time.ZoneId

/** Письмо себе в будущее. Запечатано до дня открытия: к возрасту («к 40 годам») или к дате. */
@Serializable
data class FutureLetter(
    val id: String = newId(),
    val text: String = "",
    val createdAt: Instant = Instant.now(),
    /** Открыть к возрасту. Взаимоисключающе с openDate. */
    val openAge: Int? = null,
    /** Открыть в конкретный день. */
    val openDate: Instant? = null,
    /** Когда письмо прочитали. null — ещё запечатано или ждёт. */
    val openedAt: Instant? = null,
) {
    val isOpened: Boolean get() = openedAt != null

    /** С какого момента письмо можно открыть — с начала нужного дня. */
    fun unlockDate(birthDate: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant {
        openDate?.let { return startOfDay(it, zone) }
        openAge?.let { return startOfDay(birthDate.atZone(zone).plusYears(it.toLong()).toInstant(), zone) }
        return startOfDay(createdAt.atZone(zone).plusYears(1).toInstant(), zone)
    }

    /** Срок настал (неважно, открыто ли уже). */
    fun isUnlocked(now: Instant, birthDate: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        !now.isBefore(unlockDate(birthDate, zone))

    /** Срок настал, а письмо ещё не прочитано. */
    fun isWaiting(now: Instant, birthDate: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        !isOpened && isUnlocked(now, birthDate, zone)

    /** «к 40 годам» или «14 марта 2027 г.». */
    fun openLabel(birthDate: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val age = openAge
        if (openDate == null && age != null) return Format.byAge(age)
        return Format.date(unlockDate(birthDate, zone), zone)
    }

    /** Сколько лет было, когда писали письмо. */
    fun ageWhenWritten(birthDate: Instant, zone: ZoneId = ZoneId.systemDefault()): Int =
        LifeStats(birthDate, 120, createdAt, zone).ageYears

    /** Первая строка — чтобы узнать письмо в списке после того, как его прочитали. */
    val preview: String
        get() {
            val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
            return if (line.length > 80) line.take(80) + "…" else line
        }
}

/** Письма, которые уже можно открыть, — старые первыми. */
fun AppData.waitingLetters(now: Instant, zone: ZoneId = ZoneId.systemDefault()): List<FutureLetter> {
    val birthDate = profile?.birthDate ?: return emptyList()
    return letters.filter { it.isWaiting(now, birthDate, zone) }.sortedBy { it.unlockDate(birthDate, zone) }
}

/** Запечатанные письма — ближайшие первыми. */
fun AppData.sealedLetters(now: Instant, zone: ZoneId = ZoneId.systemDefault()): List<FutureLetter> {
    val birthDate = profile?.birthDate ?: return emptyList()
    return letters.filter { !it.isOpened && !it.isUnlocked(now, birthDate, zone) }.sortedBy { it.unlockDate(birthDate, zone) }
}

/** Прочитанные письма — последние прочитанные первыми. */
val AppData.openedLetters: List<FutureLetter>
    get() = letters.filter { it.isOpened }.sortedByDescending { it.openedAt }

/** Дней до дня, когда письмо можно будет открыть. */
fun daysUntilLetter(letter: FutureLetter, birthDate: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Int =
    maxOf(0L, daysBetween(now, letter.unlockDate(birthDate, zone), zone)).toInt()

/** Вопросы, с которых легко начать письмо. */
object LetterPrompts {
    val all = listOf(
        "Что тебя сейчас волнует больше всего?",
        "Чем ты гордишься в этом году?",
        "Чего ты боишься — и что будешь делать, если это случится?",
        "Какие цели из списка ты надеешься закрыть к этому дню?",
        "Кого ты любишь и что хочешь им сказать?",
        "Какой совет ты дашь себе будущему?",
        "Что ты делаешь каждый день и хочешь, чтобы это осталось?",
        "Что ты надеешься изменить к моменту, когда это прочитаешь?",
    )
}

package app.mementomori.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

// Формат данных совпадает с версией для iPhone: резервную копию оттуда можно открыть здесь и наоборот.

/** JSON без лишних требований: неизвестные поля пропускаем, пустые — заполняем значениями по умолчанию. */
val MementoJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
    prettyPrint = true
}

/** Идентификаторы — UUID заглавными буквами, как на iPhone. */
fun newId(): String = UUID.randomUUID().toString().uppercase()

/** Даты — ISO 8601 с точностью до секунды: «2026-10-28T09:41:00Z». */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.truncatedTo(ChronoUnit.SECONDS).toString())
    }

    override fun deserialize(decoder: Decoder): Instant = parseInstant(decoder.decodeString())
}

fun parseInstant(text: String): Instant =
    runCatching { Instant.parse(text) }.getOrElse { OffsetDateTime.parse(text).toInstant() }

/** Перечисления читаются терпимо: незнакомое значение (из будущей версии) — значение по умолчанию. */
abstract class LenientEnumSerializer<T>(
    name: String,
    private val values: List<T>,
    private val raw: (T) -> String,
    private val fallback: T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(raw(value))

    override fun deserialize(decoder: Decoder): T {
        val text = decoder.decodeString()
        return values.firstOrNull { raw(it) == text } ?: fallback
    }
}

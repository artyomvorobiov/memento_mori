package app.mementomori.core

import java.text.Collator
import java.util.Locale

data class Country(val code: String, val name: String) {
    val flag: String get() = flagEmoji(code)

    companion object {
        fun name(code: String): String =
            Locale("", code).getDisplayCountry(Format.locale).takeIf { it.isNotBlank() && it != code } ?: code
    }
}

/** Эмодзи-флаг из двухбуквенного ISO-кода: «JP» → 🇯🇵. */
fun flagEmoji(countryCode: String): String {
    val builder = StringBuilder()
    for (char in countryCode.uppercase()) {
        if (char in 'A'..'Z') builder.appendCodePoint(0x1F1E6 + (char - 'A'))
    }
    return builder.toString()
}

object CountryCatalog {
    /** 193 страны — члены ООН и 2 государства-наблюдателя. */
    val sovereignCodes: List<String> = listOf(
        "AF", "AL", "DZ", "AD", "AO", "AG", "AR", "AM", "AU", "AT", "AZ", "BS", "BH", "BD", "BB", "BY",
        "BE", "BZ", "BJ", "BT", "BO", "BA", "BW", "BR", "BN", "BG", "BF", "BI", "CV", "KH", "CM", "CA",
        "CF", "TD", "CL", "CN", "CO", "KM", "CG", "CD", "CR", "CI", "HR", "CU", "CY", "CZ", "DK", "DJ",
        "DM", "DO", "EC", "EG", "SV", "GQ", "ER", "EE", "SZ", "ET", "FJ", "FI", "FR", "GA", "GM", "GE",
        "DE", "GH", "GR", "GD", "GT", "GN", "GW", "GY", "HT", "HN", "HU", "IS", "IN", "ID", "IR", "IQ",
        "IE", "IL", "IT", "JM", "JP", "JO", "KZ", "KE", "KI", "KP", "KR", "KW", "KG", "LA", "LV", "LB",
        "LS", "LR", "LY", "LI", "LT", "LU", "MG", "MW", "MY", "MV", "ML", "MT", "MH", "MR", "MU", "MX",
        "FM", "MD", "MC", "MN", "ME", "MA", "MZ", "MM", "NA", "NR", "NP", "NL", "NZ", "NI", "NE", "NG",
        "MK", "NO", "OM", "PK", "PW", "PA", "PG", "PY", "PE", "PH", "PL", "PT", "QA", "RO", "RU", "RW",
        "KN", "LC", "VC", "WS", "SM", "ST", "SA", "SN", "RS", "SC", "SL", "SG", "SK", "SI", "SB", "SO",
        "ZA", "SS", "ES", "LK", "SD", "SR", "SE", "CH", "SY", "TJ", "TZ", "TH", "TL", "TG", "TO", "TT",
        "TN", "TR", "TM", "TV", "UG", "UA", "AE", "GB", "US", "UY", "UZ", "VU", "VE", "VN", "YE", "ZM",
        "ZW", "VA", "PS",
    )

    /** Популярные у путешественников территории. */
    val territoryCodes: List<String> = listOf(
        "HK", "MO", "TW", "PR", "GL", "FO", "GI", "BM", "PF", "NC", "RE", "AW", "CW", "KY", "VG",
    )

    val all: List<Country> by lazy {
        val collator = Collator.getInstance(Format.locale)
        (sovereignCodes + territoryCodes)
            .map { Country(it, Country.name(it)) }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    fun country(code: String): Country? = all.firstOrNull { it.code == code }
}

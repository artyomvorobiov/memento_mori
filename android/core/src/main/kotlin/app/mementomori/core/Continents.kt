package app.mementomori.core

/**
 * Части света для статистики путешествий.
 * Деление — по геосхеме ООН (Турция, Кавказ и Казахстан — Азия, Россия — Европа),
 * Кипр отнесён к Европе.
 */
enum class Continent(val title: String) {
    EUROPE("Европа"),
    ASIA("Азия"),
    AFRICA("Африка"),
    NORTH_AMERICA("Северная Америка"),
    SOUTH_AMERICA("Южная Америка"),
    OCEANIA("Океания");

    companion object {
        fun of(countryCode: String): Continent? = byCountry[countryCode]

        private val byCountry: Map<String, Continent> = mapOf(
        "AD" to EUROPE, "AL" to EUROPE, "AT" to EUROPE, "BA" to EUROPE, "BE" to EUROPE, "BG" to EUROPE,
        "BY" to EUROPE, "CH" to EUROPE, "CY" to EUROPE, "CZ" to EUROPE, "DE" to EUROPE, "DK" to EUROPE,
        "EE" to EUROPE, "ES" to EUROPE, "FI" to EUROPE, "FO" to EUROPE, "FR" to EUROPE, "GB" to EUROPE,
        "GI" to EUROPE, "GR" to EUROPE, "HR" to EUROPE, "HU" to EUROPE, "IE" to EUROPE, "IS" to EUROPE,
        "IT" to EUROPE, "LI" to EUROPE, "LT" to EUROPE, "LU" to EUROPE, "LV" to EUROPE, "MC" to EUROPE,
        "MD" to EUROPE, "ME" to EUROPE, "MK" to EUROPE, "MT" to EUROPE, "NL" to EUROPE, "NO" to EUROPE,
        "PL" to EUROPE, "PT" to EUROPE, "RO" to EUROPE, "RS" to EUROPE, "RU" to EUROPE, "SE" to EUROPE,
        "SI" to EUROPE, "SK" to EUROPE, "SM" to EUROPE, "UA" to EUROPE, "VA" to EUROPE, "AE" to ASIA,
        "AF" to ASIA, "AM" to ASIA, "AZ" to ASIA, "BD" to ASIA, "BH" to ASIA, "BN" to ASIA,
        "BT" to ASIA, "CN" to ASIA, "GE" to ASIA, "HK" to ASIA, "ID" to ASIA, "IL" to ASIA,
        "IN" to ASIA, "IQ" to ASIA, "IR" to ASIA, "JO" to ASIA, "JP" to ASIA, "KG" to ASIA,
        "KH" to ASIA, "KP" to ASIA, "KR" to ASIA, "KW" to ASIA, "KZ" to ASIA, "LA" to ASIA,
        "LB" to ASIA, "LK" to ASIA, "MM" to ASIA, "MN" to ASIA, "MO" to ASIA, "MV" to ASIA,
        "MY" to ASIA, "NP" to ASIA, "OM" to ASIA, "PH" to ASIA, "PK" to ASIA, "PS" to ASIA,
        "QA" to ASIA, "SA" to ASIA, "SG" to ASIA, "SY" to ASIA, "TH" to ASIA, "TJ" to ASIA,
        "TL" to ASIA, "TM" to ASIA, "TR" to ASIA, "TW" to ASIA, "UZ" to ASIA, "VN" to ASIA,
        "YE" to ASIA, "AO" to AFRICA, "BF" to AFRICA, "BI" to AFRICA, "BJ" to AFRICA, "BW" to AFRICA,
        "CD" to AFRICA, "CF" to AFRICA, "CG" to AFRICA, "CI" to AFRICA, "CM" to AFRICA, "CV" to AFRICA,
        "DJ" to AFRICA, "DZ" to AFRICA, "EG" to AFRICA, "ER" to AFRICA, "ET" to AFRICA, "GA" to AFRICA,
        "GH" to AFRICA, "GM" to AFRICA, "GN" to AFRICA, "GQ" to AFRICA, "GW" to AFRICA, "KE" to AFRICA,
        "KM" to AFRICA, "LR" to AFRICA, "LS" to AFRICA, "LY" to AFRICA, "MA" to AFRICA, "MG" to AFRICA,
        "ML" to AFRICA, "MR" to AFRICA, "MU" to AFRICA, "MW" to AFRICA, "MZ" to AFRICA, "NA" to AFRICA,
        "NE" to AFRICA, "NG" to AFRICA, "RE" to AFRICA, "RW" to AFRICA, "SC" to AFRICA, "SD" to AFRICA,
        "SL" to AFRICA, "SN" to AFRICA, "SO" to AFRICA, "SS" to AFRICA, "ST" to AFRICA, "SZ" to AFRICA,
        "TD" to AFRICA, "TG" to AFRICA, "TN" to AFRICA, "TZ" to AFRICA, "UG" to AFRICA, "ZA" to AFRICA,
        "ZM" to AFRICA, "ZW" to AFRICA, "AG" to NORTH_AMERICA, "AW" to NORTH_AMERICA, "BB" to NORTH_AMERICA, "BM" to NORTH_AMERICA,
        "BS" to NORTH_AMERICA, "BZ" to NORTH_AMERICA, "CA" to NORTH_AMERICA, "CR" to NORTH_AMERICA, "CU" to NORTH_AMERICA, "CW" to NORTH_AMERICA,
        "DM" to NORTH_AMERICA, "DO" to NORTH_AMERICA, "GD" to NORTH_AMERICA, "GL" to NORTH_AMERICA, "GT" to NORTH_AMERICA, "HN" to NORTH_AMERICA,
        "HT" to NORTH_AMERICA, "JM" to NORTH_AMERICA, "KN" to NORTH_AMERICA, "KY" to NORTH_AMERICA, "LC" to NORTH_AMERICA, "MX" to NORTH_AMERICA,
        "NI" to NORTH_AMERICA, "PA" to NORTH_AMERICA, "PR" to NORTH_AMERICA, "SV" to NORTH_AMERICA, "TT" to NORTH_AMERICA, "US" to NORTH_AMERICA,
        "VC" to NORTH_AMERICA, "VG" to NORTH_AMERICA, "AR" to SOUTH_AMERICA, "BO" to SOUTH_AMERICA, "BR" to SOUTH_AMERICA, "CL" to SOUTH_AMERICA,
        "CO" to SOUTH_AMERICA, "EC" to SOUTH_AMERICA, "GY" to SOUTH_AMERICA, "PE" to SOUTH_AMERICA, "PY" to SOUTH_AMERICA, "SR" to SOUTH_AMERICA,
        "UY" to SOUTH_AMERICA, "VE" to SOUTH_AMERICA, "AU" to OCEANIA, "FJ" to OCEANIA, "FM" to OCEANIA, "KI" to OCEANIA,
        "MH" to OCEANIA, "NC" to OCEANIA, "NR" to OCEANIA, "NZ" to OCEANIA, "PF" to OCEANIA, "PG" to OCEANIA,
        "PW" to OCEANIA, "SB" to OCEANIA, "TO" to OCEANIA, "TV" to OCEANIA, "VU" to OCEANIA, "WS" to OCEANIA,
        )
    }
}

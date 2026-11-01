package app.mementomori.core

/**
 * Ожидаемая продолжительность жизни при рождении (оба пола), лет.
 *
 * Источник: Всемирный банк, World Development Indicators, показатель SP.DYN.LE00.IN,
 * данные за 2023 год. Лицензия CC BY 4.0: https://datacatalog.worldbank.org/public-licenses
 * Файл сгенерирован скриптом scripts/update-life-expectancy.py.
 * Стран из каталога без данных: VA, TW, RE.
 */
object LifeExpectancyData {
    const val YEAR = 2023
    const val ATTRIBUTION = "Данные: Всемирный банк (World Development Indicators), 2023 год, CC BY 4.0."

    /** Код «весь мир» (UN M49) — среднее по планете. */
    const val WORLD_CODE = "001"
    const val WORLD = 73.3

    val byCountry: Map<String, Double> = mapOf(
        "AD" to 84.0, "AE" to 82.9, "AF" to 66.0, "AG" to 77.6, "AL" to 79.6, "AM" to 77.5,
        "AO" to 64.6, "AR" to 77.4, "AT" to 81.5, "AU" to 83.1, "AW" to 76.4, "AZ" to 74.4,
        "BA" to 77.8, "BB" to 76.2, "BD" to 74.7, "BE" to 82.4, "BF" to 61.1, "BG" to 75.7,
        "BH" to 81.3, "BI" to 63.7, "BJ" to 60.8, "BM" to 82.3, "BN" to 75.3, "BO" to 68.6,
        "BR" to 75.8, "BS" to 74.6, "BT" to 73.0, "BW" to 69.2, "BY" to 74.2, "BZ" to 73.6,
        "CA" to 81.6, "CD" to 61.9, "CF" to 57.4, "CG" to 65.8, "CH" to 84.1, "CI" to 61.9,
        "CL" to 81.2, "CM" to 63.7, "CN" to 78.0, "CO" to 77.7, "CR" to 80.8, "CU" to 78.1,
        "CV" to 76.1, "CW" to 76.8, "CY" to 81.6, "CZ" to 79.9, "DE" to 80.5, "DJ" to 66.0,
        "DK" to 81.9, "DM" to 71.1, "DO" to 73.7, "DZ" to 76.3, "EC" to 77.4, "EE" to 78.5,
        "EG" to 71.6, "ER" to 68.6, "ES" to 83.9, "ET" to 67.3, "FI" to 81.7, "FJ" to 67.3,
        "FM" to 67.2, "FO" to 83.1, "FR" to 82.9, "GA" to 68.3, "GB" to 81.2, "GD" to 75.2,
        "GE" to 74.5, "GH" to 65.5, "GI" to 83.6, "GL" to 71.5, "GM" to 65.9, "GN" to 60.7,
        "GQ" to 63.7, "GR" to 81.5, "GT" to 72.6, "GW" to 64.1, "GY" to 70.2, "HK" to 85.2,
        "HN" to 72.9, "HR" to 78.5, "HT" to 64.9, "HU" to 76.8, "ID" to 71.1, "IE" to 82.9,
        "IL" to 83.2, "IN" to 72.0, "IQ" to 72.3, "IR" to 77.7, "IS" to 82.6, "IT" to 83.7,
        "JM" to 71.5, "JO" to 77.8, "JP" to 84.0, "KE" to 63.6, "KG" to 72.2, "KH" to 70.7,
        "KI" to 66.5, "KM" to 66.8, "KN" to 72.1, "KP" to 73.6, "KR" to 83.4, "KW" to 83.2,
        "KY" to 80.4, "KZ" to 74.4, "LA" to 69.0, "LB" to 77.8, "LC" to 72.7, "LI" to 84.8,
        "LK" to 77.5, "LR" to 62.2, "LS" to 57.4, "LT" to 77.0, "LU" to 83.4, "LV" to 75.7,
        "LY" to 69.3, "MA" to 75.3, "MC" to 86.4, "MD" to 71.2, "ME" to 77.6, "MG" to 63.6,
        "MH" to 66.9, "MK" to 75.3, "ML" to 60.4, "MM" to 66.9, "MN" to 72.1, "MO" to 83.2,
        "MR" to 68.5, "MT" to 83.5, "MU" to 73.4, "MV" to 81.0, "MW" to 67.4, "MX" to 75.1,
        "MY" to 76.7, "MZ" to 63.6, "NA" to 67.4, "NC" to 78.8, "NE" to 61.2, "NG" to 54.5,
        "NI" to 74.9, "NL" to 81.9, "NO" to 83.1, "NP" to 70.4, "NR" to 62.1, "NZ" to 83.0,
        "OM" to 80.0, "PA" to 79.6, "PE" to 77.7, "PF" to 84.1, "PG" to 66.1, "PH" to 69.8,
        "PK" to 67.6, "PL" to 78.5, "PR" to 81.7, "PS" to 65.2, "PT" to 82.3, "PW" to 69.3,
        "PY" to 73.8, "QA" to 82.4, "RO" to 76.6, "RS" to 76.2, "RU" to 73.3, "RW" to 67.8,
        "SA" to 78.7, "SB" to 70.5, "SC" to 75.0, "SD" to 66.3, "SE" to 83.3, "SG" to 82.9,
        "SI" to 82.0, "SK" to 78.0, "SL" to 61.8, "SM" to 85.7, "SN" to 68.7, "SO" to 58.8,
        "SR" to 73.6, "SS" to 57.6, "ST" to 69.7, "SV" to 72.1, "SY" to 72.1, "SZ" to 64.1,
        "TD" to 55.1, "TG" to 62.7, "TH" to 76.4, "TJ" to 71.8, "TL" to 67.7, "TM" to 70.1,
        "TN" to 76.5, "TO" to 72.9, "TR" to 77.2, "TT" to 73.5, "TV" to 67.1, "TZ" to 67.0,
        "UA" to 73.4, "UG" to 68.3, "US" to 78.4, "UY" to 78.1, "UZ" to 72.4, "VC" to 71.2,
        "VE" to 72.5, "VG" to 77.3, "VN" to 74.6, "VU" to 71.5, "WS" to 71.7, "YE" to 69.3,
        "ZA" to 66.1, "ZM" to 66.3, "ZW" to 62.8,
    )

    /** Средняя продолжительность жизни для страны (или для всего мира по коду 001). */
    fun value(code: String): Double? = if (code == WORLD_CODE) WORLD else byCountry[code]

    fun hasData(code: String): Boolean = value(code) != null
}

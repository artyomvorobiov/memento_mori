import Foundation

/// Части света для статистики путешествий.
/// Деление — по геосхеме ООН (Турция, Кавказ и Казахстан — Азия, Россия — Европа),
/// Кипр отнесён к Европе.
enum Continent: String, CaseIterable, Identifiable, Codable {
    case europe
    case asia
    case africa
    case northAmerica
    case southAmerica
    case oceania

    var id: String { rawValue }

    var title: String {
        switch self {
        case .europe: "Европа"
        case .asia: "Азия"
        case .africa: "Африка"
        case .northAmerica: "Северная Америка"
        case .southAmerica: "Южная Америка"
        case .oceania: "Океания"
        }
    }

    var emoji: String {
        switch self {
        case .europe, .africa: "🌍"
        case .asia, .oceania: "🌏"
        case .northAmerica, .southAmerica: "🌎"
        }
    }

    static func of(_ countryCode: String) -> Continent? {
        byCountry[countryCode]
    }

    static let byCountry: [String: Continent] = [
        // europe
        "AD": .europe, "AL": .europe, "AT": .europe, "BA": .europe, "BE": .europe, "BG": .europe,
        "BY": .europe, "CH": .europe, "CY": .europe, "CZ": .europe, "DE": .europe, "DK": .europe,
        "EE": .europe, "ES": .europe, "FI": .europe, "FO": .europe, "FR": .europe, "GB": .europe,
        "GI": .europe, "GR": .europe, "HR": .europe, "HU": .europe, "IE": .europe, "IS": .europe,
        "IT": .europe, "LI": .europe, "LT": .europe, "LU": .europe, "LV": .europe, "MC": .europe,
        "MD": .europe, "ME": .europe, "MK": .europe, "MT": .europe, "NL": .europe, "NO": .europe,
        "PL": .europe, "PT": .europe, "RO": .europe, "RS": .europe, "RU": .europe, "SE": .europe,
        "SI": .europe, "SK": .europe, "SM": .europe, "UA": .europe, "VA": .europe,
        // asia
        "AE": .asia, "AF": .asia, "AM": .asia, "AZ": .asia, "BD": .asia, "BH": .asia,
        "BN": .asia, "BT": .asia, "CN": .asia, "GE": .asia, "HK": .asia, "ID": .asia,
        "IL": .asia, "IN": .asia, "IQ": .asia, "IR": .asia, "JO": .asia, "JP": .asia,
        "KG": .asia, "KH": .asia, "KP": .asia, "KR": .asia, "KW": .asia, "KZ": .asia,
        "LA": .asia, "LB": .asia, "LK": .asia, "MM": .asia, "MN": .asia, "MO": .asia,
        "MV": .asia, "MY": .asia, "NP": .asia, "OM": .asia, "PH": .asia, "PK": .asia,
        "PS": .asia, "QA": .asia, "SA": .asia, "SG": .asia, "SY": .asia, "TH": .asia,
        "TJ": .asia, "TL": .asia, "TM": .asia, "TR": .asia, "TW": .asia, "UZ": .asia,
        "VN": .asia, "YE": .asia,
        // africa
        "AO": .africa, "BF": .africa, "BI": .africa, "BJ": .africa, "BW": .africa, "CD": .africa,
        "CF": .africa, "CG": .africa, "CI": .africa, "CM": .africa, "CV": .africa, "DJ": .africa,
        "DZ": .africa, "EG": .africa, "ER": .africa, "ET": .africa, "GA": .africa, "GH": .africa,
        "GM": .africa, "GN": .africa, "GQ": .africa, "GW": .africa, "KE": .africa, "KM": .africa,
        "LR": .africa, "LS": .africa, "LY": .africa, "MA": .africa, "MG": .africa, "ML": .africa,
        "MR": .africa, "MU": .africa, "MW": .africa, "MZ": .africa, "NA": .africa, "NE": .africa,
        "NG": .africa, "RE": .africa, "RW": .africa, "SC": .africa, "SD": .africa, "SL": .africa,
        "SN": .africa, "SO": .africa, "SS": .africa, "ST": .africa, "SZ": .africa, "TD": .africa,
        "TG": .africa, "TN": .africa, "TZ": .africa, "UG": .africa, "ZA": .africa, "ZM": .africa,
        "ZW": .africa,
        // northAmerica
        "AG": .northAmerica, "AW": .northAmerica, "BB": .northAmerica, "BM": .northAmerica, "BS": .northAmerica, "BZ": .northAmerica,
        "CA": .northAmerica, "CR": .northAmerica, "CU": .northAmerica, "CW": .northAmerica, "DM": .northAmerica, "DO": .northAmerica,
        "GD": .northAmerica, "GL": .northAmerica, "GT": .northAmerica, "HN": .northAmerica, "HT": .northAmerica, "JM": .northAmerica,
        "KN": .northAmerica, "KY": .northAmerica, "LC": .northAmerica, "MX": .northAmerica, "NI": .northAmerica, "PA": .northAmerica,
        "PR": .northAmerica, "SV": .northAmerica, "TT": .northAmerica, "US": .northAmerica, "VC": .northAmerica, "VG": .northAmerica,
        // southAmerica
        "AR": .southAmerica, "BO": .southAmerica, "BR": .southAmerica, "CL": .southAmerica, "CO": .southAmerica, "EC": .southAmerica,
        "GY": .southAmerica, "PE": .southAmerica, "PY": .southAmerica, "SR": .southAmerica, "UY": .southAmerica, "VE": .southAmerica,
        // oceania
        "AU": .oceania, "FJ": .oceania, "FM": .oceania, "KI": .oceania, "MH": .oceania, "NC": .oceania,
        "NR": .oceania, "NZ": .oceania, "PF": .oceania, "PG": .oceania, "PW": .oceania, "SB": .oceania,
        "TO": .oceania, "TV": .oceania, "VU": .oceania, "WS": .oceania,
    ]
}

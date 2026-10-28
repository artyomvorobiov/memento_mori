import Foundation

struct Country: Identifiable, Hashable {
    let code: String
    let name: String

    var id: String { code }
    var flag: String { flagEmoji(for: code) }

    static func name(for code: String) -> String {
        Format.locale.localizedString(forRegionCode: code) ?? code
    }
}

enum CountryCatalog {
    /// 193 страны — члены ООН и 2 государства-наблюдателя.
    static let sovereignCodes: [String] = [
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
    ]

    /// Популярные у путешественников территории.
    static let territoryCodes: [String] = ["HK", "MO", "TW", "PR", "GL", "FO", "GI", "BM", "PF", "NC", "RE", "AW", "CW", "KY", "VG"]

    static let all: [Country] = (sovereignCodes + territoryCodes)
        .map { Country(code: $0, name: Country.name(for: $0)) }
        .sorted { $0.name.compare($1.name, locale: Format.locale) == .orderedAscending }

    static func country(code: String) -> Country? {
        all.first { $0.code == code }
    }
}

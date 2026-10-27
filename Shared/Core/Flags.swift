import Foundation

/// Эмодзи-флаг из двухбуквенного ISO-кода: «JP» → 🇯🇵.
func flagEmoji(for countryCode: String) -> String {
    let base: UInt32 = 0x1F1E6 - 0x41
    var result = ""
    for scalar in countryCode.uppercased().unicodeScalars {
        guard (0x41...0x5A).contains(scalar.value),
              let indicator = Unicode.Scalar(base + scalar.value)
        else { continue }
        result.unicodeScalars.append(indicator)
    }
    return result
}

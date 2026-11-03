import Foundation

/// Права (entitlements) из подписи кода исполняемого файла Mach-O.
///
/// Подпись есть в любой сборке на iPhone — из Xcode, TestFlight или App Store, — в отличие
/// от embedded.mobileprovision, которого в сборках из TestFlight и App Store нет. Поэтому
/// группу App Group, на которую реально подписаны приложение и виджет, надёжнее брать отсюда.
enum CodeSignature {
    static let appGroupsKey = "com.apple.security.application-groups"

    /// App Groups из подписи файла. nil — подпись не прочиталась; [] — групп нет.
    static func appGroups(ofExecutableAt url: URL?) -> [String]? {
        guard let url, let entitlements = entitlements(ofExecutableAt: url) else { return nil }
        return entitlements[appGroupsKey] as? [String] ?? []
    }

    static func entitlements(ofExecutableAt url: URL) -> [String: Any]? {
        guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? handle.close() }
        return entitlements { offset, length in
            do {
                try handle.seek(toOffset: offset)
                return try handle.read(upToCount: length)
            } catch {
                return nil
            }
        }
    }

    static func entitlements(in data: Data) -> [String: Any]? {
        entitlements { offset, length in
            guard offset < UInt64(data.count) else { return nil }
            let start = data.startIndex + Int(offset)
            return data.subdata(in: start..<min(data.endIndex, start + length))
        }
    }

    private static let lcCodeSignature: UInt32 = 0x1D
    private static let superBlobMagic: UInt32 = 0xFADE0CC0
    private static let entitlementsMagic: UInt32 = 0xFADE7171
    private static let arm64: UInt32 = 0x0100000C

    /// read(смещение, длина) — кусок файла.
    private static func entitlements(read: (UInt64, Int) -> Data?) -> [String: Any]? {
        guard var header = read(0, 4096), header.count >= 32 else { return nil }
        var sliceOffset: UInt64 = 0

        // Универсальный файл: берём срез arm64 (или первый).
        let fatMagic = header.uint32BE(0)
        if fatMagic == 0xCAFEBABE || fatMagic == 0xCAFEBABF {
            let is64 = fatMagic == 0xCAFEBABF
            let entrySize = is64 ? 32 : 20
            var chosen: UInt64?
            for index in 0..<Int(min(header.uint32BE(4), 16)) {
                let base = 8 + index * entrySize
                guard base + entrySize <= header.count else { break }
                let offset = is64 ? header.uint64BE(base + 8) : UInt64(header.uint32BE(base + 8))
                if chosen == nil || header.uint32BE(base) == arm64 { chosen = offset }
            }
            guard let offset = chosen, let slice = read(offset, 4096), slice.count >= 32 else { return nil }
            sliceOffset = offset
            header = slice
        }

        let magic = header.uint32LE(0)
        guard magic == 0xFEEDFACF || magic == 0xFEEDFACE else { return nil }
        let headerSize = magic == 0xFEEDFACF ? 32 : 28
        let commandCount = Int(header.uint32LE(16))
        let commandsSize = Int(header.uint32LE(20))
        if header.count < headerSize + commandsSize {
            guard let full = read(sliceOffset, headerSize + commandsSize) else { return nil }
            header = full
        }

        var cursor = headerSize
        for _ in 0..<commandCount {
            guard cursor + 8 <= header.count else { return nil }
            let command = header.uint32LE(cursor)
            let size = Int(header.uint32LE(cursor + 4))
            if command == lcCodeSignature, cursor + 16 <= header.count {
                let dataOffset = UInt64(header.uint32LE(cursor + 8))
                let dataSize = Int(header.uint32LE(cursor + 12))
                guard let blob = read(sliceOffset + dataOffset, dataSize) else { return nil }
                return entitlements(superBlob: blob)
            }
            guard size >= 8 else { return nil }
            cursor += size
        }
        return nil
    }

    /// Подпись — «суперблоб» с оглавлением; права лежат в блобе 0xFADE7171 обычным XML-плистом.
    private static func entitlements(superBlob blob: Data) -> [String: Any]? {
        guard blob.count >= 12, blob.uint32BE(0) == superBlobMagic else { return nil }
        let count = Int(blob.uint32BE(8))
        for index in 0..<min(count, 64) {
            let entry = 12 + index * 8
            guard entry + 8 <= blob.count else { break }
            let offset = Int(blob.uint32BE(entry + 4))
            guard offset + 8 <= blob.count, blob.uint32BE(offset) == entitlementsMagic else { continue }
            let length = Int(blob.uint32BE(offset + 4))
            guard length > 8, offset + length <= blob.count else { continue }
            let xml = blob.subdata(in: (blob.startIndex + offset + 8)..<(blob.startIndex + offset + length))
            return (try? PropertyListSerialization.propertyList(from: xml, format: nil)) as? [String: Any]
        }
        return nil
    }
}

private extension Data {
    func byte(_ offset: Int) -> UInt32 { UInt32(self[startIndex + offset]) }

    func uint32BE(_ offset: Int) -> UInt32 {
        byte(offset) << 24 | byte(offset + 1) << 16 | byte(offset + 2) << 8 | byte(offset + 3)
    }

    func uint32LE(_ offset: Int) -> UInt32 {
        byte(offset) | byte(offset + 1) << 8 | byte(offset + 2) << 16 | byte(offset + 3) << 24
    }

    func uint64BE(_ offset: Int) -> UInt64 {
        UInt64(uint32BE(offset)) << 32 | UInt64(uint32BE(offset + 4))
    }
}

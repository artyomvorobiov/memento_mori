import Foundation

enum DataStoreError: Error {
    /// Файл есть, но прочитать его сейчас нельзя (например, устройство ещё не разблокировали).
    case unreadable(Error)
    /// Файл прочитан, но содержимое повреждено.
    case corrupted(Error)
}

/// Хранилище данных: один JSON-файл и папка с фото.
/// Приложение и виджет работают с одним и тем же каталогом в App Group,
/// поэтому запись атомарная — читатель всегда видит целый файл.
final class DataStore {
    static let fileName = "memento-data.json"

    let directory: URL

    init(directory: URL) {
        self.directory = directory
    }

    var fileURL: URL { directory.appendingPathComponent(Self.fileName) }
    var photosDirectory: URL { directory.appendingPathComponent("Photos", isDirectory: true) }

    var fileExists: Bool { FileManager.default.fileExists(atPath: fileURL.path) }

    /// Читает данные. Возвращает nil, если файла ещё нет.
    /// Бросает ошибку, если файл есть, но прочитать его не удалось, —
    /// в этом случае ни в коем случае нельзя перезаписывать файл пустыми данными.
    func load() throws -> AppData? {
        guard fileExists else { return nil }
        let raw: Data
        do {
            raw = try Data(contentsOf: fileURL)
        } catch {
            throw DataStoreError.unreadable(error)
        }
        do {
            return try Self.decode(raw)
        } catch {
            throw DataStoreError.corrupted(error)
        }
    }

    func save(_ appData: AppData) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let data = try Self.encode(appData)
        try data.write(to: fileURL, options: [.atomic])
    }

    func modificationDate() -> Date? {
        let attributes = try? FileManager.default.attributesOfItem(atPath: fileURL.path)
        return attributes?[.modificationDate] as? Date
    }

    /// Откладывает повреждённый файл в сторону, чтобы его можно было восстановить вручную.
    @discardableResult
    func quarantineCurrentFile(now: Date = Date()) -> URL? {
        guard fileExists else { return nil }
        let stamp = Int(now.timeIntervalSince1970)
        let target = directory.appendingPathComponent("memento-data-unreadable-\(stamp).json")
        do {
            try FileManager.default.moveItem(at: fileURL, to: target)
            return target
        } catch {
            return nil
        }
    }

    // MARK: Кодирование

    static func makeEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }

    static func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }

    static func encode(_ appData: AppData) throws -> Data {
        try makeEncoder().encode(appData)
    }

    static func decode(_ data: Data) throws -> AppData {
        try makeDecoder().decode(AppData.self, from: data)
    }
}

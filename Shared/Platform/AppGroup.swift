import Foundation

/// Общий контейнер приложения и виджета.
///
/// Идентификатор группы выводится из bundle id: приложение `com.name.mementomori`
/// и виджет `com.name.mementomori.widget` → группа `group.com.name.mementomori`.
/// Такая же строка должна стоять в обоих файлах .entitlements (см. README).
enum AppGroup {
    static let widgetSuffix = ".widget"

    static var identifier: String {
        let bundleID = Bundle.main.bundleIdentifier ?? "com.yourname.mementomori"
        let base = bundleID.hasSuffix(widgetSuffix) ? String(bundleID.dropLast(widgetSuffix.count)) : bundleID
        return "group." + base
    }

    static var containerURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier)
    }

    /// false — App Group не настроен, и виджет не увидит данные приложения.
    static var isAvailable: Bool { containerURL != nil }

    static var dataDirectory: URL {
        if let containerURL {
            return containerURL.appendingPathComponent("Memento", isDirectory: true)
        }
        return localDataDirectory
    }

    /// Запасной вариант, если App Group недоступен.
    static var localDataDirectory: URL {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return support.appendingPathComponent("Memento", isDirectory: true)
    }

    static var defaults: UserDefaults {
        UserDefaults(suiteName: identifier) ?? .standard
    }
}

extension DataStore {
    /// Хранилище в общем контейнере — им пользуются и приложение, и виджет.
    static var shared: DataStore { DataStore(directory: AppGroup.dataDirectory) }

    /// Если раньше данные лежали локально (App Group не был настроен),
    /// а теперь контейнер доступен — переносим их туда.
    static func migrateLocalDataIfNeeded() {
        guard AppGroup.isAvailable else { return }
        let local = DataStore(directory: AppGroup.localDataDirectory)
        let shared = DataStore.shared
        guard local.fileExists, !shared.fileExists else { return }
        let manager = FileManager.default
        do {
            try manager.createDirectory(at: shared.directory, withIntermediateDirectories: true)
            try manager.copyItem(at: local.fileURL, to: shared.fileURL)
            if manager.fileExists(atPath: local.photosDirectory.path), !manager.fileExists(atPath: shared.photosDirectory.path) {
                try manager.copyItem(at: local.photosDirectory, to: shared.photosDirectory)
            }
        } catch {
            // Оставляем локальную копию нетронутой — попробуем в следующий раз.
        }
    }
}

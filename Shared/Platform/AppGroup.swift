import Foundation

/// Общий контейнер приложения и виджета (App Group).
///
/// Идентификатор группы не зашит в код: берём тот, на который приложение реально подписано.
/// Так виджет продолжает видеть данные, даже если bundle id поменяли прямо в Xcode,
/// а не скриптом scripts/set-bundle-id.sh.
enum AppGroup {
    static let templateIdentifier = "group.com.yourname.mementomori"

    /// Группа, к которой есть доступ, или наиболее вероятная — для сообщений об ошибке.
    static let identifier: String = resolveIdentifier()

    static var containerURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier)
    }

    /// false — App Group не подключён, и виджет не увидит данные приложения.
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

    // MARK: - Определение группы

    /// Кандидаты по порядку: группы из профиля подписи, группа от bundle id приложения, группа из шаблона.
    static var candidates: [String] {
        var result = provisionedGroups()
        result.append("group." + containingAppBundleID)
        result.append(templateIdentifier)
        var seen = Set<String>()
        return result.filter { seen.insert($0).inserted }
    }

    private static func resolveIdentifier() -> String {
        let all = candidates
        let manager = FileManager.default
        return all.first { manager.containerURL(forSecurityApplicationGroupIdentifier: $0) != nil } ?? all.first ?? templateIdentifier
    }

    /// Bundle id приложения: у виджета это его собственный id без последнего компонента.
    private static var containingAppBundleID: String {
        let bundleID = Bundle.main.bundleIdentifier ?? "com.yourname.mementomori"
        guard Bundle.main.bundleURL.pathExtension == "appex",
              let dot = bundleID.lastIndex(of: ".")
        else { return bundleID }
        return String(bundleID[..<dot])
    }

    /// Группы из профиля подписи виджета — приложение читает их из вложенного расширения.
    /// nil — профиля нет (например, в симуляторе), проверить нельзя.
    static var widgetProvisionedGroups: [String]? {
        guard let plugins = Bundle.main.builtInPlugInsURL,
              let items = try? FileManager.default.contentsOfDirectory(at: plugins, includingPropertiesForKeys: nil),
              let appex = items.first(where: { $0.pathExtension == "appex" })
        else { return nil }
        let profile = appex.appendingPathComponent("embedded.mobileprovision")
        guard FileManager.default.fileExists(atPath: profile.path) else { return nil }
        return provisionedGroups(at: profile)
    }

    /// Группы из профиля подписи этого процесса (приложения или виджета).
    static var ownProvisionedGroups: [String] { provisionedGroups() }

    /// App Groups из встроенного профиля подписи (есть в сборках для своего iPhone).
    private static func provisionedGroups() -> [String] {
        guard let url = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision") else { return [] }
        return provisionedGroups(at: url)
    }

    private static func provisionedGroups(at url: URL) -> [String] {
        guard let data = try? Data(contentsOf: url),
              let start = data.range(of: Data("<?xml".utf8)),
              let end = data.range(of: Data("</plist>".utf8), in: start.lowerBound..<data.endIndex)
        else { return [] }
        let plistData = data.subdata(in: start.lowerBound..<end.upperBound)
        guard let plist = try? PropertyListSerialization.propertyList(from: plistData, format: nil) as? [String: Any],
              let entitlements = plist["Entitlements"] as? [String: Any],
              let groups = entitlements["com.apple.security.application-groups"] as? [String]
        else { return [] }
        return groups
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

import Foundation

/// Ссылки из виджетов в приложение: mementomori://letter/<id>, mementomori://letters/new, mementomori://counter/<id>.
enum DeepLink: Equatable {
    case letter(UUID)
    case newLetter
    case counter(UUID)

    static let scheme = "mementomori"

    static func url(for link: DeepLink) -> URL {
        let path: String
        switch link {
        case .letter(let id): path = "letter/\(id.uuidString)"
        case .newLetter: path = "letters/new"
        case .counter(let id): path = "counter/\(id.uuidString)"
        }
        return URL(string: "\(scheme)://\(path)") ?? URL(fileURLWithPath: "/")
    }

    init?(url: URL) {
        guard url.scheme == Self.scheme, let host = url.host else { return nil }
        let id = url.pathComponents.dropFirst().first.flatMap { UUID(uuidString: $0) }
        switch (host, id) {
        case ("letter", let id?): self = .letter(id)
        case ("letters", _): self = .newLetter
        case ("counter", let id?): self = .counter(id)
        default: return nil
        }
    }
}

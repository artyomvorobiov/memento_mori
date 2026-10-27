import UIKit

/// Фото-воспоминания: сжимаем до 1600 px и храним JPEG рядом с данными.
enum PhotoStore {
    private static let cache = NSCache<NSString, UIImage>()

    static func save(_ data: Data, in directory: URL) throws -> String {
        guard let image = UIImage(data: data) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        let resized = image.mementoResized(maxDimension: 1600)
        guard let jpeg = resized.jpegData(compressionQuality: 0.82) else {
            throw CocoaError(.fileWriteUnknown)
        }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let name = UUID().uuidString + ".jpg"
        try jpeg.write(to: directory.appendingPathComponent(name), options: .atomic)
        return name
    }

    static func image(named name: String, in directory: URL) -> UIImage? {
        let key = cacheKey(name, variant: "full")
        if let cached = cache.object(forKey: key) { return cached }
        guard let image = UIImage(contentsOfFile: directory.appendingPathComponent(name).path) else { return nil }
        cache.setObject(image, forKey: key)
        return image
    }

    static func thumbnail(named name: String, in directory: URL) -> UIImage? {
        let key = cacheKey(name, variant: "thumb")
        if let cached = cache.object(forKey: key) { return cached }
        guard let image = UIImage(contentsOfFile: directory.appendingPathComponent(name).path) else { return nil }
        let thumbnail = image.mementoResized(maxDimension: 180)
        cache.setObject(thumbnail, forKey: key)
        return thumbnail
    }

    static func delete(named name: String, in directory: URL) {
        try? FileManager.default.removeItem(at: directory.appendingPathComponent(name))
        cache.removeObject(forKey: cacheKey(name, variant: "full"))
        cache.removeObject(forKey: cacheKey(name, variant: "thumb"))
    }

    static func clearCache() {
        cache.removeAllObjects()
    }

    private static func cacheKey(_ name: String, variant: String) -> NSString {
        "\(variant)/\(name)" as NSString
    }
}

extension UIImage {
    /// Уменьшает картинку так, чтобы большая сторона была не больше maxDimension.
    func mementoResized(maxDimension: CGFloat) -> UIImage {
        let longest = max(size.width, size.height)
        guard longest > maxDimension, longest > 0 else { return self }
        let scale = maxDimension / longest
        let target = CGSize(width: (size.width * scale).rounded(), height: (size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
    }
}

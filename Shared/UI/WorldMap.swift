import SwiftUI

/// Контуры стран для карты (Natural Earth 1:50 000 000, проекция Natural Earth I).
/// Ресурс Shared/Resources/world-map.json собирается скриптом scripts/build-world-map.py.
struct WorldMapData {
    /// Размер карты в её собственных координатах.
    let size: CGSize
    let countries: [String: Path]
    /// Части суши без отдельной страны в каталоге (Косово, ледник Сиачен…).
    let other: Path
    /// Маленькие страны дополнительно рисуются точкой, чтобы их было видно и можно было нажать.
    let dots: [String: CGPoint]
    /// Прямоугольник основной территории: у России без Чукотки за 180-м меридианом, у США без Алеут.
    let mainBounds: [String: CGRect]

    static let shared = WorldMapData.load()

    /// Страна в точке карты. Сначала маленькие страны-точки, потом контуры.
    func country(at point: CGPoint, dotRadius: CGFloat) -> String? {
        var nearest: (code: String, distance: CGFloat)?
        for (code, center) in dots {
            let distance = hypot(center.x - point.x, center.y - point.y)
            if distance <= dotRadius, distance < (nearest?.distance ?? .infinity) {
                nearest = (code, distance)
            }
        }
        if let nearest { return nearest.code }
        return countries.first { $0.value.contains(point) }?.key
    }

    /// Прямоугольник страны на карте (по основной территории).
    func bounds(of code: String) -> CGRect? {
        if let rect = mainBounds[code], rect.width > 1 || rect.height > 1 {
            return rect
        }
        if let path = countries[code] {
            let rect = path.boundingRect
            if rect.width > 1 || rect.height > 1 { return rect }
        }
        if let dot = dots[code] {
            return CGRect(x: dot.x - 10, y: dot.y - 10, width: 20, height: 20)
        }
        return nil
    }

    // MARK: Загрузка

    private struct RawMap: Decodable {
        let width: Double
        let height: Double
        let countries: [String: [[Double]]]
        let other: [[Double]]
        let dots: [String: [Double]]
    }

    private static func load() -> WorldMapData {
        let empty = WorldMapData(size: CGSize(width: 2000, height: 872), countries: [:], other: Path(), dots: [:], mainBounds: [:])
        guard let url = Bundle.main.url(forResource: "world-map", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let raw = try? JSONDecoder().decode(RawMap.self, from: data)
        else { return empty }
        var countries: [String: Path] = [:]
        var mainBounds: [String: CGRect] = [:]
        for (code, rings) in raw.countries {
            countries[code] = path(from: rings)
            mainBounds[code] = rings
                .map { path(from: [$0]).boundingRect }
                .max { $0.width * $0.height < $1.width * $1.height }
        }
        var dots: [String: CGPoint] = [:]
        for (code, point) in raw.dots where point.count == 2 {
            dots[code] = CGPoint(x: point[0], y: point[1])
        }
        return WorldMapData(
            size: CGSize(width: raw.width, height: raw.height),
            countries: countries,
            other: path(from: raw.other),
            dots: dots,
            mainBounds: mainBounds
        )
    }

    private static func path(from rings: [[Double]]) -> Path {
        var path = Path()
        for ring in rings where ring.count >= 6 {
            path.move(to: CGPoint(x: ring[0], y: ring[1]))
            var index = 2
            while index + 1 < ring.count {
                path.addLine(to: CGPoint(x: ring[index], y: ring[index + 1]))
                index += 2
            }
            path.closeSubpath()
        }
        return path
    }
}

/// Палитра карты.
struct WorldMapStyle {
    var land = Color.white.opacity(0.16)
    var other = Color.white.opacity(0.1)
    var visited = Theme.accent
    var wished = Theme.accent.opacity(0.35)
    var border = Color.black
    var selection = Color.white
    /// Точки маленьких стран, где ещё не был: нужны на интерактивной карте, чтобы по ним можно было попасть.
    var showsUnmarkedDots = true
    var unmarkedDot = Color.white.opacity(0.3)
}

/// Карта мира: посещённые страны закрашены акцентом, желанные — полупрозрачно.
/// zoom и offset задают масштаб и сдвиг относительно положения «вся карта по центру».
struct WorldMapView: View {
    let visited: Set<String>
    let wished: Set<String>
    var selected: String? = nil
    var zoom: CGFloat = 1
    var offset: CGSize = .zero
    var fill: FitMode = .fit
    var style = WorldMapStyle()

    enum FitMode {
        /// Вся карта целиком.
        case fit
        /// Карта заполняет высоту, лишнее по ширине уходит за края.
        case fillHeight
    }

    var body: some View {
        Canvas { context, size in
            let map = WorldMapData.shared
            let transform = WorldMapView.transform(map: map.size, in: size, zoom: zoom, offset: offset, fill: fill)
            let scale = transform.a
            context.concatenate(transform)

            context.fill(map.other, with: .color(style.other))
            for (code, path) in map.countries {
                context.fill(path, with: .color(color(for: code)))
            }
            let borderWidth = 0.6 / scale
            for path in map.countries.values {
                context.stroke(path, with: .color(style.border), lineWidth: borderWidth)
            }

            let dotRadius = max(2.2, 1.6 * min(zoom, 3)) / scale
            for (code, center) in map.dots {
                let isUnmarked = status(of: code) == .none
                if isUnmarked && !style.showsUnmarkedDots { continue }
                let rect = CGRect(x: center.x - dotRadius, y: center.y - dotRadius, width: dotRadius * 2, height: dotRadius * 2)
                context.fill(Path(ellipseIn: rect), with: .color(isUnmarked ? style.unmarkedDot : color(for: code)))
            }

            if let selected {
                if let path = map.countries[selected] {
                    context.stroke(path, with: .color(style.selection), lineWidth: 1.6 / scale)
                }
                if let center = map.dots[selected] {
                    let radius = dotRadius * 2
                    let rect = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
                    context.stroke(Path(ellipseIn: rect), with: .color(style.selection), lineWidth: 1.4 / scale)
                }
            }
        }
    }

    private func status(of code: String) -> CountryStatus {
        if visited.contains(code) { return .visited }
        if wished.contains(code) { return .wished }
        return .none
    }

    private func color(for code: String) -> Color {
        switch status(of: code) {
        case .visited: style.visited
        case .wished: style.wished
        case .none: style.land
        }
    }

    /// Преобразование из координат карты в координаты вида.
    static func transform(map: CGSize, in size: CGSize, zoom: CGFloat, offset: CGSize, fill: FitMode) -> CGAffineTransform {
        guard map.width > 0, map.height > 0, size.width > 0, size.height > 0 else { return .identity }
        let base: CGFloat
        switch fill {
        case .fit: base = min(size.width / map.width, size.height / map.height)
        case .fillHeight: base = max(size.height / map.height, size.width / map.width)
        }
        let scale = base * zoom
        let tx = (size.width - map.width * scale) / 2 + offset.width
        let ty = (size.height - map.height * scale) / 2 + offset.height
        return CGAffineTransform(a: scale, b: 0, c: 0, d: scale, tx: tx, ty: ty)
    }
}

extension WorldMapView: Animatable {
    /// Плавное «перелётом» приближение к стране.
    var animatableData: AnimatablePair<CGFloat, AnimatablePair<CGFloat, CGFloat>> {
        get { AnimatablePair(zoom, AnimatablePair(offset.width, offset.height)) }
        set {
            zoom = newValue.first
            offset = CGSize(width: newValue.second.first, height: newValue.second.second)
        }
    }
}

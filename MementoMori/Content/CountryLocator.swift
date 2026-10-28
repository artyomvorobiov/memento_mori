import Foundation

/// Страна по координатам — без интернета, по границам Natural Earth 1:10m
/// из ресурса country-lookup.json (собирается скриптом scripts/build-country-lookup.py).
struct CountryLocator {
    /// Кольцо границы в градусах: x — долгота, y — широта.
    private struct Ring {
        let xs: [Double]
        let ys: [Double]
        let minX: Double, maxX: Double, minY: Double, maxY: Double
        let area: Double

        init(xs: [Double], ys: [Double]) {
            self.xs = xs
            self.ys = ys
            minX = xs.min() ?? 0
            maxX = xs.max() ?? 0
            minY = ys.min() ?? 0
            maxY = ys.max() ?? 0
            var sum = 0.0
            var j = xs.count - 1
            for i in 0..<xs.count {
                sum += xs[j] * ys[i] - xs[i] * ys[j]
                j = i
            }
            area = abs(sum) / 2
        }

        func boxContains(x: Double, y: Double, margin: (x: Double, y: Double) = (0, 0)) -> Bool {
            x >= minX - margin.x && x <= maxX + margin.x && y >= minY - margin.y && y <= maxY + margin.y
        }

        /// Луч вправо из точки: нечётное число пересечений — точка внутри.
        func contains(x: Double, y: Double) -> Bool {
            var inside = false
            var j = xs.count - 1
            for i in 0..<xs.count {
                let yi = ys[i], yj = ys[j]
                if (yi > y) != (yj > y) {
                    let crossX = xs[i] + (y - yi) / (yj - yi) * (xs[j] - xs[i])
                    if x < crossX { inside.toggle() }
                }
                j = i
            }
            return inside
        }

        /// Расстояние до границы в километрах (в местной плоской проекции — для малых расстояний этого хватает).
        func distanceKm(x: Double, y: Double) -> Double {
            let kx = 111.32 * cos(y * .pi / 180)
            let ky = 110.57
            var best = Double.infinity
            var j = xs.count - 1
            for i in 0..<xs.count {
                let ax = (xs[j] - x) * kx, ay = (ys[j] - y) * ky
                let bx = (xs[i] - x) * kx, by = (ys[i] - y) * ky
                let dx = bx - ax, dy = by - ay
                let length = dx * dx + dy * dy
                let t = length > 0 ? max(0, min(1, -(ax * dx + ay * dy) / length)) : 0
                let px = ax + t * dx, py = ay + t * dy
                best = min(best, (px * px + py * py).squareRoot())
                j = i
            }
            return best
        }
    }

    private struct Shape {
        let code: String
        let rings: [Ring]
        let minX: Double, maxX: Double, minY: Double, maxY: Double

        init(code: String, rings: [Ring]) {
            self.code = code
            self.rings = rings
            minX = rings.map(\.minX).min() ?? 0
            maxX = rings.map(\.maxX).max() ?? 0
            minY = rings.map(\.minY).min() ?? 0
            maxY = rings.map(\.maxY).max() ?? 0
        }

        func boxContains(x: Double, y: Double, margin: (x: Double, y: Double) = (0, 0)) -> Bool {
            x >= minX - margin.x && x <= maxX + margin.x && y >= minY - margin.y && y <= maxY + margin.y
        }
    }

    /// Места, которых нет в данных отдельной страной (Ватикан, Реюньон): круг вокруг центра.
    private struct Circle {
        let code: String
        let latitude: Double
        let longitude: Double
        let radiusKm: Double
    }

    private let shapes: [Shape]
    private let circles: [Circle]

    var countryCount: Int { shapes.count + circles.count }

    private struct Raw: Decodable {
        let scale: Double
        let countries: [String: [[Int]]]
        let circles: [String: [Double]]?
    }

    init(data: Data) throws {
        let raw = try JSONDecoder().decode(Raw.self, from: data)
        let scale = raw.scale > 0 ? raw.scale : 1000
        shapes = raw.countries.map { code, encodedRings in
            let rings = encodedRings.compactMap { values -> Ring? in
                guard values.count >= 6 else { return nil }
                var xs: [Double] = []
                var ys: [Double] = []
                xs.reserveCapacity(values.count / 2)
                ys.reserveCapacity(values.count / 2)
                // Первая точка целиком, дальше — разницы с предыдущей.
                var x = 0, y = 0
                var index = 0
                while index + 1 < values.count {
                    x += values[index]
                    y += values[index + 1]
                    xs.append(Double(x) / scale)
                    ys.append(Double(y) / scale)
                    index += 2
                }
                return Ring(xs: xs, ys: ys)
            }
            return Shape(code: code, rings: rings)
        }
        .sorted { $0.code < $1.code }
        circles = (raw.circles ?? [:]).compactMap { code, values in
            values.count == 3 ? Circle(code: code, latitude: values[0], longitude: values[1], radiusKm: values[2]) : nil
        }
        .sorted { $0.code < $1.code }
    }

    /// Встроенные в приложение границы.
    static func bundled() -> CountryLocator? {
        guard let url = Bundle.main.url(forResource: "country-lookup", withExtension: "json"),
              let data = try? Data(contentsOf: url)
        else { return nil }
        return try? CountryLocator(data: data)
    }

    /// Страна в точке. Если точка в море рядом с берегом (фото с пляжа или с лодки) —
    /// ближайшая страна не дальше nearbyKm.
    func country(latitude: Double, longitude: Double, nearbyKm: Double = 12) -> String? {
        guard latitude.isFinite, longitude.isFinite, abs(latitude) <= 90, abs(longitude) <= 180 else { return nil }
        let x = longitude, y = latitude

        for circle in circles where Self.distanceKm(latitude, longitude, circle.latitude, circle.longitude) <= circle.radiusKm {
            return circle.code
        }

        // Внутри страны: нечётное число её колец (дырки — анклавы вроде Лесото внутри ЮАР).
        // Если подходят несколько стран, побеждает меньшее кольцо: Монако, а не Франция вокруг.
        var best: (code: String, area: Double)?
        for shape in shapes where shape.boxContains(x: x, y: y) {
            var inside = false
            var smallest = Double.infinity
            for ring in shape.rings where ring.boxContains(x: x, y: y) && ring.contains(x: x, y: y) {
                inside.toggle()
                smallest = min(smallest, ring.area)
            }
            if inside, smallest < (best?.area ?? .infinity) {
                best = (shape.code, smallest)
            }
        }
        if let best { return best.code }

        guard nearbyKm > 0 else { return nil }
        let margin = (x: nearbyKm / (111.32 * max(0.05, cos(y * .pi / 180))), y: nearbyKm / 110.57)
        var nearest: (code: String, distance: Double)?
        for shape in shapes where shape.boxContains(x: x, y: y, margin: margin) {
            for ring in shape.rings where ring.boxContains(x: x, y: y, margin: margin) {
                let distance = ring.distanceKm(x: x, y: y)
                if distance <= nearbyKm, distance < (nearest?.distance ?? .infinity) {
                    nearest = (shape.code, distance)
                }
            }
        }
        return nearest?.code
    }

    /// Расстояние по большому кругу в километрах.
    static func distanceKm(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
        let rad = Double.pi / 180
        let dLat = (lat2 - lat1) * rad
        let dLon = (lon2 - lon1) * rad
        let a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1 * rad) * cos(lat2 * rad) * sin(dLon / 2) * sin(dLon / 2)
        return 6371 * 2 * atan2(a.squareRoot(), (1 - a).squareRoot())
    }
}

// MARK: - Сводка по фото

/// Найденная в фото страна: сколько снимков и в какие годы.
struct PhotoCountryHit: Identifiable, Hashable {
    let code: String
    var photos: Int
    var first: Date?
    var last: Date?

    var id: String { code }

    /// «2019–2024» или «2023».
    func yearsText(calendar: Calendar = .memento) -> String? {
        guard let first, let last else { return nil }
        let start = calendar.component(.year, from: first)
        let end = calendar.component(.year, from: last)
        return start == end ? "\(start)" : "\(start)–\(end)"
    }
}

/// Сводит геометки фото в страны. Отделено от Photos, чтобы проверять без iPhone.
struct PhotoCountryTally {
    private struct Cell {
        var latitude: Double
        var longitude: Double
        var photos: Int
        var first: Date?
        var last: Date?
    }

    /// Фото группируются по клеткам ≈ 1 км, чтобы не искать страну для каждого снимка.
    private var cells: [Int64: Cell] = [:]
    private(set) var geotagged = 0

    init() {}

    mutating func add(latitude: Double, longitude: Double, date: Date?) {
        guard latitude.isFinite, longitude.isFinite, abs(latitude) <= 90, abs(longitude) <= 180 else { return }
        // (0, 0) — «пустая» геометка у некоторых камер и приложений.
        if latitude == 0 && longitude == 0 { return }
        geotagged += 1
        let key = Int64((latitude * 100).rounded()) * 1_000_000 + Int64((longitude * 100).rounded())
        if var cell = cells[key] {
            cell.photos += 1
            if let date {
                cell.first = min(cell.first ?? date, date)
                cell.last = max(cell.last ?? date, date)
            }
            cells[key] = cell
        } else {
            cells[key] = Cell(latitude: latitude, longitude: longitude, photos: 1, first: date, last: date)
        }
    }

    var cellCount: Int { cells.count }

    /// Страны по убыванию числа фото.
    func countries(using locator: CountryLocator) -> [PhotoCountryHit] {
        var hits: [String: PhotoCountryHit] = [:]
        for cell in cells.values {
            guard let code = locator.country(latitude: cell.latitude, longitude: cell.longitude) else { continue }
            var hit = hits[code] ?? PhotoCountryHit(code: code, photos: 0, first: nil, last: nil)
            hit.photos += cell.photos
            if let first = cell.first { hit.first = min(hit.first ?? first, first) }
            if let last = cell.last { hit.last = max(hit.last ?? last, last) }
            hits[code] = hit
        }
        return hits.values.sorted { $0.photos != $1.photos ? $0.photos > $1.photos : $0.code < $1.code }
    }
}

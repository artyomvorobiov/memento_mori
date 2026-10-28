import CoreLocation
import Photos

/// Ищет страны по геометкам фото и видео. Всё считается на телефоне: снимки никуда
/// не отправляются, читаются только координаты и даты.
enum PhotoCountryScanner {
    enum Access {
        case full
        /// Пользователь дал доступ только к выбранным фото.
        case limited
        case denied
    }

    struct Result {
        let countries: [PhotoCountryHit]
        /// Сколько всего снимков просмотрено и у скольких есть геометка.
        let scanned: Int
        let geotagged: Int
    }

    static func requestAccess() async -> Access {
        let status = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        switch status {
        case .authorized: return .full
        case .limited: return .limited
        default: return .denied
        }
    }

    /// progress получает долю просмотренных снимков от 0 до 1.
    static func scan(progress: @escaping @Sendable (Double) -> Void) async -> Result {
        await Task.detached(priority: .userInitiated) {
            let options = PHFetchOptions()
            // Только своя медиатека — без общих альбомов, где чужие поездки.
            options.includeAssetSourceTypes = [.typeUserLibrary, .typeiTunesSynced]
            let assets = PHAsset.fetchAssets(with: options)
            let total = assets.count
            var tally = PhotoCountryTally()
            for index in 0..<total {
                if index % 400 == 0 {
                    progress(Double(index) / Double(total))
                }
                let asset = assets.object(at: index)
                guard let coordinate = asset.location?.coordinate, CLLocationCoordinate2DIsValid(coordinate) else { continue }
                tally.add(latitude: coordinate.latitude, longitude: coordinate.longitude, date: asset.creationDate)
            }
            progress(1)
            let countries = CountryLocator.bundled().map { tally.countries(using: $0) } ?? []
            return Result(countries: countries, scanned: total, geotagged: tally.geotagged)
        }.value
    }
}

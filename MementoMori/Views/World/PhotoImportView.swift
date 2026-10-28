import SwiftUI
import UIKit

/// «Найти страны в фото»: по геометкам снимков предлагает отметить страны посещёнными.
struct PhotoImportView: View {
    enum Phase: Equatable {
        case intro
        case scanning(Double)
        case results
        case denied
    }

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var phase: Phase
    @State private var result: PhotoCountryScanner.Result?
    @State private var selected: Set<String> = []
    @State private var isLimited = false
    @State private var didPreselect = false

    init(demo: Bool = false) {
        if demo {
            let demoResult = PhotoImportView.demoResult
            _phase = State(initialValue: .results)
            _result = State(initialValue: demoResult)
        } else {
            _phase = State(initialValue: .intro)
        }
    }

    var body: some View {
        NavigationStack {
            Group {
                switch phase {
                case .intro:
                    intro
                case .scanning(let progress):
                    scanning(progress)
                case .results:
                    results
                case .denied:
                    denied
                }
            }
            .background(Theme.background)
            .navigationTitle("Страны из фото")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { dismiss() }
                }
            }
            .onAppear(perform: preselect)
        }
    }

    // MARK: Шаги

    private var intro: some View {
        VStack(spacing: 18) {
            Spacer()
            Image(systemName: "photo.on.rectangle.angled")
                .font(.system(size: 56, weight: .light))
                .foregroundStyle(Theme.accent)
            Text("Заполни карту за минуту")
                .font(.system(.title2, design: .serif).weight(.medium))
            Text("Приложение посмотрит геометки в фото и видео и найдёт страны, где они сняты. Отметишь посещёнными только те, что выберешь.")
                .font(.subheadline)
                .foregroundStyle(Theme.secondaryText)
                .multilineTextAlignment(.center)
            Label("Всё считается на телефоне: фото никуда не отправляются, читаются только место и дата съёмки.", systemImage: "lock")
                .font(.footnote)
                .foregroundStyle(Theme.tertiaryText)
                .multilineTextAlignment(.leading)
                .padding(.top, 4)
            Spacer()
            Button(action: start) {
                AccentButtonLabel(title: "Найти страны", systemImage: "magnifyingglass")
            }
            .buttonStyle(.plain)
        }
        .padding(24)
    }

    private func scanning(_ progress: Double) -> some View {
        VStack(spacing: 16) {
            Spacer()
            ProgressView(value: progress)
                .tint(Theme.accent)
                .frame(maxWidth: 240)
            Text(progress < 1 ? "Смотрю фото… \(Format.percent(progress, digits: 0))" : "Определяю страны…")
                .font(.subheadline)
                .foregroundStyle(Theme.secondaryText)
                .monospacedDigit()
            Spacer()
        }
        .padding(24)
    }

    private var denied: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "photo.badge.exclamationmark")
                .font(.system(size: 48, weight: .light))
                .foregroundStyle(Theme.secondaryText)
            Text("Нет доступа к фото")
                .font(.headline)
            Text("Разреши доступ в настройках iPhone: Memento Mori → Фото → «Полный доступ».")
                .font(.subheadline)
                .foregroundStyle(Theme.secondaryText)
                .multilineTextAlignment(.center)
            Spacer()
            Button {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            } label: {
                AccentButtonLabel(title: "Открыть настройки", systemImage: "gear")
            }
            .buttonStyle(.plain)
        }
        .padding(24)
    }

    @ViewBuilder
    private var results: some View {
        let found = result?.countries ?? []
        let fresh = found.filter { !model.isVisited($0.code) }
        let known = found.filter { model.isVisited($0.code) }
        List {
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text(found.isEmpty ? "Стран не нашлось" : "Нашлось \(Format.count(found.count, "страна", "страны", "стран"))")
                        .font(.system(.title2, design: .serif).weight(.medium))
                    Text(summaryText(found: found))
                        .font(.subheadline)
                        .foregroundStyle(Theme.secondaryText)
                }
                .listRowBackground(Color.clear)
            }

            if !fresh.isEmpty {
                Section {
                    ForEach(fresh) { hit in
                        Button {
                            toggle(hit.code)
                        } label: {
                            HitRow(hit: hit, isSelected: selected.contains(hit.code), isKnown: false)
                        }
                        .buttonStyle(.plain)
                    }
                } header: {
                    Text("Новые — отметить посещёнными")
                } footer: {
                    Text("Сними галочку, если фото попало туда случайно — например, прислали чужое.")
                }
            }

            if !known.isEmpty {
                Section("Уже отмечены") {
                    ForEach(known) { hit in
                        HitRow(hit: hit, isSelected: true, isKnown: true)
                    }
                }
            }

            if isLimited {
                Section {
                    Text("Есть доступ только к выбранным фото, поэтому страны найдены только по ним. Открыть всю медиатеку можно в настройках iPhone: Memento Mori → Фото.")
                        .font(.footnote)
                        .foregroundStyle(Theme.secondaryText)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .safeAreaInset(edge: .bottom) {
            if !fresh.isEmpty {
                Button(action: apply) {
                    AccentButtonLabel(
                        title: selected.isEmpty ? "Ничего не выбрано" : "Отметить \(Format.count(selected.count, "страну", "страны", "стран"))",
                        systemImage: "checkmark"
                    )
                }
                .buttonStyle(.plain)
                .disabled(selected.isEmpty)
                .opacity(selected.isEmpty ? 0.5 : 1)
                .padding(.horizontal, 20)
                .padding(.bottom, 8)
            }
        }
    }

    private func summaryText(found: [PhotoCountryHit]) -> String {
        guard let result else { return "" }
        if result.geotagged == 0 {
            return result.scanned == 0
                ? "В медиатеке пока нет фото."
                : "У фото нет геометок. Чтобы камера их сохраняла, включи: Настройки → Конфиденциальность и безопасность → Службы геолокации → Камера."
        }
        if found.isEmpty {
            return "Геометки есть у \(Format.number(result.geotagged)) фото, но ни одна не попала в страну из списка."
        }
        return "По \(Format.number(result.geotagged)) фото с геометками из \(Format.number(result.scanned))."
    }

    // MARK: Действия

    private func start() {
        Task {
            let access = await PhotoCountryScanner.requestAccess()
            guard access != .denied else {
                phase = .denied
                return
            }
            isLimited = access == .limited
            phase = .scanning(0)
            let scanned = await PhotoCountryScanner.scan { value in
                Task { @MainActor in
                    if case .scanning = phase { phase = .scanning(value) }
                }
            }
            result = scanned
            didPreselect = false
            preselect()
            withAnimation { phase = .results }
        }
    }

    /// Новые страны выбраны сразу — снять галочку проще, чем ставить.
    private func preselect() {
        guard !didPreselect, let result else { return }
        didPreselect = true
        selected = Set(result.countries.map(\.code).filter { !model.isVisited($0) })
    }

    private func toggle(_ code: String) {
        if selected.contains(code) {
            selected.remove(code)
        } else {
            selected.insert(code)
        }
    }

    private func apply() {
        model.markVisited(selected.sorted())
        dismiss()
    }

    // MARK: Демо для скриншотов

    private static var demoResult: PhotoCountryScanner.Result {
        let calendar = Calendar.memento
        func year(_ value: Int) -> Date {
            calendar.date(from: DateComponents(year: value, month: 6, day: 1)) ?? Date()
        }
        let hits = [
            PhotoCountryHit(code: "RU", photos: 2_431, first: year(2014), last: year(2026)),
            PhotoCountryHit(code: "IT", photos: 132, first: year(2019), last: year(2024)),
            PhotoCountryHit(code: "TH", photos: 120, first: year(2023), last: year(2023)),
            PhotoCountryHit(code: "FR", photos: 87, first: year(2018), last: year(2025)),
            PhotoCountryHit(code: "ES", photos: 64, first: year(2022), last: year(2022)),
            PhotoCountryHit(code: "GR", photos: 45, first: year(2021), last: year(2021)),
            PhotoCountryHit(code: "GE", photos: 41, first: year(2025), last: year(2025)),
            PhotoCountryHit(code: "PT", photos: 28, first: year(2017), last: year(2017)),
            PhotoCountryHit(code: "TR", photos: 33, first: year(2016), last: year(2024)),
            PhotoCountryHit(code: "CZ", photos: 19, first: year(2015), last: year(2015)),
            PhotoCountryHit(code: "AT", photos: 12, first: year(2015), last: year(2015)),
            PhotoCountryHit(code: "NL", photos: 9, first: year(2019), last: year(2019)),
            PhotoCountryHit(code: "HU", photos: 4, first: year(2015), last: year(2015)),
        ]
        return PhotoCountryScanner.Result(countries: hits, scanned: 18_204, geotagged: 3_412)
    }
}

private struct HitRow: View {
    let hit: PhotoCountryHit
    let isSelected: Bool
    let isKnown: Bool

    var body: some View {
        HStack(spacing: 12) {
            Text(flagEmoji(for: hit.code))
                .font(.system(size: 26))
            VStack(alignment: .leading, spacing: 2) {
                Text(Country.name(for: hit.code))
                    .foregroundStyle(isKnown ? Theme.secondaryText : Color.white)
                Text([Format.number(hit.photos) + " фото", hit.yearsText()].compactMap { $0 }.joined(separator: " · "))
                    .font(.caption)
                    .foregroundStyle(Theme.tertiaryText)
            }
            Spacer()
            Image(systemName: isKnown ? "checkmark.seal.fill" : isSelected ? "checkmark.circle.fill" : "circle")
                .font(.title3)
                .foregroundStyle(isKnown ? Theme.tertiaryText : isSelected ? Theme.accent : Theme.tertiaryText)
        }
        .contentShape(Rectangle())
    }
}

import Foundation
import Observation
import UIKit
import UserNotifications
import WidgetKit

/// Изменение фото-воспоминания при сохранении.
enum PhotoChange {
    case keep
    case remove
    case replace(Data)
}

/// Состояние приложения. Единственный источник правды для всех экранов:
/// любое изменение сохраняется на диск, обновляет виджеты и расписание напоминаний.
@MainActor
@Observable
final class AppModel {
    private(set) var data: AppData
    /// Файл данных есть, но прочитать его сейчас нельзя — ничего не записываем.
    private(set) var storageUnavailable = false
    /// Сообщение о проблеме с данными, которое нужно показать пользователю.
    var dataProblem: String? = nil

    private let store: DataStore
    private let scheduler = NotificationScheduler()
    /// В демо-режиме не трогаем настоящие напоминания.
    private let managesReminders: Bool
    @ObservationIgnored private var lastSeenModification: Date?
    @ObservationIgnored private var rescheduleTask: Task<Void, Never>?

    init(store: DataStore? = nil, managesReminders: Bool = true) {
        if store == nil {
            DataStore.migrateLocalDataIfNeeded()
        }
        let store = store ?? DataStore.shared
        self.store = store
        self.managesReminders = managesReminders
        self.data = AppData()
        load()
    }

    /// Модель для запуска приложения. В отладочной сборке аргумент `-MementoDemo`
    /// подставляет демонстрационные данные во временную папку — настоящие не трогаются.
    static func makeForLaunch() -> AppModel {
        if DebugLaunch.isDemo {
            let directory = FileManager.default.temporaryDirectory.appendingPathComponent("MementoDemo", isDirectory: true)
            let store = DataStore(directory: directory)
            try? store.save(DemoData.make())
            return AppModel(store: store, managesReminders: false)
        }
        return AppModel()
    }

    // MARK: - Чтение

    var profile: Profile? { data.profile }

    var trackingSince: Date { data.profile?.createdAt ?? Date() }

    func stats(at date: Date = Date()) -> LifeStats? {
        data.profile.map { LifeStats(profile: $0, now: date) }
    }

    /// Активные цели: сначала в фокусе, потом по ближайшему сроку, потом новые.
    var activeGoals: [Goal] {
        let birthDate = data.profile?.birthDate ?? Date()
        return data.activeGoals.sorted { lhs, rhs in
            if lhs.isFocused != rhs.isFocused { return lhs.isFocused }
            let left = lhs.deadline(birthDate: birthDate)
            let right = rhs.deadline(birthDate: birthDate)
            switch (left, right) {
            case let (l?, r?) where l != r: return l < r
            case (.some, .none): return true
            case (.none, .some): return false
            default: return lhs.createdAt > rhs.createdAt
            }
        }
    }

    var completedGoals: [Goal] {
        data.completedGoals.sorted { ($0.completedAt ?? .distantPast) > ($1.completedAt ?? .distantPast) }
    }

    var focusedGoals: [Goal] {
        data.focusedGoals.sorted { $0.createdAt < $1.createdAt }
    }

    func goal(id: UUID) -> Goal? {
        data.goal(id: id)
    }

    func hasGoal(titled title: String) -> Bool {
        let needle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        return data.goals.contains { $0.title.compare(needle, options: [.caseInsensitive, .diacriticInsensitive]) == .orderedSame }
    }

    // MARK: - Фото

    var photosDirectory: URL { store.photosDirectory }

    func photo(for goal: Goal) -> UIImage? {
        guard let name = goal.photoFileName else { return nil }
        return PhotoStore.image(named: name, in: photosDirectory)
    }

    func thumbnail(for goal: Goal) -> UIImage? {
        guard let name = goal.photoFileName else { return nil }
        return PhotoStore.thumbnail(named: name, in: photosDirectory)
    }

    // MARK: - Онбординг и профиль

    func completeOnboarding(birthDate: Date, expectancy: ExpectancyChoice, ideas: [Idea], remindersEnabled: Bool) {
        update { data in
            data.profile = Profile(
                birthDate: Calendar.memento.startOfDay(for: birthDate),
                lifeExpectancy: expectancy.years,
                expectancySource: expectancy.source,
                expectancyCountry: expectancy.country,
                createdAt: Date()
            )
            for idea in ideas {
                data.upsert(Goal(title: idea.title, category: idea.category))
            }
            if data.counters.isEmpty {
                data.counters = CounterPresets.defaults
            }
            data.reminders.isEnabled = false
        }
        if remindersEnabled {
            Task {
                let granted = await scheduler.requestAuthorization()
                if granted {
                    update { $0.reminders.isEnabled = true }
                }
            }
        }
    }

    func updateProfile(birthDate: Date, expectancy: ExpectancyChoice) {
        update { data in
            data.profile?.birthDate = Calendar.memento.startOfDay(for: birthDate)
            data.profile?.lifeExpectancy = expectancy.years
            data.profile?.expectancySource = expectancy.source
            data.profile?.expectancyCountry = expectancy.country
        }
    }

    // MARK: - Цели

    func saveGoal(_ goal: Goal) {
        update { $0.upsert(goal) }
    }

    func addIdea(_ idea: Idea) {
        guard !hasGoal(titled: idea.title) else { return }
        update { $0.upsert(Goal(title: idea.title, category: idea.category)) }
    }

    func deleteGoal(id: UUID) {
        var removed: Goal?
        update { removed = $0.removeGoal(id: id) }
        if let name = removed?.photoFileName {
            PhotoStore.delete(named: name, in: photosDirectory)
        }
    }

    /// Отметить выполненной (или отредактировать воспоминание уже выполненной цели).
    func saveCompletion(goalID: UUID, date: Date, reflection: String, photo: PhotoChange) {
        guard let goal = data.goal(id: goalID) else { return }
        var photoName = goal.photoFileName
        switch photo {
        case .keep:
            break
        case .remove:
            if let old = goal.photoFileName {
                PhotoStore.delete(named: old, in: photosDirectory)
            }
            photoName = nil
        case .replace(let imageData):
            if let saved = try? PhotoStore.save(imageData, in: photosDirectory) {
                if let old = goal.photoFileName {
                    PhotoStore.delete(named: old, in: photosDirectory)
                }
                photoName = saved
            }
        }
        update { data in
            data.completeGoal(id: goalID, at: date, reflection: reflection)
            if let index = data.goals.firstIndex(where: { $0.id == goalID }) {
                data.goals[index].photoFileName = photoName
            }
        }
    }

    func toggleStep(goalID: UUID, stepID: UUID) {
        update { $0.toggleStep(goalID: goalID, stepID: stepID) }
    }

    func reopenGoal(id: UUID) {
        update { $0.reopenGoal(id: id) }
    }

    /// Возвращает false, если в фокусе уже максимум целей.
    @discardableResult
    func toggleFocus(id: UUID) -> Bool {
        var changed = false
        update { changed = $0.toggleFocus(id: id) }
        return changed
    }

    // MARK: - Страны

    func isVisited(_ code: String) -> Bool {
        data.isVisited(code)
    }

    func wishGoal(forCountry code: String) -> Goal? {
        data.wishGoal(forCountry: code)
    }

    func toggleWish(_ country: Country) {
        if let goal = data.wishGoal(forCountry: country.code) {
            deleteGoal(id: goal.id)
        } else {
            update { $0.upsert(Goal(title: country.name, category: .travel, countryCode: country.code)) }
        }
    }

    func toggleVisited(_ country: Country) {
        update { data in
            if data.isVisited(country.code) {
                data.markVisited(country.code, false)
            } else if let wish = data.wishGoal(forCountry: country.code) {
                data.completeGoal(id: wish.id, at: Date())
            } else {
                data.markVisited(country.code, true)
            }
        }
    }

    // MARK: - Счётчики

    func saveCounter(_ counter: LifeCounter) {
        update { data in
            if let index = data.counters.firstIndex(where: { $0.id == counter.id }) {
                data.counters[index] = counter
            } else {
                data.counters.append(counter)
            }
        }
    }

    func deleteCounter(id: UUID) {
        update { $0.counters.removeAll { $0.id == id } }
    }

    // MARK: - Напоминания

    func updateReminders(_ settings: ReminderSettings) {
        guard settings != data.reminders else { return }
        update { $0.reminders = settings }
    }

    func requestNotificationPermission() async -> Bool {
        await scheduler.requestAuthorization()
    }

    func notificationsDenied() async -> Bool {
        await scheduler.authorizationStatus() == .denied
    }

    func sendTestNotification() async {
        await scheduler.sendTest(data: data)
    }

    // MARK: - Резервные копии

    func makeBackupFile() throws -> URL {
        let stamp = Date().formatted(.iso8601.year().month().day())
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("memento-mori-\(stamp).json")
        try DataStore.encode(data).write(to: url, options: .atomic)
        return url
    }

    func readBackup(from url: URL) throws -> AppData {
        let accessing = url.startAccessingSecurityScopedResource()
        defer {
            if accessing { url.stopAccessingSecurityScopedResource() }
        }
        return try DataStore.decode(Data(contentsOf: url))
    }

    func replaceAll(with newData: AppData) {
        update { $0 = newData }
    }

    func resetAll() {
        try? FileManager.default.removeItem(at: photosDirectory)
        PhotoStore.clearCache()
        update { $0 = AppData() }
    }

    // MARK: - Жизненный цикл

    /// Приложение стало активным: подхватываем изменения от виджета
    /// и обновляем расписание напоминаний.
    func handleBecameActive() {
        if storageUnavailable || store.modificationDate() != lastSeenModification {
            load()
        }
        scheduleReminders()
    }

    private func load() {
        do {
            if let loaded = try store.load() {
                data = loaded
            }
            storageUnavailable = false
            lastSeenModification = store.modificationDate()
        } catch DataStoreError.unreadable(_) {
            storageUnavailable = true
        } catch {
            let moved = store.quarantineCurrentFile()
            data = AppData()
            storageUnavailable = false
            lastSeenModification = nil
            if let moved {
                dataProblem = "Файл с данными повреждён, поэтому приложение начало с чистого листа. Старый файл сохранён как «\(moved.lastPathComponent)»."
            } else {
                dataProblem = "Файл с данными повреждён, поэтому приложение начало с чистого листа."
            }
        }
    }

    private func update(_ change: (inout AppData) -> Void) {
        change(&data)
        persist()
    }

    private func persist() {
        guard !storageUnavailable else { return }
        do {
            try store.save(data)
            lastSeenModification = store.modificationDate()
        } catch {
            dataProblem = "Не удалось сохранить изменения: \(error.localizedDescription)"
        }
        WidgetCenter.shared.reloadAllTimelines()
        scheduleReminders()
    }

    private func scheduleReminders() {
        guard managesReminders else { return }
        rescheduleTask?.cancel()
        let snapshot = data
        let scheduler = scheduler
        rescheduleTask = Task {
            try? await Task.sleep(nanoseconds: 400_000_000)
            guard !Task.isCancelled else { return }
            await scheduler.reschedule(with: snapshot)
        }
    }
}

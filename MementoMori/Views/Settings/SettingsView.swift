import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var birthDate = Date()
    @State private var lifeExpectancy = Profile.defaultLifeExpectancy
    @State private var reminders = ReminderSettings()
    @State private var notificationsDenied = false
    @State private var isLoaded = false
    @State private var showImporter = false
    @State private var exportFile: ShareableFile?
    @State private var pendingImport: AppData?
    @State private var showResetConfirmation = false
    @State private var message: String?

    var body: some View {
        NavigationStack {
            Form {
                profileSection
                remindersSection
                widgetsSection
                dataSection
                aboutSection
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("Настройки")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
            .onAppear(perform: loadState)
            .onChange(of: birthDate) { _, _ in saveProfile() }
            .onChange(of: lifeExpectancy) { _, _ in saveProfile() }
            .onChange(of: reminders) { _, newValue in
                guard isLoaded else { return }
                model.updateReminders(newValue)
            }
            .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json]) { result in
                handleImport(result)
            }
            .sheet(item: $exportFile) { file in
                ActivityView(items: [file.url])
                    .presentationDetents([.medium, .large])
            }
            .confirmationDialog(
                "Заменить текущие данные?",
                isPresented: pendingImportBinding,
                titleVisibility: .visible
            ) {
                Button("Заменить", role: .destructive) {
                    if let pendingImport {
                        model.replaceAll(with: pendingImport)
                        loadState()
                        message = "Данные восстановлены."
                    }
                    pendingImport = nil
                }
                Button("Отмена", role: .cancel) { pendingImport = nil }
            } message: {
                Text("В файле \(Format.goals(pendingImport?.goals.count ?? 0)). Всё, что сейчас в приложении, будет заменено.")
            }
            .confirmationDialog("Удалить все данные?", isPresented: $showResetConfirmation, titleVisibility: .visible) {
                Button("Удалить всё", role: .destructive) {
                    model.resetAll()
                    dismiss()
                }
            } message: {
                Text("Цели, воспоминания, фото и настройки будут удалены без возможности восстановления.")
            }
            .alert(message ?? "", isPresented: messageBinding) {
                Button("OK", role: .cancel) {}
            }
        }
    }

    // MARK: Секции

    private var profileSection: some View {
        Section {
            DatePicker("Дата рождения", selection: $birthDate, in: birthDateRange, displayedComponents: .date)
            Stepper(value: $lifeExpectancy, in: Profile.lifeExpectancyRange) {
                HStack {
                    Text("Ожидаемая жизнь")
                    Spacer()
                    Text(Format.years(lifeExpectancy))
                        .monospacedDigit()
                        .foregroundStyle(Theme.secondaryText)
                }
            }
        } header: {
            Text("Ты")
        } footer: {
            Text("В среднем по миру люди живут около 73 лет. Это не прогноз, а точка отсчёта — выбери число, которое тебя мотивирует.")
        }
    }

    private var remindersSection: some View {
        Section {
            Toggle("Напоминания", isOn: remindersEnabledBinding)
            if reminders.isEnabled {
                Picker("Как часто", selection: $reminders.frequency) {
                    ForEach(ReminderSettings.Frequency.allCases) { frequency in
                        Text(frequency.title).tag(frequency)
                    }
                }
                if reminders.frequency == .weekly {
                    Picker("День недели", selection: $reminders.weekday) {
                        ForEach(Weekday.all) { day in
                            Text(day.name).tag(day.number)
                        }
                    }
                }
                DatePicker("Время", selection: reminderTimeBinding, displayedComponents: .hourAndMinute)
                Toggle("Напоминать о сроках целей", isOn: $reminders.deadlineAlerts)
                Button("Прислать пробное уведомление") {
                    Task {
                        await model.sendTestNotification()
                        message = "Сверни приложение — уведомление придёт через 5 секунд."
                    }
                }
            }
        } header: {
            Text("Напоминания")
        } footer: {
            Text(notificationsDenied
                 ? "Уведомления запрещены. Включи их: Настройки iPhone → Memento Mori → Уведомления."
                 : "Мысль дня, цель из твоего списка или просто «помни». О сроках — за месяц и в сам день.")
        }
    }

    private var widgetsSection: some View {
        Section {
            Text("Удерживай палец на свободном месте главного экрана → «Изменить» → «Добавить виджет» → Memento Mori. Для экрана блокировки: удерживай палец на заблокированном экране → «Настроить».")
                .font(.footnote)
                .foregroundStyle(Theme.secondaryText)
            if !AppGroup.isAvailable {
                Label("App Group не настроен — виджеты не увидят данные. Инструкция в README проекта.", systemImage: "exclamationmark.triangle")
                    .font(.footnote)
                    .foregroundStyle(Theme.accent)
            }
        } header: {
            Text("Виджеты")
        }
    }

    private var dataSection: some View {
        Section {
            Button {
                exportData()
            } label: {
                Label("Экспорт в файл", systemImage: "square.and.arrow.up")
            }
            Button {
                showImporter = true
            } label: {
                Label("Импорт из файла", systemImage: "square.and.arrow.down")
            }
            Button(role: .destructive) {
                showResetConfirmation = true
            } label: {
                Label("Удалить все данные", systemImage: "trash")
            }
        } header: {
            Text("Данные")
        } footer: {
            Text("Всё хранится только на этом iPhone — без аккаунтов и серверов. Экспорт сохраняет цели и настройки в JSON (без фото): это резервная копия и способ перенести данные.")
        }
    }

    private var aboutSection: some View {
        Section {
            LabeledContent("Версия", value: appVersion)
            Text("Memento mori — «помни о смерти». Древние стоики повторяли это не из страха, а чтобы не откладывать жизнь на потом.")
                .font(.footnote)
                .foregroundStyle(Theme.secondaryText)
        } header: {
            Text("О приложении")
        }
    }

    // MARK: Привязки

    private var remindersEnabledBinding: Binding<Bool> {
        Binding(
            get: { reminders.isEnabled },
            set: { isOn in
                guard isOn else {
                    reminders.isEnabled = false
                    return
                }
                Task {
                    let granted = await model.requestNotificationPermission()
                    await MainActor.run {
                        reminders.isEnabled = granted
                        notificationsDenied = !granted
                    }
                }
            }
        )
    }

    private var reminderTimeBinding: Binding<Date> {
        Binding(
            get: {
                Calendar.memento.date(bySettingHour: reminders.hour, minute: reminders.minute, second: 0, of: Date()) ?? Date()
            },
            set: { date in
                let parts = Calendar.memento.dateComponents([.hour, .minute], from: date)
                reminders.hour = parts.hour ?? 9
                reminders.minute = parts.minute ?? 0
            }
        )
    }

    private var pendingImportBinding: Binding<Bool> {
        Binding(
            get: { pendingImport != nil },
            set: { if !$0 { pendingImport = nil } }
        )
    }

    private var messageBinding: Binding<Bool> {
        Binding(
            get: { message != nil },
            set: { if !$0 { message = nil } }
        )
    }

    // MARK: Действия

    private func loadState() {
        isLoaded = false
        if let profile = model.profile {
            birthDate = profile.birthDate
            lifeExpectancy = profile.lifeExpectancy
        }
        reminders = model.data.reminders
        Task {
            let denied = await model.notificationsDenied()
            await MainActor.run {
                notificationsDenied = denied
                isLoaded = true
            }
        }
    }

    private func saveProfile() {
        guard isLoaded, let profile = model.profile else { return }
        let normalized = Calendar.memento.startOfDay(for: birthDate)
        guard normalized != profile.birthDate || lifeExpectancy != profile.lifeExpectancy else { return }
        model.updateProfile(birthDate: birthDate, lifeExpectancy: lifeExpectancy)
    }

    private func exportData() {
        do {
            exportFile = ShareableFile(url: try model.makeBackupFile())
        } catch {
            message = "Не удалось создать файл: \(error.localizedDescription)"
        }
    }

    private func handleImport(_ result: Result<URL, Error>) {
        switch result {
        case .success(let url):
            do {
                pendingImport = try model.readBackup(from: url)
            } catch {
                message = "Этот файл не похож на резервную копию Memento Mori."
            }
        case .failure(let error):
            message = "Не удалось открыть файл: \(error.localizedDescription)"
        }
    }

    private var birthDateRange: ClosedRange<Date> {
        let earliest = Calendar.memento.date(byAdding: .year, value: -110, to: Date()) ?? Date.distantPast
        return earliest...Date()
    }

    private var appVersion: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "\(version) (\(build))"
    }

}

private struct Weekday: Identifiable {
    let number: Int
    let name: String

    var id: Int { number }

    static let all: [Weekday] = [
        Weekday(number: 2, name: "Понедельник"),
        Weekday(number: 3, name: "Вторник"),
        Weekday(number: 4, name: "Среда"),
        Weekday(number: 5, name: "Четверг"),
        Weekday(number: 6, name: "Пятница"),
        Weekday(number: 7, name: "Суббота"),
        Weekday(number: 1, name: "Воскресенье"),
    ]
}

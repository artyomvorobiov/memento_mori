import SwiftUI

/// Счётчик «Последний раз»: когда было, как часто выходит на самом деле, история отметок.
struct CounterDetailView: View {
    let counterID: UUID
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var isEditing = false
    @State private var isPickingDate = false
    @State private var pickedDate = Date()
    @State private var showAllHistory = false
    @State private var justLogged = 0

    var body: some View {
        NavigationStack {
            Group {
                if let counter = model.counter(id: counterID) {
                    content(counter)
                } else {
                    Text("Счётчик удалён")
                        .foregroundStyle(Theme.secondaryText)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .background(Theme.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Готово") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button("Изменить") { isEditing = true }
                }
            }
            .sheet(isPresented: $isEditing) {
                CounterEditorView(counter: model.counter(id: counterID))
            }
            .sensoryFeedback(.success, trigger: justLogged)
            .onChange(of: model.counter(id: counterID) == nil) { _, isGone in
                if isGone { dismiss() }
            }
        }
    }

    private func content(_ counter: LifeCounter) -> some View {
        let now = Date()
        let stats = model.stats(at: now)
        return List {
            Section {
                header(counter, stats: stats, now: now)
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 8, leading: 4, bottom: 8, trailing: 4))

            Section {
                if counter.isLogged(on: now) {
                    Label("Сегодня уже отмечено", systemImage: "checkmark.circle.fill")
                        .foregroundStyle(Theme.accent)
                } else {
                    Button {
                        if model.logCounterToday(id: counterID) { justLogged += 1 }
                    } label: {
                        Label("Было сегодня", systemImage: "plus.circle.fill")
                            .font(.headline)
                            .foregroundStyle(Theme.accent)
                    }
                }
                if isPickingDate {
                    DatePicker("Когда", selection: $pickedDate, in: ...now, displayedComponents: .date)
                    Button("Добавить отметку") {
                        model.logCounter(id: counterID, at: noonOf(pickedDate))
                        justLogged += 1
                        withAnimation { isPickingDate = false }
                    }
                } else {
                    Button("Было в другой день…") {
                        pickedDate = now
                        withAnimation { isPickingDate = true }
                    }
                }
            } footer: {
                if counter.log.isEmpty {
                    Text("Отмечай каждый раз, когда это случается. Приложение покажет, сколько прошло с последнего раза, и напомнит, если перерыв затянется.")
                }
            }

            if let pace = paceLines(counter, stats: stats, now: now), !pace.isEmpty {
                Section("Как выходит на самом деле") {
                    ForEach(pace, id: \.self) { line in
                        Text(line)
                            .font(.subheadline)
                    }
                }
            }

            if !counter.log.isEmpty {
                Section {
                    let history = Array(counter.log.reversed())
                    ForEach(showAllHistory ? history : Array(history.prefix(8)), id: \.self) { date in
                        HStack {
                            Text(Format.date(date))
                            Spacer()
                            Text(Format.daysAgo(Calendar.memento.dateComponents([.day], from: Calendar.memento.startOfDay(for: date), to: Calendar.memento.startOfDay(for: now)).day ?? 0))
                                .font(.caption)
                                .foregroundStyle(Theme.tertiaryText)
                        }
                        .swipeActions {
                            Button("Удалить", role: .destructive) {
                                model.removeCounterLog(id: counterID, date: date)
                            }
                        }
                    }
                    if history.count > 8 && !showAllHistory {
                        Button("Показать все — \(history.count)") {
                            withAnimation { showAllHistory = true }
                        }
                    }
                } header: {
                    Text("Когда было")
                } footer: {
                    Text("Смахни отметку влево, чтобы удалить.")
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }

    private func header(_ counter: LifeCounter, stats: LifeStats?, now: Date) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(counter.emoji)
                .font(.system(size: 44))
            VStack(alignment: .leading, spacing: 2) {
                Text("≈ \(Format.number(counter.remaining(remainingYears: stats?.remainingYears ?? 0)))")
                    .font(.system(size: 44, weight: .thin))
                    .monospacedDigit()
                Text(counter.title)
                    .font(.headline)
                    .foregroundStyle(Theme.secondaryText)
            }
            if let days = counter.daysSinceLast(now: now), let last = counter.lastDate {
                Text("Последний раз — \(Format.daysAgo(days)), \(Format.date(last))")
                    .font(.subheadline)
                    .foregroundStyle(counter.isOverdue(now: now) ? Theme.accent : Color.white)
            }
            if let usual = counter.usualIntervalDays {
                Text("Обычно — \(Format.everyInterval(days: usual))" + (counter.isOverdue(now: now) ? ". Перерыв уже дольше обычного." : "."))
                    .font(.footnote)
                    .foregroundStyle(Theme.secondaryText)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// «В 2026 году — 3 из 5», «По факту — 4 раза в год: впереди ≈ 80».
    private func paceLines(_ counter: LifeCounter, stats: LifeStats?, now: Date) -> [String]? {
        guard !counter.log.isEmpty else { return nil }
        var lines: [String] = []
        let year = Calendar.memento.component(.year, from: now)
        let plannedThisYear = max(1, Int(counter.perYear.rounded()))
        lines.append("В \(String(year)) году — \(counter.count(inYearOf: now)) из \(plannedThisYear) запланированных.")
        if let actual = counter.actualPerYear(now: now) {
            let remainingYears = stats?.remainingYears ?? 0
            let atPace = counter.remaining(remainingYears: remainingYears, perYear: actual)
            let planned = counter.remaining(remainingYears: remainingYears)
            if actual < 0.05 {
                lines.append("За последний год — ни разу. В таком темпе этого больше не случится.")
            } else if abs(atPace - planned) >= max(1, planned / 10) {
                lines.append("По факту — \(Format.perYear(actual)) в год. В таком темпе впереди ≈ \(Format.number(atPace)), а не \(Format.number(planned)).")
            } else {
                lines.append("По факту — \(Format.perYear(actual)) в год: всё идёт по плану.")
            }
        }
        return lines
    }

    /// Отметки задним числом ставим на полдень, чтобы не съезжали на соседний день при смене часового пояса.
    private func noonOf(_ date: Date) -> Date {
        Calendar.memento.date(bySettingHour: 12, minute: 0, second: 0, of: date) ?? date
    }
}

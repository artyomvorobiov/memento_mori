import SwiftUI

/// Свой счётчик «Осталось»: что-то, что случается N раз в год.
struct CounterEditorView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var draft: LifeCounter
    @State private var untilEndOfLife: Bool
    @State private var years: Int
    private let isNew: Bool

    init(counter: LifeCounter?) {
        let initial = counter ?? LifeCounter(title: "", emoji: "❤️", perYear: 4)
        _draft = State(initialValue: initial)
        _untilEndOfLife = State(initialValue: initial.years == nil)
        _years = State(initialValue: initial.years ?? 20)
        isNew = counter == nil
    }

    var body: some View {
        NavigationStack {
            Form {
                if isNew {
                    Section("Идеи") {
                        ForEach(CounterPresets.all) { preset in
                            Button {
                                apply(preset)
                            } label: {
                                HStack(spacing: 10) {
                                    Text(preset.emoji)
                                    Text(preset.title)
                                        .foregroundStyle(Color.white)
                                    Spacer()
                                    Text(preset.hint)
                                        .font(.caption)
                                        .foregroundStyle(Theme.secondaryText)
                                }
                            }
                        }
                    }
                }

                Section {
                    HStack(spacing: 12) {
                        TextField("✦", text: $draft.emoji)
                            .frame(width: 44)
                            .multilineTextAlignment(.center)
                        TextField("например, встреч с родителями", text: $draft.title)
                    }
                } header: {
                    Text("Что считаем")
                } footer: {
                    Text("Название читается после числа: «≈ 100 встреч с родителями».")
                }

                Section {
                    HStack {
                        Text("Раз в год")
                        Spacer()
                        TextField("4", value: $draft.perYear, format: .number)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 80)
                    }
                    Toggle("До конца жизни", isOn: $untilEndOfLife)
                    if !untilEndOfLife {
                        Stepper(value: $years, in: 1...100) {
                            HStack {
                                Text("Ещё")
                                Spacer()
                                Text(Format.years(years))
                                    .foregroundStyle(Theme.secondaryText)
                            }
                        }
                    }
                } header: {
                    Text("Как часто")
                } footer: {
                    Text("Если видишь родителей 5 раз в год и надеешься, что так будет ещё лет 20, — впереди около 100 встреч. Не так уж много.")
                }

                if draft.supportsOverdueReminders {
                    Section {
                        Toggle("Напоминать, если давно не было", isOn: $draft.remindsWhenOverdue)
                    } footer: {
                        Text("Отмечай «Было» на главном экране. Если перерыв станет заметно дольше обычного (\(Format.everyInterval(days: draft.usualIntervalDays ?? 1))), придёт мягкое напоминание.")
                    }
                }

                Section {
                    HStack {
                        Text("Осталось примерно")
                        Spacer()
                        Text(Format.number(remainingPreview))
                            .font(.title3.weight(.semibold))
                            .monospacedDigit()
                            .foregroundStyle(Theme.accent)
                    }
                }

                if !isNew {
                    Section {
                        Button("Удалить счётчик", role: .destructive) {
                            model.deleteCounter(id: draft.id)
                            dismiss()
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle(isNew ? "Новый счётчик" : "Счётчик")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Сохранить", action: save)
                        .disabled(!canSave)
                }
            }
        }
    }

    private var resultCounter: LifeCounter {
        var counter = draft
        counter.title = counter.title.trimmingCharacters(in: .whitespacesAndNewlines)
        counter.emoji = counter.emoji.trimmingCharacters(in: .whitespacesAndNewlines)
        if counter.emoji.isEmpty { counter.emoji = "✦" }
        counter.years = untilEndOfLife ? nil : years
        return counter
    }

    private var canSave: Bool {
        !resultCounter.title.isEmpty && draft.perYear > 0
    }

    private var remainingPreview: Int {
        resultCounter.remaining(remainingYears: model.stats()?.remainingYears ?? 0)
    }

    private func apply(_ preset: CounterPreset) {
        draft.title = preset.title
        draft.emoji = preset.emoji
        draft.perYear = preset.perYear
        untilEndOfLife = preset.years == nil
        years = preset.years ?? years
    }

    private func save() {
        model.saveCounter(resultCounter)
        dismiss()
    }
}

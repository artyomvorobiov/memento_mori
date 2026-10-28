import SwiftUI

struct LetterSelection: Identifiable, Hashable {
    let id: UUID
}

// MARK: - Новое письмо

/// Письмо себе в будущее: текст и день, когда его можно будет открыть.
struct LetterEditorView: View {
    enum When: Hashable {
        case inYears(Int)
        case atAge
        case onDate
    }

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var when: When = .inYears(1)
    @State private var age = 40
    @State private var date = Calendar.memento.date(byAdding: .year, value: 1, to: Date()) ?? Date()
    @State private var didSetup = false
    @FocusState private var isTextFocused: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            chip("Через год", value: .inYears(1))
                            chip("Через 3 года", value: .inYears(3))
                            chip("Через 5 лет", value: .inYears(5))
                            chip(Format.byAge(age).capitalizedFirst, value: .atAge)
                            chip("Своя дата", value: .onDate)
                        }
                        .padding(.vertical, 2)
                    }
                    .listRowInsets(EdgeInsets(top: 10, leading: 16, bottom: 10, trailing: 16))
                    switch when {
                    case .atAge:
                        Stepper(value: $age, in: ageRange) {
                            HStack {
                                Text("Возраст")
                                Spacer()
                                Text(Format.byAge(age))
                                    .foregroundStyle(Theme.secondaryText)
                            }
                        }
                    case .onDate:
                        DatePicker("Дата", selection: $date, in: dateRange, displayedComponents: .date)
                    case .inYears:
                        EmptyView()
                    }
                } header: {
                    Text("Когда открыть")
                } footer: {
                    Text(insight)
                }

                Section {
                    ZStack(alignment: .topLeading) {
                        if text.isEmpty {
                            Text("Привет из прошлого! Сейчас…")
                                .foregroundStyle(Theme.tertiaryText)
                                .padding(.top, 8)
                                .padding(.leading, 5)
                                .allowsHitTesting(false)
                        }
                        TextEditor(text: $text)
                            .focused($isTextFocused)
                            .frame(minHeight: 240)
                            .scrollContentBackground(.hidden)
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(LetterPrompts.all, id: \.self) { prompt in
                                Button {
                                    insert(prompt)
                                } label: {
                                    Text(prompt)
                                        .font(.caption)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 7)
                                        .background(Theme.future, in: Capsule())
                                        .foregroundStyle(Color.white)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .listRowInsets(EdgeInsets(top: 10, leading: 16, bottom: 10, trailing: 16))
                } header: {
                    Text("Письмо")
                } footer: {
                    Text("Нажми на вопрос, чтобы начать с него. После сохранения письмо запечатается: до этого дня его не прочитать и не изменить. В день открытия придёт уведомление.")
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("Письмо себе")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Запечатать", action: save)
                        .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .onAppear(perform: setup)
        }
    }

    private func chip(_ title: String, value: When) -> some View {
        let isSelected = when == value
        return Button {
            withAnimation(.snappy) { when = value }
        } label: {
            Text(title)
                .font(.subheadline)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(isSelected ? Theme.accent : Theme.future, in: Capsule())
                .foregroundStyle(isSelected ? Color.black : Color.white)
        }
        .buttonStyle(.plain)
    }

    private var birthDate: Date { model.profile?.birthDate ?? Date() }

    private var currentAge: Int { model.stats()?.ageYears ?? 30 }

    private var ageRange: ClosedRange<Int> {
        (currentAge + 1)...max(currentAge + 1, 120)
    }

    private var dateRange: ClosedRange<Date> {
        let calendar = Calendar.memento
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: Date())) ?? Date()
        let latest = calendar.date(byAdding: .year, value: 100, to: Date()) ?? tomorrow
        return tomorrow...latest
    }

    private var draft: FutureLetter {
        switch when {
        case .inYears(let years):
            let date = Calendar.memento.date(byAdding: .year, value: years, to: Date()) ?? Date()
            return FutureLetter(text: text, openDate: date)
        case .atAge:
            return FutureLetter(text: text, openAge: age)
        case .onDate:
            return FutureLetter(text: text, openDate: date)
        }
    }

    /// «Откроется 28 октября 2027 — через год. Тебе будет 35.»
    private var insight: String {
        let unlock = draft.unlockDate(birthDate: birthDate)
        var line = "Откроется \(Format.date(unlock)) — \(Format.relative(from: Date(), to: unlock))."
        if let ageThen = Calendar.memento.dateComponents([.year], from: birthDate, to: unlock).year {
            line += " Тебе будет \(Format.years(ageThen))."
        }
        if let stats = model.stats(), stats.remainingSeconds > 0 {
            let share = unlock.timeIntervalSinceNow / stats.remainingSeconds
            if share > 0.005 && share < 1 {
                line += " Это \(Format.percent(share, digits: 0)) оставшейся жизни."
            } else if share >= 1 {
                line += " Это дальше, чем ожидаемая продолжительность жизни, — но вдруг."
            }
        }
        return line
    }

    private func setup() {
        guard !didSetup else { return }
        didSetup = true
        age = max(currentAge + 1, (currentAge / 10 + 1) * 10)
    }

    private func insert(_ prompt: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        text = trimmed.isEmpty ? prompt + "\n" : trimmed + "\n\n" + prompt + "\n"
        isTextFocused = true
    }

    private func save() {
        model.saveLetter(draft)
        dismiss()
    }
}

// MARK: - Письмо

/// Запечатанное, ждущее или прочитанное письмо.
struct LetterView: View {
    let letterID: UUID
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var confirmEarlyOpen = false
    @State private var confirmDelete = false
    @State private var isReplying = false
    @State private var openedCount = 0

    var body: some View {
        NavigationStack {
            Group {
                if let letter = model.letter(id: letterID), let birthDate = model.profile?.birthDate {
                    content(letter, birthDate: birthDate)
                } else {
                    Text("Письмо удалено")
                        .foregroundStyle(Theme.secondaryText)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .background(Theme.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        if model.letter(id: letterID)?.isOpened == true {
                            Button {
                                isReplying = true
                            } label: {
                                Label("Написать ответ в будущее", systemImage: "square.and.pencil")
                            }
                        }
                        Button(role: .destructive) {
                            confirmDelete = true
                        } label: {
                            Label("Удалить письмо", systemImage: "trash")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                    .accessibilityLabel("Ещё")
                }
            }
            .confirmationDialog("Удалить письмо?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Удалить", role: .destructive) {
                    model.deleteLetter(id: letterID)
                    dismiss()
                }
            } message: {
                Text("Его нельзя будет восстановить.")
            }
            .sheet(isPresented: $isReplying) {
                LetterEditorView()
            }
            .sensoryFeedback(.success, trigger: openedCount)
        }
    }

    @ViewBuilder
    private func content(_ letter: FutureLetter, birthDate: Date) -> some View {
        let now = Date()
        if letter.isOpened {
            reading(letter, birthDate: birthDate)
        } else if letter.isUnlocked(now: now, birthDate: birthDate) {
            envelope(
                icon: "envelope.open.fill",
                title: "Письмо из прошлого",
                subtitle: "Написано \(Format.date(letter.createdAt)), тебе было \(Format.years(letter.ageWhenWritten(birthDate: birthDate))).",
                detail: nil
            ) {
                Button(action: open) {
                    AccentButtonLabel(title: "Открыть письмо", systemImage: "envelope.open")
                }
                .buttonStyle(.plain)
            }
        } else {
            let unlock = letter.unlockDate(birthDate: birthDate)
            envelope(
                icon: "envelope.fill",
                title: "Запечатано до \(Format.date(unlock))",
                subtitle: "Написано \(Format.date(letter.createdAt)), откроется \(Format.relative(from: now, to: unlock)).",
                detail: "До этого дня его не прочитать — в этом и смысл."
            ) {
                Button("Открыть досрочно") {
                    confirmEarlyOpen = true
                }
                .foregroundStyle(Theme.secondaryText)
                .confirmationDialog("Открыть раньше срока?", isPresented: $confirmEarlyOpen, titleVisibility: .visible) {
                    Button("Открыть сейчас", action: open)
                    Button("Подождать", role: .cancel) {}
                } message: {
                    Text("Письмо задумано на \(Format.date(unlock)) — иногда ждать тоже часть удовольствия.")
                }
            }
        }
    }

    private func envelope<Actions: View>(
        icon: String,
        title: String,
        subtitle: String,
        detail: String?,
        @ViewBuilder actions: () -> Actions
    ) -> some View {
        VStack(spacing: 18) {
            Spacer()
            Image(systemName: icon)
                .font(.system(size: 64, weight: .light))
                .foregroundStyle(Theme.accent)
                .symbolEffect(.bounce, value: openedCount)
            VStack(spacing: 8) {
                Text(title)
                    .font(.system(.title2, design: .serif).weight(.medium))
                    .multilineTextAlignment(.center)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(Theme.secondaryText)
                    .multilineTextAlignment(.center)
                if let detail {
                    Text(detail)
                        .font(.footnote)
                        .foregroundStyle(Theme.tertiaryText)
                        .multilineTextAlignment(.center)
                }
            }
            Spacer()
            actions()
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func reading(_ letter: FutureLetter, birthDate: Date) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("ПИСЬМО ИЗ ПРОШЛОГО")
                        .font(.caption.weight(.semibold))
                        .tracking(2)
                        .foregroundStyle(Theme.tertiaryText)
                    Text("\(Format.date(letter.createdAt)) · тебе было \(Format.years(letter.ageWhenWritten(birthDate: birthDate)))")
                        .font(.subheadline)
                        .foregroundStyle(Theme.secondaryText)
                }
                Text(letter.text)
                    .font(.system(.title3, design: .serif))
                    .lineSpacing(6)
                    .foregroundStyle(Color.white)
                    .textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)
                if let openedAt = letter.openedAt {
                    Text("Прочитано \(Format.date(openedAt))")
                        .font(.caption)
                        .foregroundStyle(Theme.tertiaryText)
                }
                Button {
                    isReplying = true
                } label: {
                    Label("Написать ответ в будущее", systemImage: "square.and.pencil")
                        .font(.subheadline)
                }
                .padding(.top, 8)
            }
            .padding(24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func open() {
        withAnimation(.easeInOut(duration: 0.4)) {
            model.openLetter(id: letterID)
        }
        openedCount += 1
    }
}

// MARK: - Список писем (вкладка «Жизнь»)

struct LettersSection: View {
    @Environment(AppModel.self) private var model
    @State private var openedLetter: LetterSelection?
    @State private var isWriting = false

    var body: some View {
        let now = Date()
        let waiting = model.waitingLetters(at: now)
        let sealed = model.sealedLetters(at: now)
        let opened = model.openedLetters
        let total = waiting.count + sealed.count + opened.count
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Письма в будущее", trailing: total > 0 ? "\(total)" : nil)
            if total == 0 {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Напиши письмо себе — через год, к 40 годам или к любой дате. До этого дня его не прочитать, а в нужный день придёт уведомление. Письмо появится меткой на сетке недель.")
                        .font(.subheadline)
                        .foregroundStyle(Theme.secondaryText)
                    Button {
                        isWriting = true
                    } label: {
                        AccentButtonLabel(title: "Написать письмо", systemImage: "envelope")
                    }
                    .buttonStyle(.plain)
                }
                .mementoCard()
            } else {
                VStack(spacing: 0) {
                    let rows = waiting + sealed + opened
                    ForEach(Array(rows.enumerated()), id: \.element.id) { index, letter in
                        if index > 0 {
                            Divider().overlay(Theme.cardStroke)
                        }
                        Button {
                            openedLetter = LetterSelection(id: letter.id)
                        } label: {
                            LetterRow(letter: letter, now: now, birthDate: model.profile?.birthDate ?? now)
                        }
                        .buttonStyle(.plain)
                    }
                    Divider().overlay(Theme.cardStroke)
                    Button {
                        isWriting = true
                    } label: {
                        Label("Написать ещё одно", systemImage: "square.and.pencil")
                            .font(.subheadline)
                            .foregroundStyle(Theme.accent)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 14)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }
        }
        .sheet(item: $openedLetter) { selection in
            LetterView(letterID: selection.id)
        }
        .sheet(isPresented: $isWriting) {
            LetterEditorView()
        }
    }
}

struct LetterRow: View {
    let letter: FutureLetter
    let now: Date
    let birthDate: Date

    var body: some View {
        let unlock = letter.unlockDate(birthDate: birthDate)
        let isWaiting = letter.isWaiting(now: now, birthDate: birthDate)
        HStack(spacing: 12) {
            Image(systemName: letter.isOpened ? "envelope.open" : isWaiting ? "envelope.open.fill" : "envelope.fill")
                .font(.system(size: 17))
                .foregroundStyle(letter.isOpened ? Theme.secondaryText : Theme.accent)
                .frame(width: 26)
            VStack(alignment: .leading, spacing: 2) {
                Text(title(isWaiting: isWaiting))
                    .foregroundStyle(isWaiting ? Theme.accent : Color.white)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                Text(subtitle(unlock: unlock))
                    .font(.caption)
                    .foregroundStyle(Theme.secondaryText)
                    .lineLimit(2)
            }
            Spacer(minLength: 8)
            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(Theme.tertiaryText)
        }
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    private func title(isWaiting: Bool) -> String {
        if letter.isOpened { return letter.preview.isEmpty ? "Письмо" : "«\(letter.preview)»" }
        if isWaiting { return "Можно открыть" }
        return "Откроется \(letter.openLabel(birthDate: birthDate))"
    }

    private func subtitle(unlock: Date) -> String {
        if let openedAt = letter.openedAt {
            return "написано \(Format.shortDate(letter.createdAt)) · прочитано \(Format.shortDate(openedAt))"
        }
        if letter.isUnlocked(now: now, birthDate: birthDate) {
            return "написано \(Format.shortDate(letter.createdAt))"
        }
        return "написано \(Format.shortDate(letter.createdAt)) · \(Format.relative(from: now, to: unlock))"
    }
}

// MARK: - «Пришло письмо» на главном экране

struct LetterArrivedCard: View {
    let letter: FutureLetter
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 14) {
                Image(systemName: "envelope.open.fill")
                    .font(.system(size: 26))
                    .foregroundStyle(Theme.accent)
                    .symbolEffect(.pulse)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Пришло письмо из прошлого")
                        .font(.system(.headline, design: .serif))
                        .foregroundStyle(Color.white)
                    Text("Написано \(Format.date(letter.createdAt)) — открой, когда будет минутка.")
                        .font(.caption)
                        .foregroundStyle(Theme.secondaryText)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Theme.tertiaryText)
            }
            .mementoCard()
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(Theme.accent.opacity(0.5), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

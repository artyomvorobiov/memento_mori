import SwiftUI

struct TodayView: View {
    @Environment(AppModel.self) private var model
    @State private var showSettings = false
    @State private var showShareCard = false
    @State private var completingGoal: Goal?
    @State private var openedLetter: LetterSelection?

    var body: some View {
        NavigationStack {
            ScrollView {
                TimelineView(.everyMinute) { context in
                    if let stats = model.stats(at: context.date) {
                        VStack(alignment: .leading, spacing: 32) {
                            VStack(alignment: .leading, spacing: 16) {
                                HeroCountdown(stats: stats)
                                LifeProgressSummary(stats: stats)
                            }
                            if let letter = model.waitingLetters(at: context.date).first {
                                LetterArrivedCard(letter: letter) {
                                    openedLetter = LetterSelection(id: letter.id)
                                }
                            }
                            QuoteCard(date: context.date)
                            FocusSection(completingGoal: $completingGoal)
                            RemainingSection(stats: stats)
                            PaceSection(stats: stats)
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 8)
                        .padding(.bottom, 32)
                    }
                }
            }
            .defaultScrollAnchor(DebugLaunch.scrollToBottom ? .bottom : .top)
            .background(Theme.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text("MEMENTO MORI")
                        .font(.caption.weight(.semibold))
                        .tracking(3)
                        .foregroundStyle(Theme.secondaryText)
                }
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showShareCard = true
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                    }
                    .accessibilityLabel("Поделиться")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showSettings = true
                    } label: {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Настройки")
                }
            }
            .navigationDestination(for: UUID.self) { id in
                GoalDetailView(goalID: id)
            }
            .sheet(isPresented: $showSettings) {
                SettingsView()
            }
            .sheet(isPresented: $showShareCard) {
                ShareCardSheet()
            }
            .sheet(item: $completingGoal) { goal in
                CompleteGoalSheet(goal: goal)
            }
            .sheet(item: $openedLetter) { selection in
                LetterView(letterID: selection.id)
            }
        }
    }
}

// MARK: - Главный счётчик

enum HeroUnit: String, CaseIterable {
    case weeks, days, hours, seconds, years, percent

    var next: HeroUnit {
        let all = HeroUnit.allCases
        let index = all.firstIndex(of: self) ?? 0
        return all[(index + 1) % all.count]
    }
}

/// Сколько осталось. Нажатие переключает единицы: недели → дни → часы → секунды → годы → проценты.
struct HeroCountdown: View {
    let stats: LifeStats
    @AppStorage("heroUnit") private var unitRaw = HeroUnit.weeks.rawValue

    private var unit: HeroUnit { HeroUnit(rawValue: unitRaw) ?? .weeks }

    var body: some View {
        Group {
            if unit == .seconds && !stats.isBeyondExpectancy {
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    let seconds = Int(stats.at(context.date).remainingSeconds)
                    counter(
                        Format.number(seconds),
                        caption: RussianPlural.form(seconds, "секунда осталась", "секунды осталось", "секунд осталось")
                    )
                }
            } else {
                let value = display(for: unit)
                counter(value.number, caption: value.caption)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
        .onTapGesture {
            withAnimation(.snappy) {
                unitRaw = unit.next.rawValue
            }
        }
        .sensoryFeedback(.selection, trigger: unitRaw)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityHint("Нажми, чтобы сменить единицы")
    }

    private func counter(_ number: String, caption: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(number)
                .font(.system(size: 64, weight: .thin))
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.4)
                .contentTransition(.numericText())
            Text(caption)
                .font(.subheadline)
                .foregroundStyle(Theme.secondaryText)
        }
    }

    private func display(for unit: HeroUnit) -> (number: String, caption: String) {
        if stats.isBeyondExpectancy {
            return ("∞", "каждый день сверх плана — подарок")
        }
        switch unit {
        case .weeks:
            let value = stats.remainingWeeks
            return (Format.number(value), RussianPlural.form(value, "неделя осталась", "недели осталось", "недель осталось"))
        case .days:
            let value = stats.remainingDays
            return (Format.number(value), RussianPlural.form(value, "день остался", "дня осталось", "дней осталось"))
        case .hours, .seconds:
            let value = stats.remainingHours
            return (Format.number(value), RussianPlural.form(value, "час остался", "часа осталось", "часов осталось"))
        case .years:
            return (Format.decimal(stats.remainingYears), "года осталось")
        case .percent:
            return (Format.percent(stats.progress), "жизни прожито")
        }
    }
}

struct LifeProgressSummary: View {
    let stats: LifeStats

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ThinProgressBar(progress: stats.progress)
            HStack {
                Text("\(Format.percent(stats.progress)) прожито")
                Spacer()
                Text("\(stats.ageYears) из \(Format.genitive(stats.lifeExpectancy, "года", "лет"))")
            }
            .font(.caption)
            .monospacedDigit()
            .foregroundStyle(Theme.secondaryText)
        }
    }
}

// MARK: - Мысль дня

struct QuoteCard: View {
    let date: Date
    @State private var offset = 0

    var body: some View {
        let quote = Quotes.ofTheDay(date, offset: offset)
        VStack(alignment: .leading, spacing: 12) {
            Text("«\(quote.text)»")
                .font(.system(.title3, design: .serif))
                .italic()
                .foregroundStyle(Color.white)
                .fixedSize(horizontal: false, vertical: true)
            Text("— \(quote.author)")
                .font(.footnote)
                .foregroundStyle(Theme.secondaryText)
        }
        .mementoCard(padding: 20)
        .contentShape(Rectangle())
        .onTapGesture {
            withAnimation(.easeInOut) { offset += 1 }
        }
        .accessibilityHint("Нажми, чтобы прочитать другую мысль")
    }
}

// MARK: - Фокус

struct FocusSection: View {
    @Environment(AppModel.self) private var model
    @Binding var completingGoal: Goal?

    var body: some View {
        let goals = model.focusedGoals
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "В фокусе", trailing: goals.isEmpty ? nil : "\(goals.count) из \(AppData.maxFocusedGoals)")
            if goals.isEmpty {
                Text(model.data.activeGoals.isEmpty
                     ? "Добавь цели во вкладке «Цели» и отметь звёздочкой до трёх главных."
                     : "Отметь звёздочкой до трёх главных целей — они будут здесь и на виджете.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.secondaryText)
                    .mementoCard()
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(goals.enumerated()), id: \.element.id) { index, goal in
                        if index > 0 {
                            Divider().overlay(Theme.cardStroke)
                        }
                        focusRow(goal)
                    }
                }
                .padding(.horizontal, 16)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }
        }
    }

    private func focusRow(_ goal: Goal) -> some View {
        HStack(spacing: 12) {
            Button {
                completingGoal = goal
            } label: {
                Image(systemName: "circle")
                    .font(.title3)
                    .foregroundStyle(Theme.accent)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Отметить выполненной")

            NavigationLink(value: goal.id) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(goal.title)
                        .foregroundStyle(Color.white)
                        .multilineTextAlignment(.leading)
                    if let step = goal.nextStep {
                        Text("Дальше: \(step.title)")
                            .font(.caption)
                            .foregroundStyle(Theme.accent)
                            .lineLimit(1)
                    }
                    if let summary = DeadlineText.summary(for: goal, birthDate: model.profile?.birthDate) {
                        Text(summary)
                            .font(.caption)
                            .foregroundStyle(Theme.secondaryText)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 12)
    }
}

// MARK: - Осталось примерно

struct RemainingSection: View {
    let stats: LifeStats
    @Environment(AppModel.self) private var model
    @State private var openedCounter: CounterSelection?
    @State private var isAddingCounter = false
    @State private var loggedCount = 0

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Осталось примерно")
            LazyVGrid(columns: columns, spacing: 12) {
                CounterTile(
                    emoji: "🌅",
                    value: Format.number(stats.remainingDays),
                    caption: RussianPlural.form(stats.remainingDays, "рассвет", "рассвета", "рассветов")
                )
                CounterTile(
                    emoji: "🌕",
                    value: Format.number(stats.fullMoonsLeft),
                    caption: RussianPlural.form(stats.fullMoonsLeft, "полнолуние", "полнолуния", "полнолуний")
                )
                CounterTile(
                    emoji: "🎄",
                    value: Format.number(stats.newYearsLeft),
                    caption: RussianPlural.form(stats.newYearsLeft, "Новый год", "Новых года", "Новых годов")
                )
                CounterTile(
                    emoji: "🫀",
                    value: Format.compact(stats.heartbeatsLeft),
                    caption: "ударов сердца"
                )
                ForEach(model.data.counters) { counter in
                    LivingCounterTile(
                        counter: counter,
                        remaining: counter.remaining(remainingYears: stats.remainingYears),
                        now: stats.now,
                        onOpen: { openedCounter = CounterSelection(id: counter.id) },
                        onLog: {
                            if model.logCounterToday(id: counter.id) { loggedCount += 1 }
                        }
                    )
                }
                Button {
                    isAddingCounter = true
                } label: {
                    VStack(spacing: 8) {
                        Image(systemName: "plus")
                            .font(.title3)
                        Text("Свой счётчик")
                            .font(.caption)
                    }
                    .foregroundStyle(Theme.secondaryText)
                    .frame(maxWidth: .infinity, minHeight: 112)
                    .background(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .strokeBorder(Theme.future, style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
                    )
                }
                .buttonStyle(.plain)
            }
        }
        .sheet(item: $openedCounter) { selection in
            CounterDetailView(counterID: selection.id)
        }
        .sheet(isPresented: $isAddingCounter) {
            CounterEditorView(counter: nil)
        }
        .sensoryFeedback(.success, trigger: loggedCount)
        .onAppear {
            if DebugLaunch.openCounterDetail, let first = model.data.counters.first {
                openedCounter = CounterSelection(id: first.id)
            }
        }
    }
}

struct CounterSelection: Identifiable, Hashable {
    let id: UUID
}

/// Свой счётчик: сколько раз осталось, когда было в последний раз и кнопка «Было».
struct LivingCounterTile: View {
    let counter: LifeCounter
    let remaining: Int
    let now: Date
    let onOpen: () -> Void
    let onLog: () -> Void

    var body: some View {
        let loggedToday = counter.isLogged(on: now)
        VStack(alignment: .leading, spacing: 6) {
            Text(counter.emoji)
                .font(.title3)
            Text(Format.number(remaining))
                .font(.system(size: 28, weight: .light))
                .monospacedDigit()
                .foregroundStyle(Color.white)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(counter.title)
                .font(.caption)
                .foregroundStyle(Theme.secondaryText)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
            Text(lastTimeText)
                .font(.caption2)
                .foregroundStyle(counter.isOverdue(now: now) ? Theme.accent : Theme.tertiaryText)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, minHeight: 84, alignment: .topLeading)
        .padding(14)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .onTapGesture(perform: onOpen)
        .overlay(alignment: .topTrailing) {
            Button(action: onLog) {
                Image(systemName: loggedToday ? "checkmark" : "plus")
                    .font(.system(size: 13, weight: .bold))
                    .frame(width: 30, height: 30)
                    .background(loggedToday ? Theme.future : Theme.accent, in: Circle())
                    .foregroundStyle(loggedToday ? Color.white.opacity(0.7) : Color.black)
            }
            .buttonStyle(.plain)
            .disabled(loggedToday)
            .padding(10)
            .accessibilityLabel(loggedToday ? "Сегодня уже отмечено" : "Было сегодня")
        }
        .contextMenu {
            Button(action: onLog) {
                Label("Было сегодня", systemImage: "plus")
            }
            .disabled(loggedToday)
            Button(action: onOpen) {
                Label("Подробнее", systemImage: "clock.arrow.circlepath")
            }
        }
        .accessibilityElement(children: .contain)
    }

    private var lastTimeText: String {
        guard let days = counter.daysSinceLast(now: now) else { return "нажми +, когда случится" }
        return "последний раз — " + Format.daysAgo(days)
    }
}

struct CounterTile: View {
    let emoji: String
    let value: String
    let caption: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(emoji)
                .font(.title3)
            Text(value)
                .font(.system(size: 28, weight: .light))
                .monospacedDigit()
                .foregroundStyle(Color.white)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(caption)
                .font(.caption)
                .foregroundStyle(Theme.secondaryText)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
        }
        .frame(maxWidth: .infinity, minHeight: 84, alignment: .topLeading)
        .padding(14)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

// MARK: - Темп

struct PaceSection: View {
    let stats: LifeStats
    @Environment(AppModel.self) private var model

    var body: some View {
        let insight = PaceInsight(goals: model.data.goals, stats: stats, trackingSince: model.trackingSince)
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(
                title: "Темп",
                trailing: "выполнено \(model.data.completedGoals.count) из \(model.data.goals.count)"
            )
            VStack(alignment: .leading, spacing: 12) {
                ForEach(insight.lines, id: \.self) { line in
                    Text(line)
                        .font(.subheadline)
                        .foregroundStyle(Color.white.opacity(0.85))
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .mementoCard()
        }
    }
}

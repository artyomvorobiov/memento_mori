import SwiftUI

enum LifeCalendarMode: String, CaseIterable, Identifiable {
    case weeks
    case years

    var id: String { rawValue }

    var title: String {
        switch self {
        case .weeks: "Недели"
        case .years: "Годы"
        }
    }
}

/// Отметки в сетке: выполненные цели — в неделе выполнения, сроки — в неделе дедлайна,
/// письма в будущее — в неделе, когда их можно открыть.
struct WeekMarkers {
    var completed: Set<Int> = []
    var deadlines: Set<Int> = []
    var letters: Set<Int> = []
}

struct LifeCalendarView: View {
    @Environment(AppModel.self) private var model
    @AppStorage("lifeCalendarMode") private var modeRaw = LifeCalendarMode.weeks.rawValue
    @State private var selectedIndex: Int?
    @State private var openedLetter: LetterSelection?

    private var mode: LifeCalendarMode { LifeCalendarMode(rawValue: modeRaw) ?? .weeks }

    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                if let stats = model.stats() {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 20) {
                            Picker("Вид", selection: $modeRaw) {
                                ForEach(LifeCalendarMode.allCases) { item in
                                    Text(item.title).tag(item.rawValue)
                                }
                            }
                            .pickerStyle(.segmented)

                            summary(stats)

                            switch mode {
                            case .weeks:
                                WeeksGrid(
                                    stats: stats,
                                    markers: markers(for: stats),
                                    width: max(0, geometry.size.width - 40),
                                    selectedIndex: $selectedIndex
                                )
                            case .years:
                                LifeYearsGrid(stats: stats)
                                    .frame(height: yearsGridHeight(stats, width: geometry.size.width - 40))
                            }

                            legend

                            LettersSection()
                                .padding(.top, 12)
                        }
                        .padding(.horizontal, 20)
                        .padding(.bottom, 24)
                    }
                    .defaultScrollAnchor(DebugLaunch.scrollToBottom ? .bottom : .top)
                    .safeAreaInset(edge: .bottom) {
                        if let selectedIndex, mode == .weeks {
                            WeekInfoCard(
                                stats: stats,
                                index: selectedIndex,
                                goals: goals(in: selectedIndex, stats: stats),
                                letters: letters(in: selectedIndex, stats: stats),
                                onOpenLetter: { openedLetter = LetterSelection(id: $0) }
                            ) {
                                withAnimation(.snappy) { self.selectedIndex = nil }
                            }
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                        }
                    }
                }
            }
            .background(Theme.background)
            .navigationTitle("Жизнь")
            .animation(.snappy, value: selectedIndex)
            .sheet(item: $openedLetter) { selection in
                LetterView(letterID: selection.id)
            }
        }
    }

    private func summary(_ stats: LifeStats) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            switch mode {
            case .weeks:
                Text("Прожито \(Format.number(stats.livedWeeks)) из \(Format.genitive(stats.totalWeeks, "недели", "недель"))")
                    .font(.headline)
                Text("Каждая клетка — неделя, строка — год жизни. Нажми на клетку, чтобы увидеть её даты и цели.")
                    .font(.footnote)
                    .foregroundStyle(Theme.secondaryText)
            case .years:
                Text("Прожито \(Format.years(stats.ageYears)) из \(stats.lifeExpectancy)")
                    .font(.headline)
                Text("Каждая точка — год. Текущий заполняется по мере того, как проходит.")
                    .font(.footnote)
                    .foregroundStyle(Theme.secondaryText)
            }
        }
    }

    private var legend: some View {
        HStack(spacing: 14) {
            legendItem(color: Theme.lived, title: "прожито")
            legendItem(color: Theme.future, title: "впереди")
            legendItem(color: Theme.accent, title: mode == .weeks ? "выполнено" : "сейчас")
            if mode == .weeks {
                HStack(spacing: 5) {
                    RoundedRectangle(cornerRadius: 2)
                        .strokeBorder(Theme.accent, lineWidth: 1)
                        .frame(width: 9, height: 9)
                    Text("срок")
                }
                if !model.data.letters.isEmpty {
                    HStack(spacing: 5) {
                        RoundedRectangle(cornerRadius: 2)
                            .strokeBorder(Color.white, lineWidth: 1)
                            .frame(width: 9, height: 9)
                        Text("письмо")
                    }
                }
            }
        }
        .font(.caption2)
        .foregroundStyle(Theme.secondaryText)
    }

    private func legendItem(color: Color, title: String) -> some View {
        HStack(spacing: 5) {
            RoundedRectangle(cornerRadius: 2)
                .fill(color)
                .frame(width: 9, height: 9)
            Text(title)
        }
    }

    private func yearsGridHeight(_ stats: LifeStats, width: CGFloat) -> CGFloat {
        let columns: CGFloat = 10
        let rows = CGFloat((stats.lifeExpectancy + 9) / 10)
        let cell = width / (columns + (columns - 1) * 0.45)
        return rows * cell + max(0, rows - 1) * cell * 0.45
    }

    private func markers(for stats: LifeStats) -> WeekMarkers {
        var markers = WeekMarkers()
        for goal in model.data.goals {
            if let completedAt = goal.completedAt {
                if let index = stats.gridIndex(for: completedAt) {
                    markers.completed.insert(index)
                }
            } else if let deadline = goal.deadline(birthDate: stats.birthDate),
                      let index = stats.gridIndex(for: deadline) {
                markers.deadlines.insert(index)
            }
        }
        for letter in model.data.letters {
            if let index = stats.gridIndex(for: letter.unlockDate(birthDate: stats.birthDate)) {
                markers.letters.insert(index)
            }
        }
        return markers
    }

    private func letters(in index: Int, stats: LifeStats) -> [FutureLetter] {
        guard let range = stats.dateRange(forGridIndex: index) else { return [] }
        return model.data.letters.filter { range.contains($0.unlockDate(birthDate: stats.birthDate)) }
    }

    private func goals(in index: Int, stats: LifeStats) -> [Goal] {
        guard let range = stats.dateRange(forGridIndex: index) else { return [] }
        return model.data.goals.filter { goal in
            if let completedAt = goal.completedAt {
                return range.contains(completedAt)
            }
            if let deadline = goal.deadline(birthDate: stats.birthDate) {
                return range.contains(deadline)
            }
            return false
        }
    }
}

// MARK: - Сетка недель

/// Геометрия сетки: 52 клетки в строке, отступ после каждого десятилетия.
struct WeeksGridLayout {
    static let columns = LifeStats.weeksPerRow
    static let labelWidth: CGFloat = 22
    static let gapRatio: CGFloat = 0.3
    static let decadeGap: CGFloat = 6

    let rows: Int
    let cell: CGFloat
    let gap: CGFloat

    init(width: CGFloat, rows: Int) {
        self.rows = max(1, rows)
        let available = max(CGFloat(Self.columns), width - Self.labelWidth)
        cell = available / (CGFloat(Self.columns) + CGFloat(Self.columns - 1) * Self.gapRatio)
        gap = cell * Self.gapRatio
    }

    var height: CGFloat { rowY(rows - 1) + cell }

    func rowY(_ row: Int) -> CGFloat {
        CGFloat(row) * (cell + gap) + CGFloat(row / 10) * Self.decadeGap
    }

    func columnX(_ column: Int) -> CGFloat {
        Self.labelWidth + CGFloat(column) * (cell + gap)
    }

    func rect(for index: Int) -> CGRect {
        CGRect(x: columnX(index % Self.columns), y: rowY(index / Self.columns), width: cell, height: cell)
    }

    func index(at point: CGPoint) -> Int? {
        guard point.x >= Self.labelWidth - gap, point.y >= 0 else { return nil }
        let column = Int((point.x - Self.labelWidth + gap / 2) / (cell + gap))
        guard column >= 0, column < Self.columns else { return nil }
        var row = min(rows - 1, max(0, Int(point.y / (cell + gap))))
        while row > 0 && rowY(row) > point.y { row -= 1 }
        while row < rows - 1 && rowY(row + 1) <= point.y { row += 1 }
        guard point.y <= rowY(row) + cell + gap + Self.decadeGap else { return nil }
        return row * Self.columns + column
    }
}

struct WeeksGrid: View {
    let stats: LifeStats
    let markers: WeekMarkers
    let width: CGFloat
    @Binding var selectedIndex: Int?

    var body: some View {
        let layout = WeeksGridLayout(width: width, rows: stats.gridRows)
        let lived = stats.livedGridCells
        let current = stats.currentGridIndex
        Canvas { context, _ in
            let radius = layout.cell * 0.28
            for index in 0..<stats.gridCellCount {
                let rect = layout.rect(for: index)
                let shape = Path(roundedRect: rect, cornerRadius: radius)
                let color: Color
                if markers.completed.contains(index) || index == current {
                    color = Theme.accent
                } else if index < lived {
                    color = Theme.lived
                } else {
                    color = Theme.future
                }
                context.fill(shape, with: .color(color))
                if markers.deadlines.contains(index) && index >= lived {
                    let ring = Path(roundedRect: rect.insetBy(dx: -0.8, dy: -0.8), cornerRadius: radius + 0.8)
                    context.stroke(ring, with: .color(Theme.accent), lineWidth: 1.2)
                }
                if markers.letters.contains(index) {
                    let ring = Path(roundedRect: rect.insetBy(dx: -0.8, dy: -0.8), cornerRadius: radius + 0.8)
                    context.stroke(ring, with: .color(.white), lineWidth: 1.2)
                }
                if index == selectedIndex {
                    let ring = Path(roundedRect: rect.insetBy(dx: -1.5, dy: -1.5), cornerRadius: radius + 1.5)
                    context.stroke(ring, with: .color(.white), lineWidth: 1.5)
                }
            }
            for row in stride(from: 0, to: layout.rows, by: 10) {
                var label = context.resolve(Text("\(row)").font(.system(size: 9, weight: .medium)))
                label.shading = .color(Theme.tertiaryText)
                context.draw(
                    label,
                    at: CGPoint(x: WeeksGridLayout.labelWidth - 5, y: layout.rowY(row) + layout.cell / 2),
                    anchor: .trailing
                )
            }
        }
        .frame(width: width, height: layout.height)
        .overlay(alignment: .topLeading) {
            if let current {
                let rect = layout.rect(for: current)
                PulsingMarker(size: layout.cell)
                    .position(x: rect.midX, y: rect.midY)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { location in
            guard let index = layout.index(at: location), index < stats.gridCellCount else { return }
            withAnimation(.snappy) {
                selectedIndex = selectedIndex == index ? nil : index
            }
        }
        .sensoryFeedback(.selection, trigger: selectedIndex)
        .accessibilityElement()
        .accessibilityLabel("Жизнь в неделях: прожито \(Format.weeks(stats.livedWeeks)), впереди \(Format.weeks(stats.remainingWeeks))")
    }
}

struct WeekInfoCard: View {
    let stats: LifeStats
    let index: Int
    let goals: [Goal]
    var letters: [FutureLetter] = []
    var onOpenLetter: (UUID) -> Void = { _ in }
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text("Неделя \(Format.number(index + 1))")
                    .font(.headline)
                Text(status)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(statusColor)
                Spacer()
                Button(action: onClose) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .foregroundStyle(Theme.tertiaryText)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Закрыть")
            }
            if let range = stats.dateRange(forGridIndex: index) {
                Text("\(Format.weekRange(range.lowerBound, range.upperBound)) · \(Format.years(stats.age(forGridIndex: index)))")
                    .font(.subheadline)
                    .foregroundStyle(Theme.secondaryText)
            }
            ForEach(goals) { goal in
                Label(goal.title, systemImage: goal.isCompleted ? "checkmark.circle.fill" : "flag")
                    .font(.subheadline)
                    .foregroundStyle(goal.isCompleted ? Theme.accent : Color.white)
                    .lineLimit(2)
            }
            ForEach(letters) { letter in
                Button {
                    onOpenLetter(letter.id)
                } label: {
                    Label(
                        letter.isOpened
                            ? "Письмо от \(Format.shortDate(letter.createdAt)) — прочитано"
                            : "Письмо от \(Format.shortDate(letter.createdAt)) откроется в эту неделю",
                        systemImage: letter.isOpened ? "envelope.open" : "envelope.fill"
                    )
                    .font(.subheadline)
                    .foregroundStyle(Color.white)
                    .lineLimit(2)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    private var status: String {
        let lived = stats.livedGridCells
        if index == stats.currentGridIndex { return "сейчас" }
        return index < lived ? "прожита" : "впереди"
    }

    private var statusColor: Color {
        index == stats.currentGridIndex ? Theme.accent : Theme.tertiaryText
    }
}

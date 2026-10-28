import SwiftUI

struct GoalDetailView: View {
    let goalID: UUID
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var showEditor = false
    @State private var showCompletion = false
    @State private var showFocusLimit = false

    var body: some View {
        Group {
            if let goal = model.goal(id: goalID) {
                content(goal)
            } else {
                ContentUnavailableView("Цель удалена", systemImage: "trash")
            }
        }
        .background(Theme.background)
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: model.goal(id: goalID) == nil) { _, isGone in
            if isGone { dismiss() }
        }
    }

    private func content(_ goal: Goal) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header(goal)
                if goal.isCompleted {
                    memory(goal)
                }
                if !goal.steps.isEmpty {
                    stepsCard(goal)
                }
                if goal.hasDeadline {
                    deadlineCard(goal)
                }
                if !goal.note.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        SectionHeader(title: "Зачем")
                        Text(goal.note)
                            .foregroundStyle(Color.white.opacity(0.9))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .mementoCard()
                }
                actions(goal)
                Text("Добавлена \(Format.date(goal.createdAt))")
                    .font(.caption)
                    .foregroundStyle(Theme.tertiaryText)
            }
            .padding(20)
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if !goal.isCompleted {
                    Button {
                        if !model.toggleFocus(id: goal.id) { showFocusLimit = true }
                    } label: {
                        Image(systemName: goal.isFocused ? "star.fill" : "star")
                    }
                    .accessibilityLabel(goal.isFocused ? "Убрать из фокуса" : "В фокус")
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button("Изменить") { showEditor = true }
            }
        }
        .sheet(isPresented: $showEditor) {
            GoalEditorView(goal: goal)
        }
        .sheet(isPresented: $showCompletion) {
            CompleteGoalSheet(goal: goal)
        }
        .alert("В фокусе уже три цели", isPresented: $showFocusLimit) {
            Button("Понятно", role: .cancel) {}
        } message: {
            Text("Сначала убери звёздочку с одной из них.")
        }
    }

    private func header(_ goal: Goal) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: goal.category.symbol)
                Text(goal.category.title.uppercased())
                    .tracking(1.5)
            }
            .font(.caption.weight(.semibold))
            .foregroundStyle(Theme.tertiaryText)

            if let code = goal.countryCode {
                Text(flagEmoji(for: code))
                    .font(.system(size: 56))
            }
            Text(goal.title)
                .font(.system(.title, design: .serif))
                .foregroundStyle(Color.white)
                .fixedSize(horizontal: false, vertical: true)
            if goal.isCompleted {
                Label("Выполнено", systemImage: "checkmark.seal.fill")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Theme.accent)
            }
        }
    }

    private func memory(_ goal: Goal) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            if let image = model.photo(for: goal) {
                Color.clear
                    .frame(height: 280)
                    .overlay {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFill()
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            }
            if let date = goal.completedAt {
                Text(completedText(date))
                    .font(.subheadline)
                    .foregroundStyle(Theme.secondaryText)
            }
            if !goal.reflection.isEmpty {
                Text(goal.reflection)
                    .font(.system(.body, design: .serif))
                    .foregroundStyle(Color.white)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func stepsCard(_ goal: Goal) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Шаги", trailing: "\(goal.completedStepsCount) из \(goal.steps.count)")
            ForEach(goal.steps) { step in
                Button {
                    model.toggleStep(goalID: goal.id, stepID: step.id)
                } label: {
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Image(systemName: step.isDone ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(step.isDone ? Theme.accent : Theme.tertiaryText)
                        Text(step.title)
                            .strikethrough(step.isDone, color: Theme.tertiaryText)
                            .foregroundStyle(step.isDone ? Theme.secondaryText : Color.white)
                            .multilineTextAlignment(.leading)
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            ThinProgressBar(
                progress: Double(goal.completedStepsCount) / Double(max(1, goal.steps.count)),
                height: 3,
                fill: Theme.accent
            )
        }
        .mementoCard()
        .sensoryFeedback(.success, trigger: goal.completedStepsCount) { old, new in new > old }
    }

    private func deadlineCard(_ goal: Goal) -> some View {
        let birthDate = model.profile?.birthDate
        let deadline = birthDate.flatMap { goal.deadline(birthDate: $0) }
        return VStack(alignment: .leading, spacing: 6) {
            SectionHeader(title: "Срок")
            Text(deadlineTitle(goal, deadline: deadline))
                .font(.title3)
                .foregroundStyle(Color.white)
            if !goal.isCompleted, let deadline, let stats = model.stats() {
                Text(deadlineInsight(deadline, stats: stats))
                    .font(.subheadline)
                    .foregroundStyle(Theme.secondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .mementoCard()
    }

    @ViewBuilder
    private func actions(_ goal: Goal) -> some View {
        if goal.isCompleted {
            VStack(spacing: 12) {
                Button {
                    showCompletion = true
                } label: {
                    Label("Изменить воспоминание", systemImage: "photo.on.rectangle")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.bordered)

                Button {
                    model.reopenGoal(id: goal.id)
                } label: {
                    Text("Вернуть в список")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.bordered)
                .tint(Color.white)
            }
        } else {
            Button {
                showCompletion = true
            } label: {
                AccentButtonLabel(title: "Выполнено", systemImage: "checkmark")
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: Тексты

    private func completedText(_ date: Date) -> String {
        var text = "Выполнено \(Format.date(date))"
        if let stats = model.stats(at: date) {
            text += " — " + Format.atAge(stats.age(at: date))
        }
        return text
    }

    private func deadlineTitle(_ goal: Goal, deadline: Date?) -> String {
        if let age = goal.targetAge {
            if let deadline {
                return "\(Format.byAge(age).capitalizedFirst) — \(Format.monthYear(deadline))"
            }
            return Format.byAge(age).capitalizedFirst
        }
        if let date = goal.targetDate {
            return "До " + Format.date(date)
        }
        return ""
    }

    private func deadlineInsight(_ deadline: Date, stats: LifeStats) -> String {
        let seconds = deadline.timeIntervalSince(stats.now)
        guard seconds > 0 else {
            return "Срок прошёл. Назначь новый — или сделай прямо сейчас."
        }
        let weeks = Int(seconds / LifeStats.secondsPerDay) / 7
        guard stats.remainingWeeks > 0 else {
            return "До срока \(Format.weeks(weeks))."
        }
        let share = Double(weeks) / Double(stats.remainingWeeks)
        let shareText = share < 0.01 ? "меньше 1%" : Format.percent(share, digits: 0)
        return "До срока \(Format.weeks(weeks)) — это \(shareText) оставшейся жизни."
    }
}

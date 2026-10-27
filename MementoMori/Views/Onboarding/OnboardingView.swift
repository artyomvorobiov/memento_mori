import SwiftUI

struct OnboardingView: View {
    @Environment(AppModel.self) private var model
    @State private var step = DebugLaunch.onboardingStep
    @State private var birthDate = Calendar.memento.date(byAdding: .year, value: -30, to: Date()) ?? Date()
    @State private var expectancy = ExpectancyChoice.suggested()
    @State private var showCountryPicker = false
    @State private var selectedIdeas: Set<String> = []
    @State private var remindersEnabled = true

    private let lastStep = 2

    var body: some View {
        VStack(spacing: 0) {
            Group {
                switch step {
                case 0: welcomeStep
                case 1: lifeStep
                default: goalsStep
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .transition(.opacity)
            .id(step)

            footer
        }
        .background(Theme.background.ignoresSafeArea())
    }

    // MARK: Шаги

    private var welcomeStep: some View {
        VStack(alignment: .leading, spacing: 24) {
            Spacer()
            Text("MEMENTO\nMORI")
                .font(.system(size: 52, weight: .ultraLight))
                .tracking(6)
                .lineSpacing(4)
            Text("Помни о смерти.")
                .font(.system(.title2, design: .serif))
                .italic()
            Text("Не чтобы бояться, а чтобы жить. Собери всё, что хочешь успеть, — и смотри, сколько у тебя осталось времени.")
                .font(.body)
                .foregroundStyle(Theme.secondaryText)
                .fixedSize(horizontal: false, vertical: true)
            Spacer()
            Spacer()
        }
        .padding(.horizontal, 28)
    }

    private var lifeStep: some View {
        let stats = LifeStats(birthDate: birthDate, lifeExpectancy: expectancy.years)
        return ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("Дата твоего рождения")
                    .font(.title2.weight(.semibold))
                DatePicker("Дата рождения", selection: $birthDate, in: birthDateRange, displayedComponents: .date)
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                    .frame(maxWidth: .infinity)

                expectancySection(age: stats.ageYears)

                VStack(alignment: .leading, spacing: 4) {
                    Text(Format.number(stats.remainingWeeks))
                        .font(.system(size: 56, weight: .thin))
                        .monospacedDigit()
                        .contentTransition(.numericText())
                        .animation(.snappy, value: stats.remainingWeeks)
                    Text(RussianPlural.form(stats.remainingWeeks, "неделя впереди", "недели впереди", "недель впереди"))
                        .foregroundStyle(Theme.secondaryText)
                }
            }
            .padding(.horizontal, 28)
            .padding(.top, 32)
        }
        .sheet(isPresented: $showCountryPicker) {
            NavigationStack {
                ExpectancyCountryList(selection: countryBinding)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Отмена") { showCountryPicker = false }
                        }
                    }
            }
        }
    }

    private func expectancySection(age: Int) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Сколько лет закладываем на жизнь?")
                .font(.headline)
            Picker("Продолжительность жизни", selection: $expectancy.source) {
                ForEach(ExpectancySource.allCases) { source in
                    Text(source.title).tag(source)
                }
            }
            .pickerStyle(.segmented)

            switch expectancy.source {
            case .country:
                Button {
                    showCountryPicker = true
                } label: {
                    HStack(spacing: 8) {
                        Text(ExpectancyCountryList.label(for: expectancy.country))
                            .foregroundStyle(Color.white)
                        Spacer()
                        Text(Format.years(expectancy.years))
                            .font(.title3)
                            .monospacedDigit()
                            .foregroundStyle(Color.white)
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.tertiaryText)
                    }
                    .padding(14)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
                Text(countryHint(age: age))
                    .font(.footnote)
                    .foregroundStyle(age >= expectancy.years - 2 ? Theme.accent : Theme.secondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            case .custom:
                Stepper(value: $expectancy.customYears, in: Profile.lifeExpectancyRange) {
                    Text(Format.years(expectancy.customYears))
                        .font(.title3)
                        .monospacedDigit()
                }
                Text("В среднем по миру люди живут около 73 лет. Можно взять с запасом — это легко поменять позже.")
                    .font(.footnote)
                    .foregroundStyle(Theme.secondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func countryHint(age: Int) -> String {
        if age >= expectancy.years - 2 {
            return "Средняя по стране уже почти позади — и это хорошо: кто прожил дольше, обычно и живёт дольше среднего. Лучше укажи своё число."
        }
        return "Средняя продолжительность жизни в стране по данным Всемирного банка за 2023 год — без расчётов и гаданий. Поменять можно в любой момент."
    }

    private var countryBinding: Binding<String> {
        Binding(
            get: { expectancy.country },
            set: { expectancy.select(country: $0) }
        )
    }

    private var goalsStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Что ты хочешь успеть?")
                    .font(.title2.weight(.semibold))
                Text("Выбери несколько идей для начала. Свои цели добавишь потом — список можно менять когда угодно.")
                    .foregroundStyle(Theme.secondaryText)
                    .fixedSize(horizontal: false, vertical: true)

                VStack(spacing: 8) {
                    ForEach(IdeaLibrary.starter) { idea in
                        ideaRow(idea)
                    }
                }

                Toggle(isOn: $remindersEnabled) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Ежедневное напоминание")
                        Text("В 9:00 — мысль дня, цель из списка или просто «помни».")
                            .font(.footnote)
                            .foregroundStyle(Theme.secondaryText)
                    }
                }
                .padding(.top, 8)
            }
            .padding(.horizontal, 28)
            .padding(.top, 32)
            .padding(.bottom, 16)
        }
    }

    private func ideaRow(_ idea: Idea) -> some View {
        let isSelected = selectedIdeas.contains(idea.id)
        return Button {
            if isSelected {
                selectedIdeas.remove(idea.id)
            } else {
                selectedIdeas.insert(idea.id)
            }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: idea.category.symbol)
                    .frame(width: 24)
                    .foregroundStyle(Theme.secondaryText)
                Text(idea.title)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isSelected ? Theme.accent : Theme.tertiaryText)
            }
            .padding(14)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.selection, trigger: isSelected)
    }

    // MARK: Навигация

    private var footer: some View {
        HStack {
            if step > 0 {
                Button("Назад") {
                    withAnimation(.easeInOut) { step -= 1 }
                }
                .foregroundStyle(Theme.secondaryText)
            }
            Spacer()
            HStack(spacing: 6) {
                ForEach(0...lastStep, id: \.self) { index in
                    Circle()
                        .fill(index == step ? Theme.accent : Theme.future)
                        .frame(width: 6, height: 6)
                }
            }
            Spacer()
            Button(action: advance) {
                Text(step == lastStep ? "Начать" : "Далее")
                    .font(.headline)
                    .padding(.horizontal, 28)
                    .padding(.vertical, 14)
                    .background(Theme.accent, in: Capsule())
                    .foregroundStyle(Color.black)
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 16)
    }

    private var birthDateRange: ClosedRange<Date> {
        let earliest = Calendar.memento.date(byAdding: .year, value: -110, to: Date()) ?? Date.distantPast
        return earliest...Date()
    }

    private func advance() {
        guard step == lastStep else {
            withAnimation(.easeInOut) { step += 1 }
            return
        }
        let ideas = IdeaLibrary.starter.filter { selectedIdeas.contains($0.id) }
        model.completeOnboarding(
            birthDate: birthDate,
            expectancy: expectancy,
            ideas: ideas,
            remindersEnabled: remindersEnabled
        )
    }
}

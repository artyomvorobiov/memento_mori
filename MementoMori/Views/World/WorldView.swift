import SwiftUI

/// Вкладка «Мир»: интерактивная карта путешествий, части света и список стран.
struct WorldView: View {
    enum Filter: String, CaseIterable, Identifiable {
        case visited
        case wished
        case all

        var id: String { rawValue }

        var title: String {
            switch self {
            case .visited: "Посещено"
            case .wished: "В планах"
            case .all: "Все"
            }
        }
    }

    @Environment(AppModel.self) private var model
    @State private var selected: String?
    @State private var focusRequest: InteractiveWorldMap.FocusRequest?
    @State private var filter: Filter = .visited
    @State private var searchText = ""
    @State private var showShare = false

    var body: some View {
        let travel = TravelStats(data: model.data)
        NavigationStack {
            VStack(spacing: 0) {
                InteractiveWorldMap(
                    visited: travel.visited,
                    wished: travel.wished,
                    selected: $selected,
                    focusRequest: focusRequest,
                    initialCenter: Locale.current.region?.identifier ?? "RU"
                )
                .frame(height: 300)
                .background(Color(white: 0.04))

                List {
                    Section {
                        summary(travel)
                    }
                    .listRowBackground(Color.clear)

                    Section("Части света") {
                        ForEach(Continent.allCases) { continent in
                            continentRow(continent, travel: travel)
                        }
                    }

                    Section {
                        Picker("Список", selection: $filter) {
                            ForEach(Filter.allCases) { item in
                                Text(item.title).tag(item)
                            }
                        }
                        .pickerStyle(.segmented)
                        .listRowBackground(Color.clear)

                        let countries = filteredCountries(travel)
                        if countries.isEmpty {
                            Text(emptyText)
                                .font(.subheadline)
                                .foregroundStyle(Theme.secondaryText)
                                .listRowBackground(Color.clear)
                        }
                        ForEach(countries) { country in
                            countryRow(country, travel: travel)
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
            .background(Theme.background)
            .navigationTitle("Мир")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $searchText, prompt: "Найти страну")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showShare = true
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                    }
                    .accessibilityLabel("Поделиться картой")
                }
            }
            .safeAreaInset(edge: .bottom) {
                if let code = selected {
                    SelectedCountryCard(
                        code: code,
                        status: travel.status(of: code),
                        wishGoalID: model.wishGoal(forCountry: code)?.id,
                        onToggleVisited: { toggleVisited(code) },
                        onToggleWish: { toggleWish(code) },
                        onClose: { withAnimation(.snappy) { selected = nil } }
                    )
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(.snappy, value: selected)
            .navigationDestination(for: UUID.self) { id in
                GoalDetailView(goalID: id)
            }
            .sheet(isPresented: $showShare) {
                RenderedShareSheet(title: "Карта путешествий") {
                    TravelShareCard(travel: travel)
                }
            }
        }
    }

    // MARK: Разделы

    private func summary(_ travel: TravelStats) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline, spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(travel.visitedCountries)")
                        .font(.system(size: 48, weight: .thin))
                        .monospacedDigit()
                        .contentTransition(.numericText())
                    Text("из \(Format.genitive(travel.totalCountries, "страны", "стран"))")
                        .font(.caption)
                        .foregroundStyle(Theme.secondaryText)
                }
                Spacer()
                statColumn(Format.percent(travel.worldShare, digits: 0), "мира")
                statColumn("\(travel.continentsVisited)", "частей света\nиз 6")
                statColumn("\(travel.wishedCount)", "в планах")
            }
            ThinProgressBar(progress: travel.worldShare, fill: Theme.accent)
            if let milestone = travel.nextMilestone {
                Text(milestoneText(milestone))
                    .font(.footnote)
                    .foregroundStyle(Theme.secondaryText)
            }
            if travel.visitedTerritories > 0 {
                Text("и ещё \(travel.visitedTerritories) \(RussianPlural.form(travel.visitedTerritories, "территория", "территории", "территорий"))")
                    .font(.caption)
                    .foregroundStyle(Theme.tertiaryText)
            }
        }
        .padding(.vertical, 4)
    }

    private func statColumn(_ value: String, _ caption: String) -> some View {
        VStack(alignment: .trailing, spacing: 2) {
            Text(value)
                .font(.title3.weight(.light))
                .monospacedDigit()
            Text(caption)
                .font(.caption2)
                .multilineTextAlignment(.trailing)
                .foregroundStyle(Theme.secondaryText)
        }
    }

    private func milestoneText(_ milestone: (target: Int, left: Int)) -> String {
        if milestone.target == 1 {
            return "Отметь первую страну — нажми на неё на карте."
        }
        return "До \(Format.genitive(milestone.target, "страны", "стран")) — ещё \(milestone.left)."
    }

    private func continentRow(_ continent: Continent, travel: TravelStats) -> some View {
        let progress = travel.progress(for: continent)
        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(continent.title)
                Spacer()
                Text("\(progress.visited) из \(progress.total)")
                    .monospacedDigit()
                    .foregroundStyle(progress.visited > 0 ? Color.white : Theme.tertiaryText)
            }
            .font(.subheadline)
            ThinProgressBar(
                progress: progress.total > 0 ? Double(progress.visited) / Double(progress.total) : 0,
                height: 3,
                fill: Theme.accent
            )
        }
        .padding(.vertical, 2)
    }

    private func countryRow(_ country: Country, travel: TravelStats) -> some View {
        Button {
            selected = country.code
            focusRequest = InteractiveWorldMap.FocusRequest(code: country.code)
        } label: {
            CountryRow(
                country: country,
                isWished: travel.wished.contains(country.code),
                isVisited: travel.visited.contains(country.code),
                onToggleWish: { toggleWish(country.code) },
                onToggleVisited: { toggleVisited(country.code) }
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: Данные

    private var emptyText: String {
        if !searchText.isEmpty { return "Ничего не найдено." }
        switch filter {
        case .visited: return "Нажми на страну на карте и отметь «Посещено»."
        case .wished: return "Отметь страны, куда хочешь поехать, — они попадут в цели."
        case .all: return "Ничего не найдено."
        }
    }

    private func filteredCountries(_ travel: TravelStats) -> [Country] {
        let query = searchText.trimmingCharacters(in: .whitespaces)
        return CountryCatalog.all.filter { country in
            if !query.isEmpty && !country.name.localizedCaseInsensitiveContains(query) {
                return false
            }
            switch filter {
            case .visited: return travel.visited.contains(country.code)
            case .wished: return travel.wished.contains(country.code)
            case .all: return true
            }
        }
    }

    private func toggleVisited(_ code: String) {
        guard let country = CountryCatalog.country(code: code) else { return }
        model.toggleVisited(country)
    }

    private func toggleWish(_ code: String) {
        guard let country = CountryCatalog.country(code: code) else { return }
        model.toggleWish(country)
    }
}

/// Карточка выбранной на карте страны.
struct SelectedCountryCard: View {
    let code: String
    let status: CountryStatus
    let wishGoalID: UUID?
    let onToggleVisited: () -> Void
    let onToggleWish: () -> Void
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                Text(flagEmoji(for: code))
                    .font(.system(size: 40))
                VStack(alignment: .leading, spacing: 2) {
                    Text(Country.name(for: code))
                        .font(.title3.weight(.semibold))
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(Theme.secondaryText)
                }
                Spacer()
                Button(action: onClose) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title2)
                        .foregroundStyle(Theme.tertiaryText)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Закрыть")
            }
            HStack(spacing: 10) {
                toggleButton(
                    title: "Посещено",
                    systemImage: status == .visited ? "checkmark.circle.fill" : "circle",
                    isOn: status == .visited,
                    action: onToggleVisited
                )
                toggleButton(
                    title: "В планах",
                    systemImage: wishGoalID != nil ? "star.fill" : "star",
                    isOn: wishGoalID != nil,
                    action: onToggleWish
                )
            }
            if let wishGoalID {
                NavigationLink(value: wishGoalID) {
                    Label("Открыть цель", systemImage: "arrow.right.circle")
                        .font(.subheadline)
                }
                .foregroundStyle(Theme.accent)
            }
        }
        .padding(16)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
        .sensoryFeedback(.success, trigger: status == .visited) { _, isVisited in isVisited }
    }

    private var subtitle: String {
        var parts: [String] = []
        if let continent = Continent.of(code) {
            parts.append(continent.title)
        }
        if let years = Profile.averageLifeExpectancy(forCountry: code) {
            parts.append("живут в среднем \(Format.years(years))")
        }
        return parts.joined(separator: " · ")
    }

    private func toggleButton(title: String, systemImage: String, isOn: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: systemImage)
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(isOn ? Theme.accent : Color.white.opacity(0.1), in: Capsule())
                .foregroundStyle(isOn ? Color.black : Color.white)
        }
        .buttonStyle(.plain)
    }
}

/// Строка страны: звёздочка — «хочу», кружок — «посещено».
struct CountryRow: View {
    let country: Country
    let isWished: Bool
    let isVisited: Bool
    let onToggleWish: () -> Void
    let onToggleVisited: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Text(country.flag)
                .font(.title2)
            VStack(alignment: .leading, spacing: 2) {
                Text(country.name)
                    .foregroundStyle(Color.white)
                if isWished {
                    Text("в списке целей")
                        .font(.caption)
                        .foregroundStyle(Theme.accent)
                }
            }
            Spacer()
            Button(action: onToggleWish) {
                Image(systemName: isWished ? "star.fill" : "star")
                    .font(.title3)
                    .foregroundStyle(isWished ? Theme.accent : Theme.tertiaryText)
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(isWished ? "Убрать из целей" : "Хочу побывать")
            Button(action: onToggleVisited) {
                Image(systemName: isVisited ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isVisited ? Theme.accent : Theme.tertiaryText)
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(isVisited ? "Снять отметку о посещении" : "Отметить посещённой")
        }
        .contentShape(Rectangle())
        .padding(.vertical, 2)
    }
}

/// Карточка «моя карта путешествий» для соцсетей.
struct TravelShareCard: View {
    let travel: TravelStats

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("MEMENTO MORI · МОЙ МИР")
                .font(.system(size: 12, weight: .semibold))
                .tracking(3)
                .foregroundStyle(Theme.secondaryText)

            WorldMapView(visited: travel.visited, wished: travel.wished)
                .frame(height: 140)

            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("\(travel.visitedCountries)")
                    .font(.system(size: 44, weight: .thin))
                Text(RussianPlural.form(travel.visitedCountries, "страна", "страны", "стран"))
                    .font(.title3)
                Spacer()
                Text(Format.percent(travel.worldShare, digits: 0) + " мира")
                    .font(.title3.weight(.light))
                    .foregroundStyle(Theme.accent)
            }

            VStack(alignment: .leading, spacing: 6) {
                ForEach(Continent.allCases) { continent in
                    let progress = travel.progress(for: continent)
                    HStack {
                        Text(continent.title)
                        Spacer()
                        Text("\(progress.visited) из \(progress.total)")
                            .monospacedDigit()
                    }
                    .font(.system(size: 12))
                    .foregroundStyle(progress.visited > 0 ? Color.white : Theme.tertiaryText)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(24)
        .frame(width: 360, height: 450, alignment: .topLeading)
        .background(Color.black)
        .foregroundStyle(Color.white)
        .environment(\.colorScheme, .dark)
    }
}

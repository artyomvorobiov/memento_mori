import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if let page = DebugLaunch.widgetGalleryPage {
                WidgetGalleryView(page: page)
            } else if model.storageUnavailable {
                StorageUnavailableView()
            } else if model.profile == nil {
                OnboardingView()
            } else {
                MainTabView()
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                model.handleBecameActive()
            }
        }
        .alert("Проблема с данными", isPresented: problemBinding) {
            Button("Понятно", role: .cancel) {}
        } message: {
            Text(model.dataProblem ?? "")
        }
    }

    private var problemBinding: Binding<Bool> {
        Binding(
            get: { model.dataProblem != nil },
            set: { isPresented in
                if !isPresented { model.dataProblem = nil }
            }
        )
    }
}

struct MainTabView: View {
    static let worldTab = 2
    static let lifeTab = 3

    @Environment(AppModel.self) private var model
    @AppStorage("selectedTab") private var selectedTab = 0
    @State private var routedLetter: LetterSelection?
    @State private var routedCounter: CounterSelection?
    @State private var isWritingLetter = false

    var body: some View {
        TabView(selection: $selectedTab) {
            TodayView()
                .tabItem { Label("Сегодня", systemImage: "hourglass") }
                .tag(0)
            GoalsView()
                .tabItem { Label("Цели", systemImage: "checklist") }
                .tag(1)
            WorldView()
                .tabItem { Label("Мир", systemImage: "globe.europe.africa.fill") }
                .tag(Self.worldTab)
            LifeCalendarView()
                .tabItem { Label("Жизнь", systemImage: "square.grid.3x3.fill") }
                .tag(Self.lifeTab)
        }
        .sensoryFeedback(.success, trigger: model.data.completedGoals.count) { old, new in
            new > old
        }
        .onOpenURL { url in
            if let link = DeepLink(url: url) {
                route(link)
            }
        }
        .onAppear(perform: openDebugScreen)
        .sheet(item: $routedLetter) { selection in
            LetterView(letterID: selection.id)
        }
        .sheet(item: $routedCounter) { selection in
            CounterDetailView(counterID: selection.id)
        }
        .sheet(isPresented: $isWritingLetter) {
            LetterEditorView()
        }
    }

    /// Ссылки из виджетов: письмо, новое письмо, счётчик.
    private func route(_ link: DeepLink) {
        switch link {
        case .letter(let id):
            selectedTab = Self.lifeTab
            routedLetter = LetterSelection(id: id)
        case .newLetter:
            selectedTab = Self.lifeTab
            isWritingLetter = true
        case .counter(let id):
            selectedTab = 0
            routedCounter = CounterSelection(id: id)
        }
    }

    private func openDebugScreen() {
        switch DebugLaunch.letterScreen {
        case "open":
            if let letter = model.waitingLetters().first {
                routedLetter = LetterSelection(id: letter.id)
            }
        case "new":
            isWritingLetter = true
        default:
            break
        }
    }
}

/// Показывается, если файл данных существует, но система пока не даёт его прочитать.
struct StorageUnavailableView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "lock")
                .font(.system(size: 40, weight: .light))
                .foregroundStyle(Theme.secondaryText)
            Text("Данные пока недоступны")
                .font(.headline)
            Text("Разблокируй iPhone и попробуй ещё раз.")
                .font(.subheadline)
                .foregroundStyle(Theme.secondaryText)
                .multilineTextAlignment(.center)
            Button("Попробовать снова") {
                model.handleBecameActive()
            }
            .buttonStyle(.bordered)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.background)
    }
}

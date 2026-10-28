import Foundation

/// Аргументы запуска для отладки и скриншотов (работают только в Debug-сборке).
///
/// В Xcode: Product → Scheme → Edit Scheme… → Run → Arguments, например:
///   -MementoDemo                  демонстрационные данные (настоящие не трогаются)
///   -MementoWidgetGallery 1       галерея виджетов (страницы 1, 2 и 3)
///   -MementoOnboardingStep 1      открыть знакомство на нужном шаге
///   -MementoScrollToBottom YES    открыть «Сегодня» прокрученным вниз
///   -MementoNewGoal "текст"       открыть создание цели с готовым названием
///   -selectedTab 2                открыть нужную вкладку (0 — «Сегодня», 1 — «Цели», 2 — «Мир», 3 — «Жизнь»)
enum DebugLaunch {
    static var isDemo: Bool {
        #if DEBUG
        return ProcessInfo.processInfo.arguments.contains(DemoData.launchArgument)
        #else
        return false
        #endif
    }

    static var widgetGalleryPage: Int? {
        #if DEBUG
        let page = UserDefaults.standard.integer(forKey: "MementoWidgetGallery")
        return page > 0 ? page : nil
        #else
        return nil
        #endif
    }

    static var onboardingStep: Int {
        #if DEBUG
        return UserDefaults.standard.integer(forKey: "MementoOnboardingStep")
        #else
        return 0
        #endif
    }

    static var newGoalTitle: String? {
        #if DEBUG
        return UserDefaults.standard.string(forKey: "MementoNewGoal")
        #else
        return nil
        #endif
    }

    static var scrollToBottom: Bool {
        #if DEBUG
        return UserDefaults.standard.bool(forKey: "MementoScrollToBottom")
        #else
        return false
        #endif
    }
}

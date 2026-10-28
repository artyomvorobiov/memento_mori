import Foundation

/// Аргументы запуска для отладки и скриншотов (работают только в Debug-сборке).
///
/// В Xcode: Product → Scheme → Edit Scheme… → Run → Arguments, например:
///   -MementoDemo                  демонстрационные данные (настоящие не трогаются)
///   -MementoWidgetGallery 1       галерея виджетов (страницы 1, 2 и 3)
///   -MementoOnboardingStep 1      открыть знакомство на нужном шаге
///   -MementoScrollToBottom YES    открыть «Сегодня» прокрученным вниз
///   -MementoNewGoal "текст"       открыть создание цели с готовым названием
///   -MementoCounterDetail YES     открыть подробности первого счётчика
///   -MementoLetter open           открыть письмо, которое ждёт (new — написать новое)
///   -MementoPhotoImportDemo YES   показать поиск стран в фото на демо-данных
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

    /// Открыть подробности первого счётчика на «Сегодня».
    static var openCounterDetail: Bool {
        #if DEBUG
        return UserDefaults.standard.bool(forKey: "MementoCounterDetail")
        #else
        return false
        #endif
    }

    /// Открыть письмо, которое ждёт, или создание нового (значение «new»).
    static var letterScreen: String? {
        #if DEBUG
        return UserDefaults.standard.string(forKey: "MementoLetter")
        #else
        return nil
        #endif
    }

    /// Показать результат поиска стран в фото на демо-данных.
    static var photoImportDemo: Bool {
        #if DEBUG
        return UserDefaults.standard.bool(forKey: "MementoPhotoImportDemo")
        #else
        return false
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

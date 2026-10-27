import SwiftUI

@main
struct MementoMoriApp: App {
    @State private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .environment(\.locale, Locale(identifier: "ru_RU"))
                .preferredColorScheme(.dark)
                .tint(Theme.accent)
        }
    }
}

import SwiftUI
import WidgetKit

@main
struct MementoWidgetBundle: WidgetBundle {
    var body: some Widget {
        LifeWidget()
        GoalWidget()
    }
}

import SwiftUI
import WidgetKit

/// Отладочная галерея: все виджеты на демонстрационных данных.
/// Открывается аргументом запуска `-MementoWidgetGallery 1` (страницы 1–4).
struct WidgetGalleryView: View {
    let page: Int
    private let snapshot = WidgetSnapshot.sample

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.18, green: 0.2, blue: 0.3), Color(red: 0.32, green: 0.22, blue: 0.3)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            if let profile = snapshot.profile {
                let stats = LifeStats(profile: profile)
                let goal = snapshot.focusedGoals.first ?? snapshot.activeGoals.first
                VStack(spacing: 20) {
                    switch page {
                    case 1: firstPage(stats: stats, goal: goal)
                    case 2: secondPage(stats: stats)
                    case 4: lastTimePage(stats: stats)
                    default: travelPage
                    }
                }
            }
        }
        .environment(\.colorScheme, .dark)
    }

    @ViewBuilder
    private func firstPage(stats: LifeStats, goal: Goal?) -> some View {
        HStack(spacing: 20) {
            WidgetFrame(width: 170, height: 170) {
                SmallLifeView(stats: stats)
            }
            WidgetFrame(width: 170, height: 170) {
                if let goal {
                    SmallGoalView(goal: goal)
                } else {
                    NoGoalsView()
                }
            }
        }
        WidgetFrame(width: 360, height: 170) {
            MediumLifeView(stats: stats, quote: Quotes.ofTheDay())
        }
        WidgetFrame(width: 360, height: 170) {
            if let goal {
                MediumGoalView(goal: goal, birthDate: snapshot.profile?.birthDate, date: Date())
            } else {
                NoGoalsView()
            }
        }
    }

    @ViewBuilder
    private func secondPage(stats: LifeStats) -> some View {
        WidgetFrame(width: 360, height: 380) {
            LargeLifeView(stats: stats, snapshot: snapshot)
        }
        HStack(spacing: 16) {
            AccessoryFrame(width: 72, height: 72) {
                CircularLifeView(stats: stats)
            }
            AccessoryFrame(width: 172, height: 76) {
                RectangularLifeView(stats: stats)
            }
        }
        AccessoryFrame(width: 260, height: 28) {
            InlineLifeView(stats: stats)
        }
        HStack(spacing: 20) {
            WidgetFrame(width: 170, height: 170) {
                SmallLifeView(stats: stats, unit: .days)
            }
            WidgetFrame(width: 170, height: 170) {
                SmallLifeView(stats: stats, unit: .seconds)
            }
        }
    }
}

extension WidgetGalleryView {
    /// «Последний раз» и «Письмо себе».
    @ViewBuilder
    func lastTimePage(stats: LifeStats) -> some View {
        let now = Date()
        let counters = snapshot.countersByUrgency(now: now)
        let letter = snapshot.letterForWidget(now: now)
        let sealed: FutureLetter? = {
            var data = AppData()
            data.profile = snapshot.profile
            data.letters = snapshot.letters
            return data.sealedLetters(now: now).last
        }()
        HStack(spacing: 20) {
            WidgetFrame(width: 170, height: 170) {
                if let counter = counters.first {
                    CounterSmallView(counter: counter, stats: stats, date: now)
                }
            }
            WidgetFrame(width: 170, height: 170) {
                LetterSmallView(letter: sealed, isWaiting: false, birthDate: stats.birthDate, date: now)
            }
        }
        WidgetFrame(width: 360, height: 170) {
            CountersMediumView(counters: counters, stats: stats, date: now)
        }
        HStack(spacing: 20) {
            WidgetFrame(width: 170, height: 170) {
                LetterSmallView(letter: letter?.letter, isWaiting: letter?.isWaiting ?? false, birthDate: stats.birthDate, date: now)
            }
            AccessoryFrame(width: 172, height: 76) {
                LetterRectangularView(letter: sealed, isWaiting: false, birthDate: stats.birthDate, date: now)
            }
        }
    }

    @ViewBuilder
    var travelPage: some View {
        WidgetFrame(width: 360, height: 170) {
            TravelMediumView(travel: snapshot.travel)
        }
        WidgetFrame(width: 360, height: 380) {
            TravelLargeView(travel: snapshot.travel)
        }
    }
}

/// Рамка виджета главного экрана: чёрный фон и стандартные отступы.
private struct WidgetFrame<Content: View>: View {
    let width: CGFloat
    let height: CGFloat
    @ViewBuilder let content: Content

    var body: some View {
        content
            .padding(16)
            .frame(width: width, height: height)
            .background(Color.black, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

/// Рамка виджета экрана блокировки.
private struct AccessoryFrame<Content: View>: View {
    let width: CGFloat
    let height: CGFloat
    @ViewBuilder let content: Content

    var body: some View {
        content
            .foregroundStyle(Color.white)
            .frame(width: width, height: height)
            .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

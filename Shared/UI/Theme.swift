import SwiftUI

/// Палитра: чёрный фон, белый текст и один тёплый акцент — цвет пламени свечи.
enum Theme {
    static let accent = Color(red: 0.949, green: 0.647, blue: 0.255)
    static let background = Color.black
    static let card = Color.white.opacity(0.06)
    static let cardStroke = Color.white.opacity(0.08)
    static let secondaryText = Color.white.opacity(0.6)
    static let tertiaryText = Color.white.opacity(0.38)
    static let lived = Color.white.opacity(0.85)
    static let future = Color.white.opacity(0.14)
}

extension View {
    /// Карточка в стиле приложения.
    func mementoCard(padding: CGFloat = 18) -> some View {
        self
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(Theme.cardStroke, lineWidth: 0.5)
            )
    }
}

/// Значок цели: флаг для стран или символ категории.
struct GoalIcon: View {
    let goal: Goal
    var size: CGFloat = 18

    var body: some View {
        Group {
            if let code = goal.countryCode, !code.isEmpty {
                Text(flagEmoji(for: code))
                    .font(.system(size: size + 2))
            } else {
                Image(systemName: goal.category.symbol)
                    .font(.system(size: size * 0.85, weight: .regular))
                    .foregroundStyle(goal.isCompleted ? Theme.accent : Color.white.opacity(0.75))
            }
        }
        .frame(width: size * 1.6, height: size * 1.6)
    }
}

/// Тонкая полоска прогресса.
struct ThinProgressBar: View {
    let progress: Double
    var height: CGFloat = 4
    var fill: Color = Theme.lived
    var track: Color = Theme.future

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule().fill(track)
                Capsule()
                    .fill(fill)
                    .frame(width: max(height, geometry.size.width * min(1, max(0, progress))))
            }
        }
        .frame(height: height)
    }
}

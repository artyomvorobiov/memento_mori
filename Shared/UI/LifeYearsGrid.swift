import SwiftUI

/// Жизнь в годах: каждая точка — год. Прожитые — белые, текущий заполняется
/// акцентным цветом по мере того, как год проходит, будущие — едва видны.
/// Используется во вкладке «Жизнь», в большом виджете и в карточке для шаринга.
struct LifeYearsGrid: View {
    let stats: LifeStats
    var columns: Int = 10
    var spacingRatio: CGFloat = 0.45
    var livedColor: Color = Theme.lived
    var futureColor: Color = Theme.future
    var accentColor: Color = Theme.accent

    var body: some View {
        Canvas { context, size in
            let total = stats.lifeExpectancy
            guard total > 0, columns > 0 else { return }
            let rows = (total + columns - 1) / columns
            let cellByWidth = size.width / (CGFloat(columns) + CGFloat(columns - 1) * spacingRatio)
            let cellByHeight = size.height / (CGFloat(rows) + CGFloat(max(0, rows - 1)) * spacingRatio)
            let cell = max(1, min(cellByWidth, cellByHeight))
            let gap = cell * spacingRatio
            let gridWidth = CGFloat(columns) * cell + CGFloat(columns - 1) * gap
            let gridHeight = CGFloat(rows) * cell + CGFloat(max(0, rows - 1)) * gap
            let origin = CGPoint(x: (size.width - gridWidth) / 2, y: (size.height - gridHeight) / 2)

            let currentYear = stats.isBeyondExpectancy ? total : stats.ageYears
            let yearFraction = min(1, max(0, stats.ageExact - Double(stats.ageYears)))

            for year in 0..<total {
                let row = year / columns
                let column = year % columns
                let rect = CGRect(
                    x: origin.x + CGFloat(column) * (cell + gap),
                    y: origin.y + CGFloat(row) * (cell + gap),
                    width: cell,
                    height: cell
                )
                let circle = Path(ellipseIn: rect)
                if year < currentYear {
                    context.fill(circle, with: .color(livedColor))
                } else if year == currentYear {
                    context.fill(circle, with: .color(futureColor))
                    let center = CGPoint(x: rect.midX, y: rect.midY)
                    var pie = Path()
                    pie.move(to: center)
                    pie.addArc(
                        center: center,
                        radius: cell / 2,
                        startAngle: .degrees(-90),
                        endAngle: .degrees(-90 + 360 * yearFraction),
                        clockwise: false
                    )
                    pie.closeSubpath()
                    context.fill(pie, with: .color(accentColor))
                    context.stroke(
                        Path(ellipseIn: rect.insetBy(dx: 0.5, dy: 0.5)),
                        with: .color(accentColor),
                        lineWidth: max(0.75, cell * 0.08)
                    )
                } else {
                    context.fill(circle, with: .color(futureColor))
                }
            }
        }
    }
}

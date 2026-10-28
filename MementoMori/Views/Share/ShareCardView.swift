import SwiftUI

/// Карточка для соцсетей: жизнь в годах и главные цифры.
struct ShareCardView: View {
    let stats: LifeStats
    let completedCount: Int
    let quote: Quote

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("MEMENTO MORI")
                .font(.system(size: 13, weight: .semibold))
                .tracking(4)
                .foregroundStyle(Theme.secondaryText)

            LifeYearsGrid(stats: stats)
                .frame(height: 170)

            HStack(alignment: .top) {
                metric(Format.percent(stats.progress, digits: 0), "жизни\nпрожито")
                Spacer()
                metric(
                    Format.number(stats.remainingWeeks),
                    RussianPlural.form(stats.remainingWeeks, "неделя\nвпереди", "недели\nвпереди", "недель\nвпереди")
                )
                Spacer()
                metric(
                    "\(completedCount)",
                    RussianPlural.form(completedCount, "цель\nвыполнена", "цели\nвыполнено", "целей\nвыполнено")
                )
            }

            Text("«\(quote.text)»")
                .font(.system(size: 13, design: .serif))
                .italic()
                .foregroundStyle(Color.white.opacity(0.8))
                .lineLimit(4)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)
        }
        .padding(28)
        .frame(width: 360, height: 450, alignment: .topLeading)
        .background(Color.black)
        .foregroundStyle(Color.white)
        .environment(\.colorScheme, .dark)
    }

    private func metric(_ value: String, _ caption: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(.system(size: 30, weight: .light))
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(caption)
                .font(.system(size: 11))
                .foregroundStyle(Theme.secondaryText)
        }
    }
}

struct ShareCardSheet: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        if let stats = model.stats() {
            RenderedShareSheet(title: "Карточка") {
                ShareCardView(
                    stats: stats,
                    completedCount: model.data.completedGoals.count,
                    quote: Quotes.ofTheDay()
                )
            }
        }
    }
}

/// Лист «Поделиться»: рисует карточку в картинку и отдаёт её системному меню.
struct RenderedShareSheet<Card: View>: View {
    let title: String
    @ViewBuilder let card: () -> Card
    @Environment(\.dismiss) private var dismiss
    @State private var image: UIImage?

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer(minLength: 0)
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 24, style: .continuous)
                                .strokeBorder(Theme.cardStroke, lineWidth: 1)
                        )
                        .padding(.horizontal, 32)
                    ShareLink(
                        item: Image(uiImage: image),
                        preview: SharePreview("Memento Mori", image: Image(uiImage: image))
                    ) {
                        AccentButtonLabel(title: "Поделиться", systemImage: "square.and.arrow.up")
                    }
                    .padding(.horizontal, 32)
                } else {
                    ProgressView()
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
            .background(Theme.background)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { dismiss() }
                }
            }
            .task {
                render()
            }
        }
    }

    @MainActor
    private func render() {
        let renderer = ImageRenderer(content: card())
        renderer.scale = 3
        image = renderer.uiImage
    }
}

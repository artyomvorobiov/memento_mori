import SwiftUI
import UIKit

/// Заголовок секции капителью.
struct SectionHeader: View {
    let title: String
    var trailing: String? = nil

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title.uppercased())
                .font(.caption.weight(.semibold))
                .tracking(1.5)
                .foregroundStyle(Theme.tertiaryText)
            Spacer(minLength: 8)
            if let trailing {
                Text(trailing)
                    .font(.caption)
                    .foregroundStyle(Theme.tertiaryText)
            }
        }
    }
}

/// Системное меню «Поделиться» для файлов.
struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

struct ShareableFile: Identifiable {
    let id = UUID()
    let url: URL
}

/// Мягко пульсирующая рамка — отмечает текущую неделю в сетке жизни.
struct PulsingMarker: View {
    let size: CGFloat
    @State private var isAnimating = false

    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
            .stroke(Theme.accent, lineWidth: 1.2)
            .frame(width: size, height: size)
            .scaleEffect(isAnimating ? 3 : 1)
            .opacity(isAnimating ? 0 : 0.9)
            .onAppear {
                withAnimation(.easeOut(duration: 1.8).repeatForever(autoreverses: false)) {
                    isAnimating = true
                }
            }
            .allowsHitTesting(false)
    }
}

/// Большая кнопка-капсула с акцентным фоном.
struct AccentButtonLabel: View {
    let title: String
    var systemImage: String? = nil

    var body: some View {
        HStack(spacing: 8) {
            if let systemImage {
                Image(systemName: systemImage)
            }
            Text(title)
        }
        .font(.headline)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Theme.accent, in: Capsule())
        .foregroundStyle(Color.black)
    }
}

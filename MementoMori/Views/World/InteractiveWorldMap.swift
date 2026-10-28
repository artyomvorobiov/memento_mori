import SwiftUI

/// Карта, которую можно приближать двумя пальцами, двигать и нажимать на страны.
struct InteractiveWorldMap: View {
    let visited: Set<String>
    let wished: Set<String>
    @Binding var selected: String?
    /// Запрос «покажи страну»: при изменении карта плавно перелетает к ней.
    var focusRequest: FocusRequest?
    /// Страна, по центру которой открывается карта.
    var initialCenter: String?

    struct FocusRequest: Equatable {
        let code: String
        let id = UUID()
    }

    @State private var zoom: CGFloat = 1
    @State private var baseZoom: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var baseOffset: CGSize = .zero
    @State private var isMagnifying = false
    @State private var viewSize: CGSize = .zero
    @State private var didCenterInitially = false

    private let maxZoom: CGFloat = 10
    private let map = WorldMapData.shared

    var body: some View {
        GeometryReader { geometry in
            WorldMapView(
                visited: visited,
                wished: wished,
                selected: selected,
                zoom: zoom,
                offset: offset,
                fill: .fillHeight
            )
            .contentShape(Rectangle())
            .onTapGesture { location in
                handleTap(at: location)
            }
            .gesture(magnifyGesture.simultaneously(with: dragGesture))
            .onAppear {
                viewSize = geometry.size
                centerInitially()
            }
            .onChange(of: geometry.size) { _, newSize in
                viewSize = newSize
                offset = clamped(offset, zoom: zoom)
                baseOffset = offset
                centerInitially()
            }
        }
        .clipped()
        .overlay(alignment: .bottomTrailing) {
            if zoom > 1.05 {
                Button {
                    reset()
                } label: {
                    Image(systemName: "arrow.down.right.and.arrow.up.left")
                        .font(.footnote.weight(.semibold))
                        .padding(10)
                        .background(.ultraThinMaterial, in: Circle())
                }
                .buttonStyle(.plain)
                .padding(12)
                .accessibilityLabel("Показать всю карту")
            }
        }
        .onChange(of: focusRequest) { _, request in
            if let request {
                focus(on: request.code)
            }
        }
        .sensoryFeedback(.selection, trigger: selected)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Карта мира")
    }

    // MARK: Жесты

    private var magnifyGesture: some Gesture {
        MagnifyGesture()
            .onChanged { value in
                isMagnifying = true
                let newZoom = min(maxZoom, max(1, baseZoom * value.magnification))
                let ratio = newZoom / zoom
                // Точка под пальцами остаётся на месте.
                let anchor = CGPoint(
                    x: value.startLocation.x - viewSize.width / 2,
                    y: value.startLocation.y - viewSize.height / 2
                )
                let moved = CGSize(
                    width: anchor.x - (anchor.x - offset.width) * ratio,
                    height: anchor.y - (anchor.y - offset.height) * ratio
                )
                zoom = newZoom
                offset = clamped(moved, zoom: newZoom)
            }
            .onEnded { _ in
                baseZoom = zoom
                baseOffset = offset
                isMagnifying = false
            }
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { value in
                guard !isMagnifying else { return }
                offset = clamped(
                    CGSize(
                        width: baseOffset.width + value.translation.width,
                        height: baseOffset.height + value.translation.height
                    ),
                    zoom: zoom
                )
            }
            .onEnded { _ in
                baseOffset = offset
            }
    }

    private func handleTap(at location: CGPoint) {
        let transform = WorldMapView.transform(map: map.size, in: viewSize, zoom: zoom, offset: offset, fill: .fillHeight)
        guard transform.a > 0 else { return }
        let point = location.applying(transform.inverted())
        let code = map.country(at: point, dotRadius: 16 / transform.a)
        withAnimation(.snappy) {
            selected = code == selected ? nil : code
        }
    }

    // MARK: Положение карты

    private func baseScale() -> CGFloat {
        WorldMapView.transform(map: map.size, in: viewSize, zoom: 1, offset: .zero, fill: .fillHeight).a
    }

    /// Сдвиг, при котором точка карты оказывается в центре.
    private func offset(centering point: CGPoint, zoom: CGFloat) -> CGSize {
        let scale = baseScale() * zoom
        return CGSize(
            width: -scale * (point.x - map.size.width / 2),
            height: -scale * (point.y - map.size.height / 2)
        )
    }

    private func clamped(_ value: CGSize, zoom: CGFloat) -> CGSize {
        let scale = baseScale() * zoom
        let maxX = max(0, (map.size.width * scale - viewSize.width) / 2)
        let maxY = max(0, (map.size.height * scale - viewSize.height) / 2)
        return CGSize(
            width: min(maxX, max(-maxX, value.width)),
            height: min(maxY, max(-maxY, value.height))
        )
    }

    private func centerInitially() {
        guard !didCenterInitially, viewSize.width > 0 else { return }
        didCenterInitially = true
        guard let code = initialCenter, let rect = map.bounds(of: code) else { return }
        offset = clamped(offset(centering: CGPoint(x: rect.midX, y: rect.midY), zoom: 1), zoom: 1)
        baseOffset = offset
    }

    private func focus(on code: String) {
        guard let rect = map.bounds(of: code), viewSize.width > 0 else { return }
        let base = baseScale()
        // Страна занимает примерно половину карты, но без сильного приближения.
        let desired = min(
            viewSize.width / max(rect.width * base, 1),
            viewSize.height / max(rect.height * base, 1)
        ) * 0.5
        let newZoom = min(5, max(1, max(zoom, desired)))
        let target = clamped(offset(centering: CGPoint(x: rect.midX, y: rect.midY), zoom: newZoom), zoom: newZoom)
        withAnimation(.snappy(duration: 0.6)) {
            zoom = newZoom
            offset = target
        }
        baseZoom = newZoom
        baseOffset = target
    }

    private func reset() {
        withAnimation(.snappy) {
            zoom = 1
            offset = .zero
        }
        baseZoom = 1
        baseOffset = .zero
        didCenterInitially = false
        centerInitially()
    }
}

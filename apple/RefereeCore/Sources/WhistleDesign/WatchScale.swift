import CoreGraphics

/// 경기 중 화면 크기 비율 (B2 스펙 §3). 40mm(폭 162pt)를 1로 보고 큰 화면만큼 키운다. 1보다 작아지지 않는다.
public struct WatchScale: Equatable, Sendable {
    public static let baseWidth: CGFloat = 162
    public var factor: CGFloat

    public init(width: CGFloat) {
        factor = max(1, width / Self.baseWidth)
    }

    public func scaled(_ base: CGFloat) -> CGFloat {
        base * factor
    }
}

import Foundation

/// 구독 상태. iPhone이 StoreKit으로 확인하고 워치로 보낸다.
public struct Entitlement: Codable, Equatable, Sendable {
    /// 구독 만료 시각 (구독이 없으면 nil)
    public var expiresAt: Double?
    /// 마지막으로 확인한 시각
    public var checkedAt: Double

    public init(expiresAt: Double?, checkedAt: Double) {
        self.expiresAt = expiresAt
        self.checkedAt = checkedAt
    }

    /// 오프라인 유예: 만료 시각 + 7일까지는 유료 기능을 연다 (스펙 §6)
    public static let gracePeriod: Double = 7 * 24 * 3600

    public static let none = Entitlement(expiresAt: nil, checkedAt: 0)

    public func isActive(at now: Double) -> Bool {
        guard let expiresAt else { return false }
        return now <= expiresAt + Self.gracePeriod
    }

    /// 두 출처(폰 전달, 워치 자체 확인) 중 더 늦은 만료를 쓴다.
    public func merged(with other: Entitlement) -> Entitlement {
        let expiry: Double? = switch (expiresAt, other.expiresAt) {
        case (nil, nil): nil
        case (let a?, nil): a
        case (nil, let b?): b
        case (let a?, let b?): max(a, b)
        }
        return Entitlement(expiresAt: expiry, checkedAt: max(checkedAt, other.checkedAt))
    }
}

import Foundation

/// 구독 상태. iPhone이 StoreKit으로 확인하고 워치로 보낸다.
public struct Entitlement: Codable, Equatable, Sendable {
    /// 구독 만료 시각 (구독이 없으면 nil)
    public var expiresAt: Double?
    /// 마지막으로 확인한 시각
    public var checkedAt: Double
    /// 환불·취소로 권한이 회수됐다 (다른 출처의 확인보다 우선한다)
    public var revoked: Bool

    public init(expiresAt: Double?, checkedAt: Double, revoked: Bool = false) {
        self.expiresAt = expiresAt
        self.checkedAt = checkedAt
        self.revoked = revoked
    }

    private enum CodingKeys: String, CodingKey {
        case expiresAt, checkedAt, revoked
    }

    /// M5 초기 형식에는 revoked가 없다 → false로 읽는다.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            expiresAt: try c.decodeIfPresent(Double.self, forKey: .expiresAt),
            checkedAt: try c.decode(Double.self, forKey: .checkedAt),
            revoked: try c.decodeIfPresent(Bool.self, forKey: .revoked) ?? false)
    }

    /// 오프라인 유예: 만료 시각 + 7일까지는 유료 기능을 연다 (스펙 §6)
    public static let gracePeriod: Double = 7 * 24 * 3600

    public static let none = Entitlement(expiresAt: nil, checkedAt: 0)

    /// 유료 기능이 열려 있는 마지막 시각 (만료 + 유예). 구독이 없거나 회수됐으면 nil.
    /// 워치는 이 시각에 맞춰 잠금을 다시 그린다.
    public var activeUntil: Double? {
        guard !revoked, let expiresAt else { return nil }
        return expiresAt + Self.gracePeriod
    }

    public func isActive(at now: Double) -> Bool {
        guard let activeUntil else { return false }
        return now <= activeUntil
    }

    /// 두 출처 중 더 늦은 만료를 쓴다.
    public func merged(with other: Entitlement) -> Entitlement {
        let expiry: Double? = switch (expiresAt, other.expiresAt) {
        case (nil, nil): nil
        case (let a?, nil): a
        case (nil, let b?): b
        case (let a?, let b?): max(a, b)
        }
        return Entitlement(expiresAt: expiry, checkedAt: max(checkedAt, other.checkedAt))
    }

    /// StoreKit 확인 결과
    public enum StoreCheck: Equatable, Sendable {
        /// 유효한 구독이 있다
        case active(expiresAt: Double)
        /// 상점에 연결해 확인했고 유효한 구독이 없다 (revoked: 환불·회수)
        case inactive(revoked: Bool)
        /// 오프라인 등으로 확인하지 못했다
        case unavailable
    }

    /// 확인 결과로 새 상태를 정한다. 확인하지 못했으면 저장해 둔 값을 그대로 쓴다
    /// (오프라인에서 갱신 기록을 못 받은 유료 사용자가 잠기지 않게, 스펙 §6·§8).
    public static func resolve(cached: Entitlement, check: StoreCheck, now: Double) -> Entitlement {
        switch check {
        case .active(let expiresAt): Entitlement(expiresAt: expiresAt, checkedAt: now)
        case .inactive(let revoked): Entitlement(expiresAt: nil, checkedAt: now, revoked: revoked)
        case .unavailable: cached
        }
    }

    /// 워치: 폰이 보낸 값과 워치 자체 StoreKit 확인을 합친다.
    /// 폰이 환불을 알렸으면 그것이 우선이고, 아니면 더 늦은 만료를 쓴다.
    public static func combine(phone: Entitlement?, watch: Entitlement?) -> Entitlement {
        if let phone, phone.revoked { return phone }
        switch (phone, watch) {
        case (nil, nil): return .none
        case (let p?, nil): return p
        case (nil, let w?): return w
        case (let p?, let w?): return p.merged(with: w)
        }
    }
}

/// 구독 상품 (iPhone 판매·워치 확인이 같은 목록을 쓴다)
public enum SubscriptionProducts {
    public static let ids = [
        // 연간 먼저: 결제 화면은 이 순서대로 보여 주고 첫 상품을 기본 선택한다 (Android 요금제 카드와 같게, 2026-11-03 critique)
        "com.aidenstudio.whistlepro.pro.yearly",
        "com.aidenstudio.whistlepro.pro.monthly",
    ]

    public static func contains(_ productID: String) -> Bool {
        ids.contains(productID)
    }
}

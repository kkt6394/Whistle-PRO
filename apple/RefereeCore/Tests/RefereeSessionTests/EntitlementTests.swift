import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

private let day = 24.0 * 3600

struct EntitlementTests {
    @Test func noSubscriptionIsInactive() {
        #expect(!Entitlement.none.isActive(at: 0))
    }

    @Test func activeBeforeExpiry() {
        #expect(Entitlement(expiresAt: 100 * day, checkedAt: 0).isActive(at: 50 * day))
    }

    @Test func activeWithinGraceAfterExpiry() {
        #expect(Entitlement(expiresAt: 100 * day, checkedAt: 0).isActive(at: 106 * day))
    }

    @Test func inactiveAfterGrace() {
        #expect(!Entitlement(expiresAt: 100 * day, checkedAt: 0).isActive(at: 107 * day + 1))
    }

    /// 홈 화면 리뷰: 워치가 이 시각에 맞춰 잠금을 다시 계산한다 (시각으로 계산해 관찰되지 않았다)
    @Test func activeUntilIsExpiryPlusGrace() {
        let e = Entitlement(expiresAt: 100 * day, checkedAt: 0)
        #expect(e.activeUntil == 107 * day)
        #expect(e.isActive(at: e.activeUntil!))
        #expect(!e.isActive(at: e.activeUntil! + 1))
        #expect(Entitlement.none.activeUntil == nil)
        #expect(Entitlement(expiresAt: 100 * day, checkedAt: 0, revoked: true).activeUntil == nil)
    }

    @Test func mergeKeepsLaterExpiry() {
        let phone = Entitlement(expiresAt: 200, checkedAt: 10)
        let watch = Entitlement(expiresAt: 300, checkedAt: 5)
        #expect(phone.merged(with: watch) == Entitlement(expiresAt: 300, checkedAt: 10))
        #expect(Entitlement.none.merged(with: phone) == Entitlement(expiresAt: 200, checkedAt: 10))
    }

    @Test func entitlementContextRoundTrips() throws {
        let preset = MatchPreset(id: UUID(), name: "A", setup: MatchSetup())
        let entitlement = Entitlement(expiresAt: 123, checkedAt: 45)
        let context = try SyncPayload.context(presets: [preset], entitlement: entitlement)
        #expect(SyncPayload.entitlement(from: context) == entitlement)
        #expect(SyncPayload.presets(from: context) == [preset])
        #expect(SyncPayload.entitlement(from: [:]) == nil)
    }

    @Test func oldRecordWithoutUnlockedIsUnlocked() throws {
        let record = MatchRecord(id: UUID(), createdAt: 0, setup: MatchSetup(), commands: [])
        var json = try #require(try JSONSerialization.jsonObject(with: JSONEncoder().encode(record)) as? [String: Any])
        json.removeValue(forKey: "unlocked")
        let decoded = try JSONDecoder().decode(MatchRecord.self, from: JSONSerialization.data(withJSONObject: json))
        #expect(decoded.unlocked)
    }

    @Test func lockedRecordRoundTrips() throws {
        let record = MatchRecord(id: UUID(), createdAt: 0, setup: MatchSetup(), commands: [], unlocked: false)
        #expect(try JSONDecoder().decode(MatchRecord.self, from: JSONEncoder().encode(record)) == record)
    }

    @MainActor
    @Test func matchKeepsUnlockAtKickoff() {
        let store = MemoryStore()
        let free = MatchSession.start(setup: MatchSetup(), store: store, now: { 0 }, unlocked: false)
        #expect(!free.record.unlocked)
        free.send(.startPeriod)
        #expect(store.active?.unlocked == false)

        let pro = MatchSession.start(setup: MatchSetup(), store: store, now: { 0 }, unlocked: true)
        pro.send(.startPeriod)
        let restored = MatchSession(record: pro.record, store: store, now: { 999_999 })
        #expect(restored.record.unlocked)
    }
}

/// 최종 리뷰: 오프라인 "확인 불가"와 "만료" 구분, 폰·워치 확인 합치기
struct EntitlementSourceTests {
    @Test func storeUnavailableKeepsCachedExpiry() {
        let cached = Entitlement(expiresAt: 30 * day, checkedAt: 1)
        #expect(Entitlement.resolve(cached: cached, check: .unavailable, now: 40 * day) == cached)
    }

    @Test func activeCheckSetsExpiry() {
        #expect(Entitlement.resolve(cached: .none, check: .active(expiresAt: 60 * day), now: 5)
                == Entitlement(expiresAt: 60 * day, checkedAt: 5))
    }

    @Test func confirmedInactiveClearsExpiryImmediately() {
        let cached = Entitlement(expiresAt: 30 * day, checkedAt: 1)
        let resolved = Entitlement.resolve(cached: cached, check: .inactive(revoked: false), now: 31 * day)
        #expect(resolved.expiresAt == nil)
        #expect(!resolved.isActive(at: 31 * day))
    }

    @Test func revokedIsNeverActive() {
        let resolved = Entitlement.resolve(cached: .none, check: .inactive(revoked: true), now: 0)
        #expect(resolved.revoked)
        #expect(!Entitlement(expiresAt: 99 * day, checkedAt: 0, revoked: true).isActive(at: 0))
    }

    @Test func watchRenewalBeatsStalePhoneValue() {
        let phone = Entitlement(expiresAt: 30 * day, checkedAt: 1)
        let watch = Entitlement(expiresAt: 60 * day, checkedAt: 2)
        #expect(Entitlement.combine(phone: phone, watch: watch).expiresAt == 60 * day)
    }

    @Test func phoneNilDoesNotWipeWatchPurchase() {
        let watch = Entitlement(expiresAt: 60 * day, checkedAt: 2)
        #expect(Entitlement.combine(phone: Entitlement(expiresAt: nil, checkedAt: 3), watch: watch).expiresAt == 60 * day)
        #expect(Entitlement.combine(phone: nil, watch: nil) == .none)
    }

    @Test func phoneRefundOverridesWatch() {
        let refund = Entitlement(expiresAt: nil, checkedAt: 3, revoked: true)
        let watch = Entitlement(expiresAt: 60 * day, checkedAt: 2)
        #expect(!Entitlement.combine(phone: refund, watch: watch).isActive(at: 10 * day))
    }

    @Test func oldEntitlementWithoutRevokedDecodes() throws {
        let decoded = try JSONDecoder().decode(Entitlement.self, from: Data(#"{"expiresAt": 5, "checkedAt": 1}"#.utf8))
        #expect(decoded == Entitlement(expiresAt: 5, checkedAt: 1))
        #expect(!decoded.revoked)
    }
}

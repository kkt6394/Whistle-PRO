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

package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DAY = 24.0 * 3600

// entitlementContextRoundTrips(SyncPayload)는 동기화와 함께 M6-3에서 옮긴다.
class EntitlementTests {
    @Test fun noSubscriptionIsInactive() {
        assertFalse(Entitlement.NONE.isActive(0.0))
    }

    @Test fun activeBeforeExpiry() {
        assertTrue(Entitlement(100 * DAY, 0.0).isActive(50 * DAY))
    }

    @Test fun activeWithinGraceAfterExpiry() {
        assertTrue(Entitlement(100 * DAY, 0.0).isActive(106 * DAY))
    }

    @Test fun inactiveAfterGrace() {
        assertFalse(Entitlement(100 * DAY, 0.0).isActive(107 * DAY + 1))
    }

    @Test fun mergeKeepsLaterExpiry() {
        val phone = Entitlement(200.0, 10.0)
        val watch = Entitlement(300.0, 5.0)
        assertEquals(Entitlement(300.0, 10.0), phone.merged(watch))
        assertEquals(Entitlement(200.0, 10.0), Entitlement.NONE.merged(phone))
    }

    @Test fun oldRecordWithoutUnlockedIsUnlocked() {
        val record = MatchRecord(UUID.randomUUID(), 0.0, MatchSetup(), emptyList())
        val json = JsonObject(record.toJson() - "unlocked")
        assertTrue(MatchRecord.fromJson(json).unlocked)
    }

    @Test fun lockedRecordRoundTrips() {
        val record = MatchRecord(UUID.randomUUID(), 0.0, MatchSetup(), emptyList(), unlocked = false)
        assertEquals(record, MatchRecord.fromJson(Json.parseToJsonElement(record.toJson().toString()).jsonObject))
    }

    @Test fun matchKeepsUnlockAtKickoff() {
        val store = MemoryStore()
        val free = MatchSession.start(MatchSetup(), store, now = { 0.0 }, unlocked = false)
        assertFalse(free.record.unlocked)
        free.send(CommandKind.StartPeriod)
        assertEquals(false, store.active?.unlocked)

        val pro = MatchSession.start(MatchSetup(), store, now = { 0.0 }, unlocked = true)
        pro.send(CommandKind.StartPeriod)
        val restored = MatchSession(pro.record, store, now = { 999_999.0 })
        assertTrue(restored.record.unlocked)
    }
}

/** 최종 리뷰: 오프라인 "확인 불가"와 "만료" 구분, 폰·워치 확인 합치기 */
class EntitlementSourceTests {
    @Test fun storeUnavailableKeepsCachedExpiry() {
        val cached = Entitlement(30 * DAY, 1.0)
        assertEquals(cached, Entitlement.resolve(cached, Entitlement.StoreCheck.Unavailable, 40 * DAY))
    }

    @Test fun activeCheckSetsExpiry() {
        assertEquals(Entitlement(60 * DAY, 5.0), Entitlement.resolve(Entitlement.NONE, Entitlement.StoreCheck.Active(60 * DAY), 5.0))
    }

    @Test fun confirmedInactiveClearsExpiryImmediately() {
        val cached = Entitlement(30 * DAY, 1.0)
        val resolved = Entitlement.resolve(cached, Entitlement.StoreCheck.Inactive(false), 31 * DAY)
        assertNull(resolved.expiresAt)
        assertFalse(resolved.isActive(31 * DAY))
    }

    @Test fun revokedIsNeverActive() {
        val resolved = Entitlement.resolve(Entitlement.NONE, Entitlement.StoreCheck.Inactive(true), 0.0)
        assertTrue(resolved.revoked)
        assertFalse(Entitlement(99 * DAY, 0.0, revoked = true).isActive(0.0))
    }

    @Test fun watchRenewalBeatsStalePhoneValue() {
        val phone = Entitlement(30 * DAY, 1.0)
        val watch = Entitlement(60 * DAY, 2.0)
        assertEquals(60 * DAY, Entitlement.combine(phone, watch).expiresAt)
    }

    @Test fun phoneNilDoesNotWipeWatchPurchase() {
        val watch = Entitlement(60 * DAY, 2.0)
        assertEquals(60 * DAY, Entitlement.combine(Entitlement(null, 3.0), watch).expiresAt)
        assertEquals(Entitlement.NONE, Entitlement.combine(null, null))
    }

    @Test fun phoneRefundOverridesWatch() {
        val refund = Entitlement(null, 3.0, revoked = true)
        val watch = Entitlement(60 * DAY, 2.0)
        assertFalse(Entitlement.combine(refund, watch).isActive(10 * DAY))
    }

    @Test fun oldEntitlementWithoutRevokedDecodes() {
        val decoded = Entitlement.fromJson(Json.parseToJsonElement("""{"expiresAt": 5, "checkedAt": 1}""").jsonObject)
        assertEquals(Entitlement(5.0, 1.0), decoded)
        assertFalse(decoded.revoked)
    }
}

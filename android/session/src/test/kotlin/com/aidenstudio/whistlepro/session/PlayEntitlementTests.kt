package com.aidenstudio.whistlepro.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Play 구매 목록 → 구독 판정 (확인 시각 + 청구 주기, 스펙 §4.4) */
class PlayEntitlementTests {
    private val monthly = "com.aidenstudio.whistlepro.pro.monthly"
    private val yearly = "com.aidenstudio.whistlepro.pro.yearly"
    private val now = 1_000_000.0
    private val periods = mapOf(monthly to PlayEntitlement.MONTH, yearly to PlayEntitlement.YEAR)

    private fun purchase(
        id: String,
        state: PlayPurchase.State = PlayPurchase.State.PURCHASED,
        acknowledged: Boolean = true,
        token: String = "t-$id",
    ) = PlayPurchase(listOf(id), state, acknowledged, token)

    private fun ok(vararg purchases: PlayPurchase) = PlayQuery.Ok(purchases.toList())

    @Test fun purchasedMonthlyIsActiveForThirtyDays() {
        assertEquals(
            Entitlement.StoreCheck.Active(now + 30.0 * 86_400),
            PlayEntitlement.check(ok(purchase(monthly)), periods, now),
        )
    }

    @Test fun yearlyWinsOverMonthly() {
        assertEquals(
            Entitlement.StoreCheck.Active(now + 365.0 * 86_400),
            PlayEntitlement.check(ok(purchase(monthly), purchase(yearly)), periods, now),
        )
    }

    @Test fun pendingIsNotActive() {
        val query = ok(purchase(monthly, state = PlayPurchase.State.PENDING, acknowledged = false))
        assertEquals(Entitlement.StoreCheck.Inactive(revoked = false), PlayEntitlement.check(query, periods, now))
        assertTrue(PlayEntitlement.hasPending(query))
        assertTrue(PlayEntitlement.toAcknowledge(query).isEmpty(), "대기 중인 구매는 인정하지 않는다")
        assertFalse(PlayEntitlement.hasPending(ok(purchase(monthly))))
        assertFalse(PlayEntitlement.hasPending(PlayQuery.Failed))
    }

    @Test fun otherProductsIgnored() {
        val query = ok(purchase("com.example.other"), purchase("com.example.other2", acknowledged = false))
        assertEquals(Entitlement.StoreCheck.Inactive(revoked = false), PlayEntitlement.check(query, periods, now))
        assertTrue(PlayEntitlement.toAcknowledge(query).isEmpty())
        assertFalse(PlayEntitlement.hasPending(ok(purchase("com.example.other", state = PlayPurchase.State.PENDING))))
    }

    @Test fun noPurchasesIsInactiveNotRevoked() {
        assertEquals(Entitlement.StoreCheck.Inactive(revoked = false), PlayEntitlement.check(ok(), periods, now))
        assertEquals(
            Entitlement.StoreCheck.Inactive(revoked = false),
            PlayEntitlement.check(ok(purchase(monthly, state = PlayPurchase.State.UNSPECIFIED)), periods, now),
        )
    }

    @Test fun failedQueryIsUnavailable() {
        assertEquals(Entitlement.StoreCheck.Unavailable, PlayEntitlement.check(PlayQuery.Failed, periods, now))
        assertTrue(PlayEntitlement.toAcknowledge(PlayQuery.Failed).isEmpty())
    }

    @Test fun periodFromBillingPeriodOrId() {
        assertEquals(PlayEntitlement.MONTH, PlayEntitlement.period(yearly, "P1M"))
        assertEquals(PlayEntitlement.YEAR, PlayEntitlement.period(monthly, "P1Y"))
        assertEquals(PlayEntitlement.YEAR, PlayEntitlement.period(yearly, null))
        assertEquals(PlayEntitlement.MONTH, PlayEntitlement.period(monthly, null))
        assertEquals(PlayEntitlement.YEAR, PlayEntitlement.period(yearly, "P3M"))
        assertEquals(PlayEntitlement.MONTH, PlayEntitlement.period(monthly, ""))
        // 주기 표에 없으면 상품 ID로 정한다
        assertEquals(
            Entitlement.StoreCheck.Active(now + PlayEntitlement.YEAR),
            PlayEntitlement.check(ok(purchase(yearly)), emptyMap(), now),
        )
    }

    @Test fun unacknowledgedTokensListed() {
        val query = ok(
            purchase(monthly, acknowledged = false, token = "a"),
            purchase(yearly, acknowledged = true, token = "b"),
            purchase(yearly, state = PlayPurchase.State.PENDING, acknowledged = false, token = "c"),
            purchase("com.example.other", acknowledged = false, token = "d"),
        )
        assertEquals(listOf("a"), PlayEntitlement.toAcknowledge(query))
    }

    /** Review Focus 4: 확인 없이 확인 시각 + 주기 + 7일이 지나면 잠긴다 */
    @Test fun activeExpiresAfterPeriodPlusGraceWithoutCheck() {
        val check = PlayEntitlement.check(ok(purchase(monthly)), periods, now)
        val entitlement = Entitlement.resolve(Entitlement.NONE, check, now)
        assertFalse(entitlement.revoked)
        val limit = now + 30.0 * 86_400 + 7.0 * 86_400
        assertTrue(entitlement.isActive(limit - 1))
        assertFalse(entitlement.isActive(limit + 1))
    }
}

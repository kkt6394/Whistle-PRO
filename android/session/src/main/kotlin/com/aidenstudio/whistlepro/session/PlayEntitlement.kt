package com.aidenstudio.whistlepro.session

/** Play Billing의 구매 하나 (라이브러리 `Purchase`에서 필요한 값만) */
data class PlayPurchase(
    val productIds: List<String>,
    val state: State,
    val acknowledged: Boolean,
    val token: String,
) {
    enum class State { PURCHASED, PENDING, UNSPECIFIED }
}

/** 구매 목록 조회 결과 */
sealed interface PlayQuery {
    data class Ok(val purchases: List<PlayPurchase>) : PlayQuery
    /** 연결·조회 실패 (오프라인 등) */
    data object Failed : PlayQuery
}

/**
 * Play 구매 목록 → 구독 판정 (스펙 §4.4 결정).
 * Play는 만료 시각을 주지 않으므로 활성 구독이 보이면 만료 = 확인 시각 + 그 상품의 청구 주기로 둔다.
 * 환불은 감지하지 않는다(revoked는 늘 false) — 다음 확인에서 목록에 없으면 무료가 된다.
 */
object PlayEntitlement {
    const val MONTH: Double = 30.0 * 86_400
    const val YEAR: Double = 365.0 * 86_400

    /** 청구 주기("P1M"·"P1Y"). 읽지 못하면 상품 ID로 정한다 (…yearly → 1년, 아니면 1달) */
    fun period(productId: String, billingPeriod: String?): Double = when (billingPeriod) {
        "P1M" -> MONTH
        "P1Y" -> YEAR
        else -> if (productId.endsWith("yearly")) YEAR else MONTH
    }

    /**
     * 우리 상품의 PURCHASED가 있으면 Active(확인 시각 + 그중 긴 주기), 없으면 Inactive(false),
     * 조회 실패면 Unavailable(캐시를 그대로 쓴다). PENDING은 활성이 아니다.
     */
    fun check(query: PlayQuery, periods: Map<String, Double>, now: Double): Entitlement.StoreCheck {
        val purchases = (query as? PlayQuery.Ok)?.purchases ?: return Entitlement.StoreCheck.Unavailable
        val longest = purchases
            .filter { it.state == PlayPurchase.State.PURCHASED }
            .flatMap { it.productIds }
            .filter(SubscriptionProducts::contains)
            .maxOfOrNull { periods[it] ?: period(it, null) }
            ?: return Entitlement.StoreCheck.Inactive(revoked = false)
        return Entitlement.StoreCheck.Active(now + longest)
    }

    /** 인정(acknowledge)해야 할 토큰: 우리 상품의 PURCHASED 중 아직 인정하지 않은 것 (3일 안에 안 하면 환불) */
    fun toAcknowledge(query: PlayQuery): List<String> =
        ours(query).filter { it.state == PlayPurchase.State.PURCHASED && !it.acknowledged }.map { it.token }

    /** 결제 대기(PENDING) 중인 우리 상품이 있다 (구독 화면 "결제 대기 중") */
    fun hasPending(query: PlayQuery): Boolean = ours(query).any { it.state == PlayPurchase.State.PENDING }

    private fun ours(query: PlayQuery): List<PlayPurchase> =
        (query as? PlayQuery.Ok)?.purchases.orEmpty().filter { p -> p.productIds.any(SubscriptionProducts::contains) }
}

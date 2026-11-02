package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.double
import com.aidenstudio.whistlepro.core.optionalBoolean
import com.aidenstudio.whistlepro.core.optionalDouble
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 구독 상태. 폰이 상점으로 확인하고 워치로 보낸다. */
data class Entitlement(
    /** 구독 만료 시각 (구독이 없으면 null) */
    val expiresAt: Double?,
    /** 마지막으로 확인한 시각 */
    val checkedAt: Double,
    /** 환불·취소로 권한이 회수됐다 (다른 출처의 확인보다 우선한다) */
    val revoked: Boolean = false,
) {
    /**
     * 유료 기능이 열려 있는 마지막 시각 (만료 + 유예). 구독이 없거나 회수됐으면 null.
     * 워치는 이 시각에 맞춰 잠금을 다시 그린다.
     */
    val activeUntil: Double?
        get() = if (revoked || expiresAt == null) null else expiresAt + GRACE_PERIOD

    fun isActive(now: Double): Boolean {
        val until = activeUntil ?: return false
        return now <= until
    }

    /** 두 출처 중 더 늦은 만료를 쓴다. */
    fun merged(other: Entitlement): Entitlement {
        val expiry = when {
            expiresAt == null -> other.expiresAt
            other.expiresAt == null -> expiresAt
            else -> maxOf(expiresAt, other.expiresAt)
        }
        return Entitlement(expiry, maxOf(checkedAt, other.checkedAt))
    }

    fun toJson(): JsonObject = buildJsonObject {
        expiresAt?.let { put("expiresAt", JsonPrimitive(it)) }
        put("checkedAt", JsonPrimitive(checkedAt))
        put("revoked", JsonPrimitive(revoked))
    }

    /** 상점 확인 결과 */
    sealed interface StoreCheck {
        /** 유효한 구독이 있다 */
        data class Active(val expiresAt: Double) : StoreCheck
        /** 상점에 연결해 확인했고 유효한 구독이 없다 (revoked: 환불·회수) */
        data class Inactive(val revoked: Boolean) : StoreCheck
        /** 오프라인 등으로 확인하지 못했다 */
        data object Unavailable : StoreCheck
    }

    companion object {
        /** 오프라인 유예: 만료 시각 + 7일까지는 유료 기능을 연다 (스펙 §6) */
        const val GRACE_PERIOD: Double = 7.0 * 24 * 3600

        val NONE = Entitlement(null, 0.0)

        /** 초기 형식에는 revoked가 없다 → false로 읽는다. */
        fun fromJson(json: JsonObject) = Entitlement(
            expiresAt = json.optionalDouble("expiresAt"),
            checkedAt = json.double("checkedAt"),
            revoked = json.optionalBoolean("revoked") ?: false,
        )

        /**
         * 확인 결과로 새 상태를 정한다. 확인하지 못했으면 저장해 둔 값을 그대로 쓴다
         * (오프라인에서 갱신 기록을 못 받은 유료 사용자가 잠기지 않게, 스펙 §6·§8).
         */
        fun resolve(cached: Entitlement, check: StoreCheck, now: Double): Entitlement = when (check) {
            is StoreCheck.Active -> Entitlement(check.expiresAt, now)
            is StoreCheck.Inactive -> Entitlement(null, now, check.revoked)
            StoreCheck.Unavailable -> cached
        }

        /**
         * 워치: 폰이 보낸 값과 워치 자체 상점 확인을 합친다.
         * 폰이 환불을 알렸으면 그것이 우선이고, 아니면 더 늦은 만료를 쓴다.
         */
        fun combine(phone: Entitlement?, watch: Entitlement?): Entitlement {
            if (phone != null && phone.revoked) return phone
            return when {
                phone == null && watch == null -> NONE
                watch == null -> phone!!
                phone == null -> watch
                else -> phone.merged(watch)
            }
        }
    }
}

/** 구독 상품 (폰 판매·워치 확인이 같은 목록을 쓴다) */
object SubscriptionProducts {
    val ids = listOf(
        // 연간 먼저: 결제 화면은 이 순서대로 보여 주고 첫 상품을 기본 선택한다 (Android 요금제 카드와 같게, 2026-11-03 critique)
        "com.aidenstudio.whistlepro.pro.yearly",
        "com.aidenstudio.whistlepro.pro.monthly",
    )

    fun contains(productId: String) = productId in ids
}

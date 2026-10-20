package com.aidenstudio.whistlepro.session

import java.util.UUID

// Swift DeliveryRetry.swift를 옮긴 것. Swift는 mutating struct라 여기서는 상태를 가진 클래스다 (한 스레드에서만 쓴다).
// Swift waitUntil(timeout:poll:)은 옮기지 않는다: 안드로이드에서는 코루틴 withTimeoutOrNull이 대신한다.

/**
 * 앱이 화면에 나타날 때 확인받지 못한 경기를 다시 보내는 간격 제한.
 * 경기 중 손목을 들 때마다(앱 활성화) 다시 보내지 않게 한다.
 */
class ResendThrottle(val interval: Double) {
    private var last: Double? = null

    fun allows(now: Double): Boolean {
        val last = last
        if (last != null && now - last < interval) return false
        this.last = now
        return true
    }
}

/**
 * 경기마다 마지막으로 보낸 시각: 같은 경기를 interval 안에 다시 보내지 않는다.
 * 폰 앱이 꺼져 있으면 전송은 끝나도(폰에 도착) 확인이 오지 않아, 워치가 화면에 나타날 때마다 다시 보냈다 (경기 확인 누락 후속)
 */
class MatchSendThrottle(val interval: Double) {
    private val lastSent = mutableMapOf<UUID, Double>()

    fun allows(matchID: UUID, now: Double): Boolean {
        val last = lastSent[matchID]
        if (last != null && now - last < interval) return false
        lastSent[matchID] = now
        return true
    }

    /** 전송이 실패했으면 바로 다시 보낼 수 있게 */
    fun failed(matchID: UUID) {
        lastSent.remove(matchID)
    }
}

/**
 * 워치가 폰에 "이 경기들 받았니?"를 묻는 정리 경로의 판단 (경기 확인 누락 후속).
 * 답을 기다리는 동안은 다시 묻지 않고, 답을 받은 뒤 interval 안에는 다시 묻지 않는다 (reachable이 깜빡여도 겹치지 않게).
 * 실패하면 다음 기회(활성화·reachable)에 바로 다시 묻는다.
 */
class ReceiptCheck(val interval: Double) {
    var inFlight = false
        private set
    private var lastAnswered: Double? = null

    /**
     * 지금 물을 수 있는지 (답 대기 중이 아니고 간격이 지났다). 상태를 바꾸지 않는다.
     * 보관함을 읽기 전에 먼저 본다: reachable이 깜빡일 때마다 디스크를 읽지 않게
     */
    fun canAsk(now: Double): Boolean {
        if (inFlight) return false
        val last = lastAnswered
        if (last != null && now - last < interval) return false
        return true
    }

    /**
     * 남은 경기를 다시 보낼 때 답을 기다릴지: 폰에 닿고 지금 묻거나(물을 수 있다) 이미 답을 기다리는 중이면 답(또는 실패) 뒤에 보낸다.
     * 폰이 이미 받은 경기를 다시 보내지 않게. 닿지 않거나 간격 안이라 묻지 않으면 바로 보낸다.
     */
    fun sendsAfterAnswer(reachable: Boolean, now: Double): Boolean = reachable && (inFlight || canAsk(now))

    /** 물을 경기 목록. 묻지 않을 때(답 대기 중, 간격 안, 남은 경기 없음)는 null. */
    fun begin(pending: List<UUID>, now: Double): List<UUID>? {
        if (!canAsk(now) || pending.isEmpty()) return null
        inFlight = true
        return pending.take(MAX_IDS)
    }

    /** 답을 받았다: 물은 경기 중 폰이 받았다고 한 경기 (묻지 않은 ID는 무시한다) */
    fun answered(asked: List<UUID>, received: List<UUID>, now: Double): List<UUID> {
        inFlight = false
        lastAnswered = now
        return toDelete(asked, received)
    }

    /** 보내지 못했거나 답이 오지 않았다 */
    fun failed() {
        inFlight = false
    }

    companion object {
        /** 한 번에 묻는 경기 수 상한: 메시지 한도 안에 들게 (경기 ID 하나 약 40바이트) */
        const val MAX_IDS = 500

        /**
         * 답을 받고 지울 경기: 물은 경기 중 폰이 받았다고 한 것만.
         * 묻고 난 뒤 보관된 경기는 답에 들어 있어도 지우지 않는다 (아직 보내지 않았을 수 있다)
         */
        fun toDelete(asked: List<UUID>, received: List<UUID>): List<UUID> {
            val receivedSet = received.toSet()
            return asked.filter { it in receivedSet }
        }
    }
}

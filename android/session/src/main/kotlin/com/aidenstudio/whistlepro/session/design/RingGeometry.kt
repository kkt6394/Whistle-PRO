package com.aidenstudio.whistlepro.session.design

/** 진행 테두리의 채움. 값은 테두리 경로 전체를 0..1로 본 위치 (B1 스펙 §2). */
data class RingFill(
    /** 규정 시간(또는 남은 휴식) 구간의 끝 (0부터) */
    val main: Double,
    /** 추가시간 구간. 규정 시간을 넘었을 때만 있다. */
    val added: ClosedFloatingPointRange<Double>?,
)

/** 진행 테두리 채움 비율 (B1 스펙 §2). 경로 전체 = 규정 시간 × 1.1. 위치는 `BorderTrack.point`에 넘긴다. */
object RingGeometry {
    /** 테두리는 "규정 시간 × 1.1"을 나타낸다. 규정 시간이 차지하는 몫 */
    const val REGULATION_SHARE = 1 / 1.1

    /** 기간 진행. 추가시간은 규정의 10%가 지나면 테두리 끝에서 멈춘다. */
    fun period(elapsed: Long, length: Long): RingFill {
        if (length <= 0 || elapsed <= 0) return RingFill(0.0, null)
        val regular = minOf(elapsed, length).toDouble() / length
        val main = regular * REGULATION_SHARE
        if (elapsed <= length) return RingFill(main, null)
        val cap = length * 0.1
        val over = minOf((elapsed - length).toDouble(), cap) / cap
        return RingFill(main, REGULATION_SHARE..(REGULATION_SHARE + over * (1 - REGULATION_SHARE)))
    }

    /** 휴식: 남은 시간만큼 찬 테두리 (끝나 갈수록 줄어든다) */
    fun rest(remaining: Long, total: Long): RingFill {
        if (total <= 0) return RingFill(0.0, null)
        return RingFill((remaining.toDouble() / total).coerceIn(0.0, 1.0), null)
    }
}

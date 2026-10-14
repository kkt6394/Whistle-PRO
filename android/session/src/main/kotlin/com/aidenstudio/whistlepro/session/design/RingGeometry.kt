package com.aidenstudio.whistlepro.session.design

/** 열린 링의 채움. 값은 링 전체(290°)를 0..1로 본 위치. */
data class RingFill(
    /** 규정 시간(또는 남은 휴식) 구간의 끝 (0부터) */
    val main: Double,
    /** 추가시간 구간. 규정 시간을 넘었을 때만 있다. */
    val added: ClosedFloatingPointRange<Double>?,
)

/** 스펙 §4.1 열린 진행 링. 아래 70°를 비우고, 왼쪽 아래에서 시작해 위를 지나 오른쪽 아래에서 끝난다. */
object RingGeometry {
    const val SWEEP_DEGREES = 290.0
    /** 0°=3시 방향, 시계 방향으로 증가하는 각도로 링이 시작하는 곳 (Compose drawArc와 같은 기준) */
    const val START_DEGREES = 125.0
    /** 링은 "규정 시간 × 1.1"을 나타낸다. 규정 시간이 차지하는 몫 */
    const val REGULATION_SHARE = 1 / 1.1

    /** 링 위 위치(0..1)의 각도 */
    fun angle(fraction: Double): Double = START_DEGREES + fraction * SWEEP_DEGREES

    /** 기간 진행. 추가시간은 규정의 10%가 지나면 링 끝에서 멈춘다. */
    fun period(elapsed: Long, length: Long): RingFill {
        if (length <= 0 || elapsed <= 0) return RingFill(0.0, null)
        val regular = minOf(elapsed, length).toDouble() / length
        val main = regular * REGULATION_SHARE
        if (elapsed <= length) return RingFill(main, null)
        val cap = length * 0.1
        val over = minOf((elapsed - length).toDouble(), cap) / cap
        return RingFill(main, REGULATION_SHARE..(REGULATION_SHARE + over * (1 - REGULATION_SHARE)))
    }

    /** 휴식: 남은 시간만큼 찬 링 (끝나 갈수록 줄어든다) */
    fun rest(remaining: Long, total: Long): RingFill {
        if (total <= 0) return RingFill(0.0, null)
        return RingFill((remaining.toDouble() / total).coerceIn(0.0, 1.0), null)
    }
}

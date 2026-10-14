package com.aidenstudio.whistlepro.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * 초 단위 값을 Long으로 바꾼다. 손상된 로그의 무한대·NaN·아주 큰 값에 대비해 ±1조 초로 제한한다.
 * Swift Int(64비트)와 같은 값을 내려고 Long을 쓴다 (Kotlin Int로는 넘친다).
 */
fun wholeSeconds(value: Double, roundUp: Boolean = false): Long {
    if (!value.isFinite()) return 0
    val clamped = value.coerceIn(-1e12, 1e12)
    return (if (roundUp) ceil(clamped) else floor(clamped)).toLong()
}

/**
 * Swift max/min과 같은 NaN 처리 (`y >= x ? y : x`, `y < x ? y : x`).
 * kotlin.math.max/min은 NaN이 하나라도 있으면 NaN을 돌려줘 Swift와 결과가 달라진다.
 */
internal fun swiftMax(x: Double, y: Double): Double = if (y >= x) y else x

internal fun swiftMin(x: Double, y: Double): Double = if (y < x) y else x

data class ClockReading(
    val period: PeriodRef,
    /** 기간 시작 후 경과(초, 내림) */
    val elapsed: Long,
    /** 누적 경기 시각 = offset + elapsed (추가시간 포함, 클램프하지 않음) */
    val matchSecond: Long,
    /** 규정 시간까지 남은 시간 (0 이상) */
    val remaining: Long,
    /** 규정 시간 초과분 (0 이상) */
    val addedTime: Long,
    /** 이 기간의 누적 중단 시간 = 권장 추가시간 */
    val stoppageTotal: Long,
    val stoppageRunning: Boolean,
)

internal fun PeriodRecord.elapsedSeconds(now: Double): Double = swiftMax(0.0, (endedAt ?: now) - startedAt)

internal fun PeriodRecord.stoppageSeconds(now: Double): Double =
    stoppages.fold(0.0) { total, s -> total + swiftMax(0.0, (s.end ?: now) - s.start) }

/** 마지막으로 시작한 기간의 시계. 기간이 끝났으면 종료 시점에 멈춘 값이다. */
fun MatchState.clock(now: Double): ClockReading? {
    val p = periods.lastOrNull() ?: return null
    val elapsed = wholeSeconds(p.elapsedSeconds(now))
    val nominal = p.nominalLength.toLong()
    return ClockReading(
        period = p.ref,
        elapsed = elapsed,
        matchSecond = p.offset + elapsed,
        remaining = max(0, nominal - elapsed),
        addedTime = max(0, elapsed - nominal),
        stoppageTotal = wholeSeconds(p.stoppageSeconds(now)),
        stoppageRunning = p.stoppages.lastOrNull()?.let { it.end == null } ?: false,
    )
}

/** 기간별 누적 중단 시간(초) */
fun MatchState.stoppageTotals(now: Double): List<Long> = periods.map { wholeSeconds(it.stoppageSeconds(now)) }

/** 휴식 중일 때 남은 휴식 시간(초, 올림, 0 이상). 휴식이 아니면 null. */
fun MatchState.intervalRemaining(now: Double): Long? {
    val p = phase as? Phase.Interval ?: return null
    val end = periods.lastOrNull()?.endedAt ?: return null
    val left = intervalLength(p.after) - swiftMax(0.0, now - end)
    return max(0, wholeSeconds(left, roundUp = true))
}

internal fun MatchState.toggleStoppage(t: Double): MatchState {
    if (!phase.isPeriod) fail(EngineError.INVALID_IN_PHASE)
    return copy(periods = periods.updateLast { p ->
        val open = p.stoppages.lastOrNull()?.takeIf { it.end == null }
        p.copy(stoppages = if (open != null) p.stoppages.dropLast(1) + open.copy(end = t) else p.stoppages + StoppageInterval(t, null))
    })
}

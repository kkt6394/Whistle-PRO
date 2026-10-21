package com.aidenstudio.whistlepro.session.design

import kotlin.math.max
import kotlin.math.min

/**
 * 원형 화면 홈의 세로 배분: [새 경기] 동그라미 · 안내 칸 · [지난 경기] 알약.
 * 우선순위: 동그라미 최소 지름 > 안내 칸 최소 한 줄(그 안에서 스크롤) > 남는 높이는 동그라미.
 * 그래도 모자라면 [지난 경기]가 44dp까지 줄어든다.
 */
object HomeLayout {
    /** 간격 */
    const val SPACING = 6.0
    /** 안내 한 줄의 높이 (12sp 한 줄 + 사이), 글자 배율을 곱해 쓴다 */
    const val NOTICE_LINE = 16.0
    /** 동그라미 최소 지름 = 화면 지름의 35% (알약보다 작아지면 안 된다) */
    const val MIN_CIRCLE_RATIO = 0.35

    fun minCircle(diameter: Double): Double = max(RoundFit.MIN_CIRCLE, diameter * MIN_CIRCLE_RATIO)

    /** [slot]: 동그라미가 받은 세로 칸, [circle]: 동그라미 지름, [notice]: 안내 칸 높이(없으면 0), [past]: 알약 높이 */
    data class Result(val slot: Double, val circle: Double, val notice: Double, val past: Double)

    fun compute(diameter: Double, fontScale: Double, notices: Int): Result {
        val top = diameter * RoundFit.TOP_MARGIN
        val bottom = diameter * RoundFit.BOTTOM_MARGIN
        val available = diameter - top - bottom - FillLayout.SLACK
        val pastBase = max(44.0, 44.0 * fontScale)
        val circleMin = minCircle(diameter)
        val line = NOTICE_LINE * fontScale
        val gaps = SPACING * (if (notices > 0) 2 else 1)
        val rest = available - gaps - pastBase
        val noticeFull = line * notices
        val notice: Double
        val slot: Double
        if (rest >= circleMin + noticeFull) {
            notice = noticeFull
            slot = rest - noticeFull
        } else {
            // 모자라면 안내 칸이 한 줄까지 줄고(칸 안 스크롤), 그래도 모자라면 알약이 44까지 줄어든다
            notice = if (notices > 0) (rest - circleMin).coerceIn(line, noticeFull) else 0.0
            slot = circleMin
        }
        val past = max(44.0, min(pastBase, available - gaps - slot - notice))
        return Result(slot, RoundFit.circleDiameter(diameter, top, slot, circleMin), notice, past)
    }
}

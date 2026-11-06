package com.aidenstudio.whistlepro.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 회전 제스처 판정기: 애플 CrownGestureTests와 같은 사례·같은 값 (2026-11-06 코틀린 이식). */
class RotaryGestureTests {
    /** (delta, 시각) 목록을 차례로 넣어 나온 이벤트를 모은다. 마지막엔 손이 멈춘 것으로 보고 flush한 결과를 덧붙인다 */
    private fun run(samples: List<Pair<Double, Double>>, flushAfter: Double = 0.3): List<RotaryGestureEvent?> {
        val detector = RotaryGestureDetector()
        val events = samples.map { detector.feed(it.first, it.second) }.toMutableList()
        events.add(detector.flush((samples.lastOrNull()?.second ?: 0.0) + flushAfter))
        return events
    }

    private fun steps(delta: Double, count: Int, from: Double = 0.0, gap: Double = 0.05): List<Pair<Double, Double>> =
        (0 until count).map { delta to from + it * gap }

    @Test fun halfTurnClockwiseIsPrimary() {
        val events = run(steps(0.1, 5))
        assertTrue(events.take(5).all { it == null })
        assertEquals(RotaryGestureEvent(PhysicalInput.PRIMARY, 0.0), events[5])
    }

    @Test fun halfTurnCounterClockwiseIsSecondary() {
        val events = run(steps(-0.1, 5))
        assertEquals(RotaryGestureEvent(PhysicalInput.SECONDARY, 0.0), events[5])
    }

    /** 멈춘 지 strokeEnd가 안 됐으면 아직 실행하지 않는다 */
    @Test fun halfTurnWaitsForStrokeEnd() {
        val d = RotaryGestureDetector()
        for ((x, t) in steps(0.1, 5)) d.feed(x, t)
        assertTrue(d.hasPending)
        assertNull(d.flush(0.2 + 0.1))
        assertEquals(RotaryGestureEvent(PhysicalInput.PRIMARY, 0.0), d.flush(0.2 + 0.16))
        assertFalse(d.hasPending)
    }

    /** 반 바퀴 뒤 멈추지 않고 다른 방향 표본이 오면, 앞 획을 먼저 실행하고 새 획을 시작한다 */
    @Test fun nextStrokeAfterPauseFlushesPrevious() {
        val d = RotaryGestureDetector()
        for ((x, t) in steps(0.1, 5)) d.feed(x, t)
        assertEquals(RotaryGestureEvent(PhysicalInput.PRIMARY, 0.0), d.feed(-0.1, 0.2 + 0.3))
    }

    /** 획이 반 바퀴를 넘는 큰 흔들기(SE 실기기 76~186도)도 중단이 아니라 실행 취소 */
    @Test fun bigWiggleIsUndoNotPrimary() {
        val samples = mutableListOf<Pair<Double, Double>>()
        var t = 0.0
        for ((sign, size) in listOf(1.0 to 0.52, -1.0 to 0.21, 1.0 to 0.43, -1.0 to 0.35)) {
            val n = (size / 0.05).toInt()
            repeat(n) { samples.add(sign * 0.05 to t); t += 0.02 }
            t += 0.08
        }
        val fired = run(samples).filterNotNull()
        assertEquals(1, fired.size)
        assertEquals(PhysicalInput.UNDO, fired.first().input)
    }

    /** 0.1바퀴(36도)는 기준(0.15) 미만 → 무시 */
    @Test fun smallTurnIsIgnored() {
        assertTrue(run(steps(0.1, 1)).all { it == null })
    }

    /** 0.2바퀴(72도)는 기준을 넘겨 획이 끝난 뒤 실행 */
    @Test fun smallCommandTurnIsEnough() {
        val events = run(steps(0.1, 2))
        assertTrue(events.take(2).all { it == null })
        assertEquals(RotaryGestureEvent(PhysicalInput.PRIMARY, 0.0), events[2])
    }

    /** 0.1바퀴 돌리고 0.5초 쉬면 처음부터 */
    @Test fun pauseResetsTurn() {
        assertTrue(run(listOf(0.1 to 0.0, 0.1 to 0.6)).all { it == null })
    }

    /** 3/4바퀴를 넘기면 멈추길 기다리지 않고 바로 실행, 시각은 획 시작 */
    @Test fun threeQuarterTurnFiresImmediately() {
        val d = RotaryGestureDetector()
        val events = steps(0.1, 8).map { d.feed(it.first, it.second) }
        assertTrue(events.take(7).all { it == null })
        assertEquals(RotaryGestureEvent(PhysicalInput.PRIMARY, 0.0), events[7])
        assertFalse(d.hasPending)
    }

    @Test fun continuousSpinFiresOnce() {
        val fired = run(steps(0.1, 10)).filterNotNull()
        assertEquals(1, fired.size)
        assertEquals(RotaryGestureEvent(PhysicalInput.PRIMARY, 0.0), fired.first())
    }

    @Test fun wiggleTwiceIsUndo() {
        val deltas = listOf(0.05, 0.05, -0.05, -0.05, 0.05, 0.05, -0.05, -0.05)
        val fired = run(deltas.mapIndexed { i, x -> x to i * 0.05 }).filterNotNull()
        assertEquals(1, fired.size)
        assertEquals(PhysicalInput.UNDO, fired.first().input)
    }

    @Test fun slowWiggleIsNotUndo() {
        val deltas = listOf(0.05, 0.05, -0.05, -0.05, 0.05, 0.05, -0.05, -0.05)
        assertTrue(run(deltas.mapIndexed { i, x -> x to i * 0.5 }).all { it == null })
    }

    /** 작은 흔들기(획 0.04바퀴, 약 14도)도 실행 취소 */
    @Test fun smallWiggleIsUndo() {
        val deltas = listOf(0.02, 0.02, -0.02, -0.02, 0.02, 0.02, -0.02, -0.02)
        val fired = run(deltas.mapIndexed { i, x -> x to i * 0.08 }).filterNotNull()
        assertEquals(1, fired.size)
        assertEquals(PhysicalInput.UNDO, fired.first().input)
    }

    /** 도 단위 설정 → 바퀴. 기본 54도 */
    @Test fun configFromDegrees() {
        assertEquals(54, RotaryGestureConfig.DEFAULT_COMMAND_DEGREES)
        assertEquals(0.25, RotaryGestureConfig.ofDegrees(90).commandTurn)
        assertEquals(RotaryGestureConfig().commandTurn, RotaryGestureConfig.ofDegrees(54).commandTurn)
    }

    /** 선택지는 18~180도 6도 간격, 범위 밖은 가까운 끝으로 */
    @Test fun degreeOptionsAndClamp() {
        val o = RotaryGestureConfig.COMMAND_DEGREE_OPTIONS
        assertEquals(18, o.first())
        assertEquals(180, o.last())
        assertTrue(o.contains(54))
        assertTrue(o.all { it % 6 == 0 })
        assertEquals(18, RotaryGestureConfig.clampCommandDegrees(0))
        assertEquals(180, RotaryGestureConfig.clampCommandDegrees(999))
        assertEquals(60, RotaryGestureConfig.clampCommandDegrees(60))
    }

    /** 90도로 두면 72도(0.2바퀴)는 무시하고 108도(0.3바퀴)는 실행 */
    @Test fun higherDegreesNeedMoreTurn() {
        val d = RotaryGestureDetector(RotaryGestureConfig.ofDegrees(90))
        for ((x, t) in steps(0.1, 2)) d.feed(x, t)
        assertNull(d.flush(0.05 + 0.3))
        d.feed(0.1, 0.6)
        assertNull(d.flush(0.6 + 0.3), "쉼 뒤 새 획 0.1은 아직 기준 미만")
        val fresh = RotaryGestureDetector(RotaryGestureConfig.ofDegrees(90))
        for ((x, t) in steps(0.1, 3)) fresh.feed(x, t)
        assertEquals(RotaryGestureEvent(PhysicalInput.PRIMARY, 0.0), fresh.flush(0.1 + 0.3))
    }

    @Test fun jitterIsNotWiggle() {
        val samples = (0 until 40).map { (if (it % 2 == 0) 0.02 else -0.02) to it * 0.02 }
        assertTrue(run(samples).all { it == null })
    }

    /** 설정 줄 글자: 기본은 standard, 작으면 sensitive, 크면 dull */
    @Test fun commandFeelComparedToDefault() {
        assertEquals(CommandFeel.STANDARD, RotaryGestureConfig.commandFeel(54))
        assertEquals(CommandFeel.SENSITIVE, RotaryGestureConfig.commandFeel(48))
        assertEquals(CommandFeel.SENSITIVE, RotaryGestureConfig.commandFeel(18))
        assertEquals(CommandFeel.DULL, RotaryGestureConfig.commandFeel(60))
        assertEquals(CommandFeel.DULL, RotaryGestureConfig.commandFeel(180))
    }

    /** 기다리던 입력만 버린다: flush·이어진 표본이 그 획을 실행하지 않고, 실행 뒤 쿨다운은 남는다 (2026-11-06 검토) */
    @Test fun discardPendingDropsStrokeButKeepsCooldown() {
        val d = RotaryGestureDetector()
        for ((x, t) in steps(0.1, 2)) d.feed(x, t)
        assertTrue(d.hasPending)
        d.discardPending()
        assertFalse(d.hasPending)
        assertNull(d.flush(1.0))
        // 같은 방향으로 이어 돌려도 버린 획이 되살아나지 않는다 (작은 표본 하나)
        assertNull(d.feed(0.05, 0.1))
        assertNull(d.flush(0.5))
        // 실행 뒤(쿨다운 중) 버려도 쿨다운은 그대로
        val e = RotaryGestureDetector()
        for ((x, t) in steps(0.1, 2)) e.feed(x, t)
        assertEquals(RotaryGestureEvent(PhysicalInput.PRIMARY, 0.0), e.flush(0.3))
        e.discardPending()
        for ((x, t) in steps(0.1, 3, from = 0.35)) assertNull(e.feed(x, t))
        assertNull(e.flush(0.7))
    }

    /** 2026-11-07 워치6 클래식 실측: 베젤 한 바퀴 24칸 → 한 칸 = 1/24바퀴(15°). 천천히 돌리면 칸 사이 0.4~0.8초 */
    private fun slowClicks(count: Int, gap: Double, sign: Double = 1.0) =
        (0 until count).map { i -> sign / 24.0 to (1.0 + i * gap) }

    @Test
    fun slowDetentBezelFiresWithDetentConfig() {
        val d = RotaryGestureDetector(RotaryGestureConfig.ofDegrees(54, detented = true))
        // 기본 54° = 3.6칸 → 3칸(45°)으로는 안 되고 4칸(60°)째에 들어간다
        val three = RotaryGestureDetector(RotaryGestureConfig.ofDegrees(54, detented = true))
        slowClicks(3, 0.6).forEach { (x, t) -> three.feed(x, t) }
        assertNull(three.flush(1.0 + 2 * 0.6 + 0.2))
        val events = slowClicks(4, 0.6).map { (x, t) -> d.feed(x, t) }
        assertTrue(events.all { it == null }, "획이 끝나기 전에는 실행하지 않는다")
        assertEquals(PhysicalInput.PRIMARY, d.flush(1.0 + 3 * 0.6 + 0.2)?.input)
        // 반시계도 같다
        val d2 = RotaryGestureDetector(RotaryGestureConfig.ofDegrees(54, detented = true))
        slowClicks(4, 0.7, -1.0).forEach { (x, t) -> d2.feed(x, t) }
        assertEquals(PhysicalInput.SECONDARY, d2.flush(1.0 + 3 * 0.7 + 0.2)?.input)
    }

    @Test
    fun slowClicksNeverAddUpWithCrownConfig() {
        // 크라운 기준(멈춤 0.4초)이면 0.6초 간격 칸은 매번 처음부터 — 실기기에서 "천천히 돌리면 안 먹던" 그 상태
        val d = RotaryGestureDetector(RotaryGestureConfig.ofDegrees(54))
        slowClicks(8, 0.6).forEach { (x, t) -> assertNull(d.feed(x, t)) }
        assertNull(d.flush(1.0 + 7 * 0.6 + 0.2))
    }

    @Test
    fun detentConfigStillIgnoresScatteredClicks() {
        // 소매 스침처럼 띄엄띄엄(1.5초 넘게) 들어온 칸은 모이지 않는다
        val d = RotaryGestureDetector(RotaryGestureConfig.ofDegrees(54, detented = true))
        slowClicks(8, 1.5).forEach { (x, t) -> assertNull(d.feed(x, t)) }
        assertNull(d.flush(1.0 + 7 * 1.5 + 0.2))
    }
}

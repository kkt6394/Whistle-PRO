package com.aidenstudio.whistlepro.session

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 회전 제스처 판정 설정. 한 바퀴 = 1.0 기준 (바퀴 환산은 호출자 몫).
 * 애플 CrownGestureConfig와 같은 값·같은 규칙 (2026-11-06 코틀린 이식).
 */
data class RotaryGestureConfig(
    /** 입력 1·2로 치는 최소 회전 (바퀴). 기본 54도. 흔들기(실행 취소)는 방향 반전으로 구분하므로 겹치지 않는다 */
    var commandTurn: Double = DEFAULT_COMMAND_DEGREES / 360.0,
    /** 흔들기 한 번으로 셀 최소 회전 (바퀴). 0.03 = 약 11도 */
    var wiggleStep: Double = 0.03,
    /** 방향 반전 3번이 들어와야 하는 시간(초) */
    var wiggleWindow: Double = 1.5,
    /** 이만큼 멈추면 처음부터 */
    var idleReset: Double = 0.4,
    /** 실행 뒤 회전을 흡수하는 시간(초) */
    var cooldown: Double = 0.6,
    /** 기준을 넘은 획은 이만큼 멈춘 뒤에 실행한다. 그 전에 방향이 바뀌면 흔들기의 한 획으로 본다 */
    var strokeEnd: Double = 0.15,
    /** 획이 이만큼 넘어가면 멈추길 기다리지 않고 바로 실행한다 */
    var fullStroke: Double = 0.75,
) {
    companion object {
        /** 기본 입력 1·2 기준 (도) */
        const val DEFAULT_COMMAND_DEGREES = 54

        /** 고를 수 있는 기준: 18~180도, 6도 간격 */
        val COMMAND_DEGREE_OPTIONS: List<Int> = (18..180 step 6).toList()

        /** 범위 밖은 가까운 끝으로 */
        fun clampCommandDegrees(d: Int): Int =
            min(max(d, COMMAND_DEGREE_OPTIONS.first()), COMMAND_DEGREE_OPTIONS.last())

        /** 도 단위 기준으로 설정을 만든다 */
        fun ofDegrees(d: Int): RotaryGestureConfig =
            RotaryGestureConfig(commandTurn = clampCommandDegrees(d) / 360.0)

        /** 기준 각도가 기본보다 어떤지: 설정 줄에 "54도 · 기본"처럼 붙인다 */
        fun commandFeel(degrees: Int): CommandFeel = when {
            degrees == DEFAULT_COMMAND_DEGREES -> CommandFeel.STANDARD
            degrees < DEFAULT_COMMAND_DEGREES -> CommandFeel.SENSITIVE
            else -> CommandFeel.DULL
        }
    }
}

/** 기준 각도가 기본보다 어떤지 */
enum class CommandFeel {
    /** 기본 각도 */
    STANDARD,
    /** 기본보다 작다: 덜 돌려도 입력된다 */
    SENSITIVE,
    /** 기본보다 크다: 더 돌려야 입력된다 */
    DULL,
}

/** 판정된 회전 제스처. startedAt = 실행된 구간의 첫 delta 시각 */
data class RotaryGestureEvent(val input: PhysicalInput, val startedAt: Double)

/**
 * 회전 표본(delta, 시각)을 제스처로 바꾼다. 한 방향 0.15바퀴(54도) 이상 = PRIMARY/SECONDARY
 * (획이 끝나면, 3/4바퀴 넘으면 즉시), 흔들기 두 번 = UNDO. 애플 CrownGestureDetector와 같은 규칙.
 * 시계에 의존하지 않는다. 시각은 호출자가 초 단위로 넘긴다. 기준을 넘긴 뒤 손이 멈춘 것은 표본 없이는
 * 알 수 없으므로 호출자가 strokeEnd 뒤에 [flush]를 불러 준다.
 */
class RotaryGestureDetector(private val config: RotaryGestureConfig = RotaryGestureConfig()) {
    private var lastAt: Double? = null
    private var cooldownUntil = Double.NEGATIVE_INFINITY
    private var direction = 0.0
    private var segmentSize = 0.0
    private var segmentStart = 0.0
    private val reversals = mutableListOf<Double>()

    /** 기준을 넘었지만 아직 획이 끝나지 않은 입력 */
    private var pending: RotaryGestureEvent? = null

    /** 실행을 기다리는 입력이 있다 → 호출자는 strokeEnd 뒤 [flush] */
    val hasPending: Boolean get() = pending != null

    /** delta > 0 = 시계 방향(위로) */
    fun feed(delta: Double, at: Double): RotaryGestureEvent? {
        if (delta == 0.0) return null
        // 앞 획이 멈춘 채 끝났으면 먼저 실행하고, 이 표본은 새 획으로
        val flushed = flush(at)
        lastAt?.let { if (at - it > config.idleReset) resetTracking() }
        lastAt = at
        if (at < cooldownUntil) return flushed

        val sign = if (delta > 0) 1.0 else -1.0
        if (sign == direction) {
            segmentSize += abs(delta)
        } else {
            // 방향이 바뀌면 기다리던 입력은 흔들기의 한 획이 된다
            pending = null
            if (direction != 0.0 && segmentSize >= config.wiggleStep) reversals.add(at)
            direction = sign
            segmentSize = abs(delta)
            segmentStart = at
        }
        reversals.removeAll { at - it > config.wiggleWindow }

        if (reversals.size >= 3) {
            fire(at)
            return RotaryGestureEvent(PhysicalInput.UNDO, segmentStart)
        }
        if (segmentSize >= config.fullStroke) {
            // 3/4바퀴를 넘긴 획은 흔들기가 아니다 → 바로 실행
            val event = pending
                ?: RotaryGestureEvent(if (sign > 0) PhysicalInput.PRIMARY else PhysicalInput.SECONDARY, segmentStart)
            fire(at)
            return event
        }
        if (segmentSize >= config.commandTurn && pending == null) {
            pending = RotaryGestureEvent(if (sign > 0) PhysicalInput.PRIMARY else PhysicalInput.SECONDARY, segmentStart)
        }
        return flushed
    }

    /** 기준을 넘긴 획이 strokeEnd 동안 멈춰 있었으면 실행한다 */
    fun flush(at: Double): RotaryGestureEvent? {
        val event = pending ?: return null
        val last = lastAt ?: return null
        if (at - last < config.strokeEnd) return null
        pending = null
        fire(last + config.strokeEnd)
        return event
    }

    private fun resetTracking() {
        direction = 0.0
        segmentSize = 0.0
        reversals.clear()
        pending = null
    }

    private fun fire(at: Double) {
        resetTracking()
        cooldownUntil = at + config.cooldown
    }
}

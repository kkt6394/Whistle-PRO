package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.Team

/** 워치 물리 입력 (크라운 누름·뒤로 키 등). 누른 횟수로 구분한다. */
enum class PhysicalInput {
    PRIMARY, SECONDARY, UNDO;

    companion object {
        /** 1번 → PRIMARY, 2번 → SECONDARY, 3번 → UNDO, 그 밖은 null */
        fun fromPressCount(count: Int): PhysicalInput? = when (count) {
            1 -> PRIMARY
            2 -> SECONDARY
            3 -> UNDO
            else -> null
        }
    }
}

/** 물리 입력이 일으키는 동작 */
sealed interface PhysicalAction {
    data object Kickoff : PhysicalAction
    data object Pause : PhysicalAction
    data object Resume : PhysicalAction
    data object OpenGoal : PhysicalAction
    data class Goal(val team: Team) : PhysicalAction
    data object CancelGoal : PhysicalAction
    data object EndPeriod : PhysicalAction
    data object StartPeriod : PhysicalAction
    data class ShootoutFirstTeam(val team: Team) : PhysicalAction
    data class ShootoutKick(val scored: Boolean) : PhysicalAction
    data class SetKickoffTeam(val team: Team) : PhysicalAction
    data object Undo : PhysicalAction
    data object None : PhysicalAction
}

/** 동작마다 다른 진동. 경고 알림 패턴과 겹치지 않는다. None은 진동 없음. */
val PhysicalAction.hapticPattern: HapticPattern?
    get() = when (this) {
        PhysicalAction.Kickoff, PhysicalAction.StartPeriod -> HapticPattern(HapticTone.START, 1, 0.0)
        PhysicalAction.Pause -> HapticPattern(HapticTone.STOP, 2, 0.3)
        PhysicalAction.Resume -> HapticPattern(HapticTone.START, 2, 0.3)
        PhysicalAction.OpenGoal -> HapticPattern(HapticTone.NOTIFICATION, 1, 0.0)
        is PhysicalAction.Goal -> homeAwayPattern(team)
        is PhysicalAction.ShootoutFirstTeam -> homeAwayPattern(team)
        is PhysicalAction.SetKickoffTeam -> homeAwayPattern(team)
        is PhysicalAction.ShootoutKick ->
            if (scored) HapticPattern(HapticTone.SUCCESS, 1, 0.0) else HapticPattern(HapticTone.STOP, 2, 0.3)
        PhysicalAction.EndPeriod -> HapticPattern(HapticTone.STOP, 3, 0.3)
        PhysicalAction.CancelGoal, PhysicalAction.Undo -> HapticPattern(HapticTone.RETRY, 3, 0.3)
        PhysicalAction.None -> null
    }

private fun homeAwayPattern(team: Team): HapticPattern =
    if (team == Team.HOME) HapticPattern(HapticTone.SUCCESS, 1, 0.0) else HapticPattern(HapticTone.SUCCESS, 2, 0.3)

/** 입력을 해석할 때 필요한 화면 상태 */
sealed interface PhysicalContext {
    /** 킥오프 대기 화면 (경기 표시 없음) */
    data object Ready : PhysicalContext

    data class Match(
        val screen: MatchDisplay.Screen,
        val stoppageRunning: Boolean,
        val shootout: ShootoutDisplay?,
        val goalOpen: Boolean,
        /** 연장 대기인데 선축(연장 전 동전 던지기, IFAB Law 8)이 아직 없음 (2026-11-07) */
        val needsKickoffTeam: Boolean = false,
    ) : PhysicalContext

    companion object {
        operator fun invoke(display: MatchDisplay, goalOpen: Boolean): PhysicalContext =
            Match(
                display.screen, display.stoppageRunning, display.shootout, goalOpen,
                needsKickoffTeam = display.screen == MatchDisplay.Screen.PRE_EXTRA_TIME && display.kickoffTeam == null,
            )
    }
}

/** 상태 + 입력 → 동작. 골 선택 중(goalOpen)은 기간 진행 중에만 의미가 있다. */
fun physicalAction(input: PhysicalInput, context: PhysicalContext): PhysicalAction {
    if (context !is PhysicalContext.Match) {
        return if (input == PhysicalInput.PRIMARY) PhysicalAction.Kickoff else PhysicalAction.None
    }
    val primary = input == PhysicalInput.PRIMARY
    val undo = input == PhysicalInput.UNDO
    return when (context.screen) {
        MatchDisplay.Screen.NOT_STARTED -> PhysicalAction.None
        MatchDisplay.Screen.PERIOD -> when {
            context.goalOpen -> when (input) {
                PhysicalInput.PRIMARY -> PhysicalAction.Goal(Team.HOME)
                PhysicalInput.SECONDARY -> PhysicalAction.Goal(Team.AWAY)
                PhysicalInput.UNDO -> PhysicalAction.CancelGoal
            }
            else -> when (input) {
                PhysicalInput.PRIMARY -> if (context.stoppageRunning) PhysicalAction.Resume else PhysicalAction.Pause
                PhysicalInput.SECONDARY ->
                    if (context.stoppageRunning) PhysicalAction.EndPeriod else PhysicalAction.OpenGoal
                PhysicalInput.UNDO -> PhysicalAction.Undo
            }
        }
        // 2026-11-07 연장 선축이 없으면 승부차기 먼저 찰 팀처럼 한 번=홈·두 번=원정으로 먼저 기록한다.
        // 크라운으로 돌리다 선축 없이 연장이 시작되던 것. 휴식(정규 후반)은 킥오프 팀이 자동이라 그대로.
        MatchDisplay.Screen.INTERVAL, MatchDisplay.Screen.PRE_EXTRA_TIME -> when {
            undo -> PhysicalAction.Undo
            context.screen == MatchDisplay.Screen.PRE_EXTRA_TIME && context.needsKickoffTeam ->
                PhysicalAction.SetKickoffTeam(if (primary) Team.HOME else Team.AWAY)
            primary -> PhysicalAction.StartPeriod
            else -> PhysicalAction.None
        }
        MatchDisplay.Screen.PENALTIES -> when {
            undo -> PhysicalAction.Undo
            context.shootout?.needsFirstTeam == true ->
                PhysicalAction.ShootoutFirstTeam(if (primary) Team.HOME else Team.AWAY)
            context.shootout?.next != null -> PhysicalAction.ShootoutKick(primary)
            else -> PhysicalAction.None
        }
        MatchDisplay.Screen.FINISHED -> if (undo) PhysicalAction.Undo else PhysicalAction.None
    }
}

/** 물리 입력 시간 상수 (초) */
object PhysicalTiming {
    /** 연속 누름을 한 묶음으로 보는 시간 */
    const val PRESS_WINDOW = 0.4
    /** 골 팀 선택을 기다리는 시간 */
    const val GOAL_PICK_TIMEOUT = 3.0
    /** 중단 시간이 흐르는 동안 알려주는 간격 */
    const val STOPPAGE_REMINDER = 30.0
}

package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.MatchEngine
import com.aidenstudio.whistlepro.core.MatchState

/**
 * [실행 취소]가 되돌릴 마지막 명령 (Swift `UndoTarget`). 메뉴의 버튼 아래 한 줄과 되돌린 뒤 메인 작은 줄 안내에 쓴다
 * (2026-11-04 A6 critique). 화면 문구로 바꾸는 일은 앱이 한다.
 */
sealed interface UndoTarget {
    /** 이벤트 기록 (그 줄) */
    data class Recorded(val row: EventRow) : UndoTarget
    /** 이벤트 번호 수정 (고친 뒤의 줄) */
    data class Edited(val row: EventRow) : UndoTarget
    /** 이벤트 삭제 (지우기 전의 줄) */
    data class Deleted(val row: EventRow) : UndoTarget
    /** 중단을 시작함 / 중단을 끝내고 재개함 */
    data class Stoppage(val started: Boolean) : UndoTarget
    /** 기간 시작·종료 (기간 라벨 "1H" 등) */
    data class PeriodStarted(val label: String) : UndoTarget
    data class PeriodEnded(val label: String) : UndoTarget
    data object KickoffTeam : UndoTarget
    data object ShootoutFirstTeam : UndoTarget
    data object PeriodLength : UndoTarget
    data object Abandoned : UndoTarget
}

/** 되돌릴 것이 없으면 null */
val MatchSession.undoTarget: UndoTarget?
    get() {
        if (!canUndo) return null
        val effective = MatchEngine.effectiveCommands(record.commands)
        val last = effective.lastOrNull() ?: return null
        fun row(id: String, state: MatchState): EventRow? = state.eventRows(setup).firstOrNull { it.id == id }
        val periodLabel = state.periods.lastOrNull()?.ref?.label(state.rules.periodCount)
        return when (val kind = last.kind) {
            is CommandKind.RecordEvent -> row(last.id, state)?.let { UndoTarget.Recorded(it) }
            is CommandKind.EditEvent -> row(kind.id, state)?.let { UndoTarget.Edited(it) }
            is CommandKind.DeleteEvent -> {
                val before = MatchEngine.reduce(record.setup.rules, effective.dropLast(1))
                row(kind.id, before)?.let { UndoTarget.Deleted(it) }
            }
            CommandKind.ToggleStoppage ->
                UndoTarget.Stoppage(started = state.periods.lastOrNull()?.stoppages?.lastOrNull()?.let { it.end == null } ?: false)
            CommandKind.StartPeriod -> periodLabel?.let { UndoTarget.PeriodStarted(it) }
            CommandKind.EndPeriod -> periodLabel?.let { UndoTarget.PeriodEnded(it) }
            is CommandKind.SetKickoffTeam -> UndoTarget.KickoffTeam
            is CommandKind.SetShootoutFirstTeam -> UndoTarget.ShootoutFirstTeam
            is CommandKind.AdjustPeriodLength -> UndoTarget.PeriodLength
            is CommandKind.AbandonMatch -> UndoTarget.Abandoned
            CommandKind.Undo -> null
        }
    }

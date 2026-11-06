package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.AlertKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.Phase
import com.aidenstudio.whistlepro.core.PeriodRef
import com.aidenstudio.whistlepro.core.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val firstHalf = Phase.Period(PeriodRef(PeriodRef.Kind.REGULAR, 1))

class MatchSessionTests {
    private fun makeSession(setup: MatchSetup = MatchSetup()): Triple<MatchSession, MemoryStore, FakeClock> {
        val store = MemoryStore()
        val clock = FakeClock()
        val session = MatchSession.start(setup, store, now = { clock.t })
        return Triple(session, store, clock)
    }

    @Test fun startPersistsEmptyRecord() {
        val (s, store, _) = makeSession()
        assertEquals(s.record, store.active)
        assertTrue(s.record.commands.isEmpty())
        assertEquals(Phase.NotStarted, s.state.phase)
    }

    @Test fun acceptedCommandIsStampedAppliedAndPersisted() {
        val (s, store, clock) = makeSession()
        clock.t = 1000.0
        assertTrue(s.send(CommandKind.StartPeriod))
        assertEquals(firstHalf, s.state.phase)
        assertEquals(listOf(1000.0), store.active?.commands?.map { it.timestamp })
    }

    @Test fun commandCanBeStampedWithEarlierTime() {
        val (s, _, clock) = makeSession()
        clock.t = 1000.0
        s.send(CommandKind.StartPeriod)
        clock.t = 1300.0
        assertTrue(s.send(CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)), at = 1240.0))
        assertEquals(1240.0, s.record.commands.last().timestamp)
        assertEquals(1240.0, s.state.events.last().timestamp)
    }

    @Test fun earlierTimeIsClampedBetweenLastCommandAndNow() {
        val (s, _, clock) = makeSession()
        clock.t = 1000.0
        s.send(CommandKind.StartPeriod)
        clock.t = 1300.0
        s.send(CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)), at = 900.0)
        assertEquals(1000.0, s.record.commands.last().timestamp)
        s.send(CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)), at = 2000.0)
        assertEquals(1300.0, s.record.commands.last().timestamp)
    }

    @Test fun rejectedCommandIsNotLogged() {
        val (s, store, _) = makeSession()
        assertFalse(s.send(CommandKind.EndPeriod))
        assertTrue(s.record.commands.isEmpty())
        assertEquals(true, store.active?.commands?.isEmpty())
    }

    @Test fun undoAfterRejectedDoubleTapUndoesTheRealCommand() {
        val (s, _, clock) = makeSession()
        s.send(CommandKind.StartPeriod)
        clock.t = 2700.0
        assertTrue(s.send(CommandKind.EndPeriod))
        clock.t = 2701.0
        assertFalse(s.send(CommandKind.EndPeriod))
        assertTrue(s.undo())
        assertEquals(firstHalf, s.state.phase)
    }

    @Test fun undoWithNothingToUndoReturnsFalse() {
        val (s, _, _) = makeSession()
        assertFalse(s.canUndo)
        assertFalse(s.undo())
        assertTrue(s.record.commands.isEmpty())
    }

    @Test fun restoringFromSavedRecordRebuildsState() {
        val (s, store, clock) = makeSession()
        s.send(CommandKind.StartPeriod)
        clock.t = 100.0
        s.send(CommandKind.ToggleStoppage)
        val restored = MatchSession(assertNotNull(store.active), store, now = { clock.t })
        assertEquals(s.state, restored.state)
    }

    @Test fun archivesOnlyFinishedMatch() {
        val (s, store, _) = makeSession()
        s.send(CommandKind.StartPeriod)
        assertFalse(s.archiveIfFinished())
        assertTrue(store.finished.isEmpty())

        s.send(CommandKind.AbandonMatch("폭우"))
        assertTrue(s.isFinished)
        assertTrue(s.archiveIfFinished())
        assertNull(store.active)
        assertEquals(1, store.finished.size)
    }

    @Test fun saveFailureIsReportedButMatchContinues() {
        val (s, store, _) = makeSession()
        store.failSaves = true
        assertTrue(s.send(CommandKind.StartPeriod))
        assertTrue(s.state.phase.isPeriod)
        assertNotNull(s.saveError)

        store.failSaves = false
        s.send(CommandKind.ToggleStoppage)
        assertNull(s.saveError)
        assertEquals(2, store.active?.commands?.size)
    }

    @Test fun nextAlertUsesSetupWarning() {
        val (s, _, _) = makeSession(MatchSetup(warningBeforeEnd = 600))
        s.send(CommandKind.StartPeriod)
        val alert = s.nextAlert(0.0)
        assertEquals(AlertKind.WARNING_BEFORE_END, alert?.kind)
        assertEquals(2100.0, alert?.at)
    }

    @Test fun nextAlertUsesSetupBreakWarning() {
        val (s, _, clock) = makeSession(MatchSetup(breakWarningBeforeEnd = 120))
        s.send(CommandKind.StartPeriod)
        clock.t = 2700.0
        s.send(CommandKind.EndPeriod)
        val alert = s.nextAlert(2700.0)
        assertEquals(AlertKind.BREAK_ENDING_SOON, alert?.kind)
        assertEquals(3480.0, alert?.at)
    }
}

/** 최종 리뷰: 킥오프를 실행 취소하면 경기 시작 버튼이 없는 화면에 갇힌다 */
class KickoffUndoTests {
    private fun makeSession(setup: MatchSetup = MatchSetup()): Triple<MatchSession, MemoryStore, FakeClock> {
        val store = MemoryStore()
        val clock = FakeClock()
        return Triple(MatchSession.start(setup, store, now = { clock.t }), store, clock)
    }

    @Test fun kickoffItselfCannotBeUndone() {
        val store = MemoryStore()
        val clock = FakeClock()
        val s = MatchSession.start(MatchSetup(), store, now = { clock.t })
        s.send(CommandKind.SetKickoffTeam(Team.HOME))
        s.send(CommandKind.StartPeriod)
        assertFalse(s.canUndo)
        assertFalse(s.undo())
        assertTrue(s.state.phase.isPeriod)

        clock.t = 100.0
        s.send(CommandKind.ToggleStoppage)
        assertTrue(s.canUndo)
        assertTrue(s.undo())
        assertTrue(s.state.phase.isPeriod)
        assertFalse(s.canUndo)
    }

    /** [킥오프 취소]는 첫 기간(전반)이 진행 중일 때만: 기간을 끝냈거나 그 뒤(하프타임·후반·종료)는 안 된다 (2026-11-06) */
    @Test fun canCancelKickoffOnlyInFirstPeriod() {
        val (s, _, clock) = makeSession()
        assertFalse(s.canCancelKickoff, "킥오프 전")
        clock.t = 1000.0
        s.send(CommandKind.SetKickoffTeam(Team.AWAY))
        s.send(CommandKind.StartPeriod)
        assertTrue(s.canCancelKickoff, "킥오프 직후")
        s.send(CommandKind.ToggleStoppage)
        s.send(CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)))
        assertTrue(s.canCancelKickoff, "전반 중 기록이 있어도")
        clock.t = 3700.0
        s.send(CommandKind.EndPeriod)
        assertFalse(s.canCancelKickoff, "하프타임")
        assertTrue(s.undo())
        assertTrue(s.canCancelKickoff, "전반 종료를 되돌리면 다시 전반")
        s.send(CommandKind.EndPeriod)
        clock.t = 4600.0
        s.send(CommandKind.StartPeriod)
        assertFalse(s.canCancelKickoff, "후반")
        s.send(CommandKind.AbandonMatch("비"))
        assertTrue(s.isFinished)
        assertFalse(s.canCancelKickoff, "종료")
    }

    /** 전반에 바로 중단한 경기도 끝난 경기라 취소할 수 없다. 쿼터제도 첫 쿼터만 */
    @Test fun canCancelKickoffNotAfterAbandonAndOnlyFirstQuarter() {
        val (s, _, clock) = makeSession()
        clock.t = 10.0
        s.send(CommandKind.StartPeriod)
        s.send(CommandKind.AbandonMatch("비"))
        assertFalse(s.canCancelKickoff)

        val (q, _, qc) = makeSession(MatchSetup(rules = com.aidenstudio.whistlepro.core.RuleSet(periodCount = 4, periodLength = 600, quarterBreakLength = 60)))
        qc.t = 10.0
        q.send(CommandKind.StartPeriod)
        assertTrue(q.canCancelKickoff)
        qc.t = 700.0
        q.send(CommandKind.EndPeriod)
        qc.t = 800.0
        q.send(CommandKind.StartPeriod)
        assertFalse(q.canCancelKickoff, "2쿼터")
    }

    /** Ruling 11·12: 취소하면 버린 경기 setup 전체(이름·규칙·기간)가 설정(draft)으로, 팀 색은 지금 설정과 다를 때만 이번 경기만 색으로 */
    @Test fun kickoffCancelRestoresWholeSetupWithColorsAsOverride() {
        val draft = MatchSetup()
        val played = MatchSetup(
            rules = com.aidenstudio.whistlepro.core.RuleSet(periodLength = 1800, halfTimeLength = 600), homeName = "청구", awayName = "영덕",
            homeColor = TeamColor.NAVY, awayColor = TeamColor.MAROON,
        )
        val restored = KickoffCancel.restore(played, draft)
        // draft에는 색만 빼고 경기 setup 그대로
        assertEquals("청구", restored.draft.homeName)
        assertEquals("영덕", restored.draft.awayName)
        assertEquals(played.rules, restored.draft.rules)
        assertEquals(TeamColors.from(draft), TeamColors.from(restored.draft))
        // 색은 이번 경기만 색으로, 합치면 버린 경기 setup과 같다
        assertEquals(TeamColors.from(played), restored.colorOverride)
        assertEquals(played, restored.colorOverride!!.applied(restored.draft))

        // 색이 같으면 이번 경기만 색은 없다
        val sameColors = TeamColors.from(draft).applied(played)
        val plain = KickoffCancel.restore(sameColors, draft)
        assertNull(plain.colorOverride)
        assertEquals(sameColors, plain.draft)
    }
}

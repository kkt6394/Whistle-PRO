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
}

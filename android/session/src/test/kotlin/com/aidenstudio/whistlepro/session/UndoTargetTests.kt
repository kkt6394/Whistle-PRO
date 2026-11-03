package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.ResolvedEventKind
import com.aidenstudio.whistlepro.core.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** 메뉴 [실행 취소] 아래 한 줄: 되돌릴 마지막 명령 (Swift UndoTargetTests, 2026-11-04 A6 critique) */
class UndoTargetTests {
    private fun started(): Pair<MatchSession, FakeClock> {
        val clock = FakeClock()
        clock.t = 1000.0
        val s = MatchSession.start(MatchSetup(), MemoryStore(), now = { clock.t })
        s.send(CommandKind.StartPeriod)
        return s to clock
    }

    @Test fun nothingToUndoRightAfterKickoff() {
        assertNull(started().first.undoTarget)
    }

    @Test fun recordedEventShowsItsRow() {
        val (s, clock) = started()
        clock.t = 1000.0 + 22 * 60 + 5
        s.send(CommandKind.RecordEvent(EventPayload.Card(Team.AWAY, 8, CardKind.YELLOW)))
        val row = assertIs<UndoTarget.Recorded>(s.undoTarget).row
        assertEquals(ResolvedEventKind.YELLOW, row.kind)
        assertEquals(Team.AWAY, row.team)
        assertEquals("#8", row.detail)
        assertEquals(EventMinute.Regular(23), row.minute)
    }

    @Test fun stoppageStartAndResume() {
        val (s, clock) = started()
        clock.t = 1100.0
        s.send(CommandKind.ToggleStoppage)
        assertEquals(UndoTarget.Stoppage(started = true), s.undoTarget)
        clock.t = 1160.0
        s.send(CommandKind.ToggleStoppage)
        assertEquals(UndoTarget.Stoppage(started = false), s.undoTarget)
    }

    @Test fun periodEndThenSecondHalfStart() {
        val (s, clock) = started()
        clock.t = 1000.0 + 46 * 60
        s.send(CommandKind.EndPeriod)
        assertEquals(UndoTarget.PeriodEnded("1H"), s.undoTarget)
        clock.t += 15 * 60
        s.send(CommandKind.StartPeriod)
        assertEquals(UndoTarget.PeriodStarted("2H"), s.undoTarget)
    }

    @Test fun editAndDeleteShowTheEventBeforeAndAfter() {
        val (s, clock) = started()
        clock.t = 1300.0
        s.send(CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)))
        val id = s.state.events[0].id
        s.send(CommandKind.EditEvent(id, EventPayload.Goal(Team.HOME, 10, false)))
        assertEquals("#10", assertIs<UndoTarget.Edited>(s.undoTarget).row.detail)
        s.send(CommandKind.DeleteEvent(id))
        val deleted = assertIs<UndoTarget.Deleted>(s.undoTarget).row
        assertEquals("#10", deleted.detail)
        assertEquals(ResolvedEventKind.GOAL, deleted.kind)
    }

    @Test fun undoMovesToThePreviousCommand() {
        val (s, clock) = started()
        clock.t = 1300.0
        s.send(CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)))
        clock.t = 1400.0
        s.send(CommandKind.ToggleStoppage)
        s.undo()
        assertIs<UndoTarget.Recorded>(s.undoTarget)
    }
}

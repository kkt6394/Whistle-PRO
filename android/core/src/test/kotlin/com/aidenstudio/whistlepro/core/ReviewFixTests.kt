package com.aidenstudio.whistlepro.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 최종 리뷰에서 나온 실제 경기 시나리오 결함 */
class ReviewFixTests {
    @Test fun sinBinnedPlayerLaterSentOffIsCountedOnce() {
        val s = play(RuleSet(sinBin = true), listOf(cmd(start, 0.0), card(Team.HOME, 5, CardKind.SIN_BIN, 100.0), card(Team.HOME, 5, CardKind.RED, 160.0)))
        assertEquals(TeamPair(10L, 11L), s.playersOnField(200.0))
        assertTrue(s.activeSinBins(200.0).isEmpty())
        assertTrue(s.alerts(200.0, 0, 300).all { it.kind != AlertKind.SIN_BIN_END })
    }

    @Test fun redShownAfterSecondYellowIsNotASecondSendOff() {
        val s = play(listOf(cmd(start, 0.0), card(Team.HOME, 5, CardKind.YELLOW, 100.0), card(Team.HOME, 5, CardKind.YELLOW, 200.0),
            card(Team.HOME, 5, CardKind.RED, 201.0, id = "r")))
        assertEquals(TeamPair(1, 0), s.sentOffCounts)
        assertEquals(TeamPair(10L, 11L), s.playersOnField(300.0))
        assertEquals(listOf(EventWarning.PLAYER_ALREADY_SENT_OFF), s.resolvedEvents.last().warnings)
    }

    @Test fun thirdYellowForDismissedPlayerIsFlaggedNotCounted() {
        val s = play(listOf(cmd(start, 0.0), card(Team.HOME, 5, CardKind.YELLOW, 100.0), card(Team.HOME, 5, CardKind.YELLOW, 200.0),
            card(Team.HOME, 5, CardKind.YELLOW, 300.0)))
        assertEquals(listOf(ResolvedEventKind.YELLOW, ResolvedEventKind.SECOND_YELLOW, ResolvedEventKind.YELLOW), s.resolvedEvents.map { it.kind })
        assertEquals(listOf(EventWarning.PLAYER_ALREADY_SENT_OFF), s.resolvedEvents.last().warnings)
        assertEquals(TeamPair(1, 0), s.sentOffCounts)
    }

    @Test fun goalCannotBeDeletedAfterFullTimeDecision() {
        // 2:1 종료 후 골 삭제로 1:1이 되면 "1:1 completed"라는 모순 기록이 된다
        val s = play(listOf(cmd(start, 0.0), goal(Team.HOME, 100.0, id = "g1"), goal(Team.HOME, 200.0), goal(Team.AWAY, 300.0),
            cmd(end, 2700.0), cmd(start, 3600.0), cmd(end, 6300.0),
            cmd(CommandKind.DeleteEvent("g1"), 6400.0, id = "d")))
        assertEquals(listOf("d"), s.ignoredCommandIds)
        assertEquals(TeamPair(2, 1), s.score)
    }

    @Test fun goalTeamCannotChangeAfterExtraTimeWasDecided() {
        val s = play(RuleSet(extraTime = true, penalties = true),
            listOf(cmd(start, 0.0), goal(Team.HOME, 100.0), goal(Team.AWAY, 200.0, id = "a"),
                cmd(end, 2700.0), cmd(start, 3600.0), cmd(end, 6300.0),
                cmd(CommandKind.EditEvent("a", EventPayload.Goal(Team.HOME, null, false)), 6400.0, id = "e")))
        assertEquals(Phase.PreExtraTime, s.phase)
        assertEquals(listOf("e"), s.ignoredCommandIds)
    }

    @Test fun goalNumberCanStillBeCorrectedAfterFullTime() {
        val s = play(listOf(cmd(start, 0.0), goal(Team.HOME, 100.0, id = "g"), cmd(end, 2700.0),
            cmd(start, 3600.0), cmd(end, 6300.0),
            cmd(CommandKind.EditEvent("g", EventPayload.Goal(Team.HOME, 10, false)), 6400.0)))
        assertTrue(s.ignoredCommandIds.isEmpty())
        assertEquals(EventPayload.Goal(Team.HOME, 10, false), s.events[0].payload)
    }

    @Test fun goalCanBeCorrectedDuringHalfTime() {
        val s = play(listOf(cmd(start, 0.0), goal(Team.HOME, 100.0, id = "g"), cmd(end, 2700.0),
            cmd(CommandKind.DeleteEvent("g"), 2800.0)))
        assertTrue(s.ignoredCommandIds.isEmpty())
        assertEquals(TeamPair(0, 0), s.score)
    }
}

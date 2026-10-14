package com.aidenstudio.whistlepro.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhaseTransitionTests {
    @Test fun regularMatchRunsTwoHalves() {
        assertEquals(Phase.Period(regular(1)), play(listOf(cmd(start, 0.0))).phase)

        val firstHalfDone = listOf(cmd(start, 0.0), cmd(end, 2760.0))
        assertEquals(Phase.Interval(regular(1)), play(firstHalfDone).phase)

        val secondHalf = play(firstHalfDone + cmd(start, 3660.0))
        assertEquals(Phase.Period(regular(2)), secondHalf.phase)
        assertEquals(2700, secondHalf.periods[1].offset)

        assertEquals(completed, play(firstHalfDone + listOf(cmd(start, 3660.0), cmd(end, 6400.0))).phase)
    }

    @Test fun quartersHaveFourPeriodsAndBreakLengths() {
        val rules = RuleSet(periodCount = 4, periodLength = 1500, halfTimeLength = 600, quarterBreakLength = 120)
        val commands = mutableListOf<Command>()
        var t = 0.0
        repeat(3) {
            commands += listOf(cmd(start, t), cmd(end, t + 1500))
            t += 2000
        }
        val s = play(rules, commands)
        assertEquals(Phase.Interval(regular(3)), s.phase)
        assertEquals(120, s.intervalLength(regular(1)))
        assertEquals(600, s.intervalLength(regular(2)))
        assertEquals(120, s.intervalLength(regular(3)))

        val done = play(rules, commands + listOf(cmd(start, t), cmd(end, t + 1500)))
        assertEquals(completed, done.phase)
        assertEquals(listOf("Q1", "Q2", "Q3", "Q4"), done.periods.map { it.ref.label(4) })
        assertEquals(listOf(0, 1500, 3000, 4500), done.periods.map { it.offset })
    }

    @Test fun tiedMatchGoesThroughExtraTimeToPenalties() {
        val rules = RuleSet(extraTime = true, penalties = true)
        assertEquals(Phase.PreExtraTime, play(rules, twoHalves()).phase)

        val et1 = twoHalves() + listOf(cmd(start, 6600.0), cmd(end, 7500.0))
        val s = play(rules, et1)
        assertEquals(Phase.Interval(extra(1)), s.phase)
        assertEquals(5400, s.periods[2].offset)
        assertEquals(900, s.periods[2].nominalLength)
        assertEquals(60, s.intervalLength(extra(1)))

        val done = play(rules, et1 + listOf(cmd(start, 7560.0), cmd(end, 8460.0)))
        assertEquals(Phase.Penalties, done.phase)
        assertEquals(listOf("1H", "2H", "ET1", "ET2"), done.periods.map { it.ref.label(2) })
    }

    @Test fun tiedWithPenaltiesOnlySkipsExtraTime() {
        assertEquals(Phase.Penalties, play(RuleSet(penalties = true), twoHalves()).phase)
    }

    @Test fun tiedWithoutTiebreakersFinishes() {
        assertEquals(completed, play(twoHalves()).phase)
    }

    @Test fun invalidCommandIsIgnoredAndRecorded() {
        val s = play(listOf(cmd(end, 0.0, id = "bad"), cmd(start, 1.0), cmd(start, 2.0, id = "dup")))
        assertEquals(listOf("bad", "dup"), s.ignoredCommandIds)
        assertEquals(1, s.periods.size)
    }

    @Test fun kickoffAlternatesEachPeriod() {
        assertEquals(Team.HOME, play(listOf(cmd(CommandKind.SetKickoffTeam(Team.HOME), 0.0))).kickoffTeam)

        val afterFirstHalf = listOf(cmd(CommandKind.SetKickoffTeam(Team.HOME), 0.0), cmd(start, 0.0), cmd(end, 2700.0))
        assertEquals(Team.AWAY, play(afterFirstHalf).kickoffTeam)
        assertEquals(Team.HOME, play(RuleSet(alternateKickoff = false), afterFirstHalf).kickoffTeam)
    }

    @Test fun kickoffCannotChangeAfterStart() {
        val s = play(listOf(cmd(start, 0.0), cmd(CommandKind.SetKickoffTeam(Team.HOME), 10.0, id = "late")))
        assertEquals(listOf("late"), s.ignoredCommandIds)
        assertNull(s.kickoffTeam)
    }

    @Test fun extraTimeKickoffIsSetSeparately() {
        val rules = RuleSet(extraTime = true)
        val toExtra = listOf(cmd(CommandKind.SetKickoffTeam(Team.HOME), 0.0)) + twoHalves() +
            cmd(CommandKind.SetKickoffTeam(Team.AWAY), 6400.0)
        val s = play(rules, toExtra)
        assertEquals(Team.AWAY, s.kickoffTeam)
        assertEquals(Team.HOME, s.regularKickoff)

        val et2 = play(rules, toExtra + listOf(cmd(start, 6600.0), cmd(end, 7500.0)))
        assertEquals(Team.HOME, et2.kickoffTeam)
    }

    @Test fun abandonClosesRunningPeriod() {
        val s = play(listOf(cmd(start, 0.0), cmd(CommandKind.AbandonMatch("폭우"), 1000.0)))
        assertEquals(Phase.Finished(FinishReason.Abandoned("폭우")), s.phase)
        assertEquals(1000.0, s.periods[0].endedAt)

        val again = play(listOf(cmd(start, 0.0), cmd(CommandKind.AbandonMatch("폭우"), 1000.0),
            cmd(CommandKind.AbandonMatch("x"), 1100.0, id = "late")))
        assertEquals(listOf("late"), again.ignoredCommandIds)
    }

    @Test fun undoRemovesLastCommand() {
        val s = play(listOf(cmd(start, 0.0), cmd(end, 2700.0), cmd(CommandKind.Undo, 2701.0)))
        assertEquals(Phase.Period(regular(1)), s.phase)
        assertNull(s.periods[0].endedAt)
    }

    @Test fun undoOnEmptyLogIsHarmless() {
        val s = play(listOf(cmd(CommandKind.Undo, 0.0), cmd(CommandKind.Undo, 1.0), cmd(start, 2.0)))
        assertEquals(Phase.Period(regular(1)), s.phase)
        assertTrue(s.ignoredCommandIds.isEmpty())
    }

    @Test fun commandsWithSameTimestampApplyInLogOrder() {
        val s = play(listOf(cmd(CommandKind.SetKickoffTeam(Team.AWAY), 0.0), cmd(start, 0.0)))
        assertTrue(s.ignoredCommandIds.isEmpty())
        assertEquals(Team.AWAY, s.kickoffTeam)
    }

    /** Kotlin 추가: 거부된 명령은 상태를 전혀 바꾸지 않고 ignored에만 남는다 (불변 상태 + 예외 방식 확인) */
    @Test fun rejectedCommandLeavesStateUnchanged() {
        val base = twoHalves() + listOf(
            cmd(CommandKind.SetShootoutFirstTeam(Team.HOME), 6400.0),
            cmd(CommandKind.RecordEvent(EventPayload.PenaltyKick(Team.HOME, 7, true)), 6410.0),
        )
        val rules = RuleSet(penalties = true)
        val rejected = listOf(
            cmd(CommandKind.RecordEvent(EventPayload.PenaltyKick(Team.HOME, 8, true)), 6420.0, id = "turn"),
            cmd(CommandKind.RecordEvent(EventPayload.PenaltyKick(Team.AWAY, 100, true)), 6430.0, id = "number"),
        )
        val withRejected = play(rules, base + rejected)
        val without = play(rules, base)
        assertEquals(listOf("turn", "number"), withRejected.ignoredCommandIds)
        assertEquals(without, withRejected.copy(ignoredCommandIds = emptyList()))
    }
}

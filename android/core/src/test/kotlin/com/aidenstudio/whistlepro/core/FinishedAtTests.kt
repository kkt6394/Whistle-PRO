package com.aidenstudio.whistlepro.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 경기가 끝난 시각 (Swift FinishedAtTests와 같다 — 운동 종료 시각에 쓴다) */
class FinishedAtTests {
    private fun kick(team: Team, scored: Boolean, at: Double) =
        cmd(CommandKind.RecordEvent(EventPayload.PenaltyKick(team, null, scored)), at)

    @Test fun nilUntilFinished() {
        assertNull(play(listOf(cmd(start, 0.0))).finishedAt)
        assertNull(play(listOf(cmd(start, 0.0), cmd(end, 2700.0))).finishedAt)
    }

    @Test fun lastPeriodEnd() {
        val s = play(listOf(cmd(start, 0.0), cmd(end, 2700.0), cmd(start, 3600.0), cmd(end, 6400.0)))
        assertEquals(6400.0, s.finishedAt)
    }

    @Test fun abandonTime() {
        assertEquals(1000.0, play(listOf(cmd(start, 0.0), cmd(CommandKind.AbandonMatch("x"), 1000.0))).finishedAt)
    }

    @Test fun decisivePenaltyKick() {
        val commands = mutableListOf(
            cmd(start, 0.0), cmd(end, 2700.0), cmd(start, 3600.0), cmd(end, 6300.0),
            cmd(CommandKind.SetShootoutFirstTeam(Team.HOME), 6400.0),
        )
        var t = 6500.0
        repeat(3) {
            commands += kick(Team.HOME, true, t); t += 10
            commands += kick(Team.AWAY, false, t); t += 10
        }
        val s = play(RuleSet(penalties = true), commands)
        // 홈 3골(6540)에는 원정이 남은 셋을 다 넣으면 3:3이 될 수 있다 → 원정 세 번째 실축(6550)에 끝난다
        assertEquals(6550.0, s.finishedAt)
    }

    /** 끝낸 뒤 기록을 고쳐도 끝난 시각은 그대로 */
    @Test fun laterEditKeepsFinishTime() {
        val s = play(listOf(
            cmd(start, 0.0), goal(Team.HOME, 100.0, 9, id = "g"),
            cmd(end, 2700.0), cmd(start, 3600.0), cmd(end, 6400.0),
            cmd(CommandKind.EditEvent("g", EventPayload.Goal(Team.HOME, 10, false)), 7000.0),
        ))
        assertEquals(6400.0, s.finishedAt)
    }
}

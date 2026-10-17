package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** GPS는 경기 시간(기간 진행 중)에만 켠다 (2026-10-15 사용자 결정: 하프타임·승부차기·종료 후에는 배터리만 쓴다) */
class RouteTrackingTests {
    @Test fun onlyWhilePeriodRuns() {
        assertFalse(makeState(emptyList()).tracksRoute)
        assertTrue(makeState(listOf(CommandKind.StartPeriod to 0.0)).tracksRoute)
        assertFalse(makeState(listOf(CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0)).tracksRoute)
        assertTrue(makeState(listOf(CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0, CommandKind.StartPeriod to 3600.0)).tracksRoute)
    }

    @Test fun offDuringPenaltiesAndAfterFinish() {
        val rules = RuleSet(penalties = true)
        val toPenalties = listOf(
            CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0,
            CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0,
        )
        assertFalse(makeState(rules, toPenalties).tracksRoute)
        assertFalse(makeState(listOf(
            CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0, CommandKind.StartPeriod to 3600.0,
            CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, null, false)) to 4000.0, CommandKind.EndPeriod to 6300.0,
        )).tracksRoute)
    }
}

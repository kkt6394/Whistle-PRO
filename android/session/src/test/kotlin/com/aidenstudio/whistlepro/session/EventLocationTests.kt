package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.MatchEngine
import com.aidenstudio.whistlepro.core.ResolvedEventKind
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun point(t: Double, lat: Double = 37.0, accuracy: Double = 5.0) = RoutePoint(t, lat, 127.0, 1.0, accuracy)
private fun event(p: EventPayload) = CommandKind.RecordEvent(p)

class EventLocationTests {
    @Test fun locatesNearestPointWithinFiveSeconds() {
        val route = listOf(point(95.0, 1.0), point(98.0, 2.0), point(103.0, 3.0), point(110.0, 4.0))
        assertEquals(2.0, EventLocation.locate(route, 100.0)?.latitude)
        assertEquals(3.0, EventLocation.locate(route, 105.0)?.latitude)
    }

    @Test fun noLocationWithoutNearbyPoint() {
        assertNull(EventLocation.locate(listOf(point(90.0), point(106.0)), 100.0))
        assertNull(EventLocation.locate(emptyList(), 100.0))
    }

    @Test fun ignoresInaccuratePoints() {
        val route = listOf(point(100.0, 1.0, 25.0), point(100.0, 2.0, -1.0), point(104.0, 3.0))
        assertEquals(3.0, EventLocation.locate(route, 100.0)?.latitude)
    }

    @Test fun eventPinsSkipShootout() {
        val state = makeState(RuleSet.FIFA_STANDARD.copy(penalties = true), listOf(
            CommandKind.StartPeriod to 0.0,
            event(EventPayload.Card(Team.AWAY, 4, CardKind.YELLOW)) to 100.0,
            CommandKind.EndPeriod to 2700.0, CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0,
            CommandKind.SetShootoutFirstTeam(Team.HOME) to 6400.0,
            event(EventPayload.PenaltyKick(Team.HOME, 9, true)) to 6410.0,
        ))
        val route = listOf(point(100.0, 1.0), point(6410.0, 2.0))
        val pins = EventLocation.pins(state, MatchSetup(), route)
        assertEquals(listOf(ResolvedEventKind.YELLOW), pins.map { it.kind })
        assertEquals(1.0, pins.first().latitude)
        assertEquals("#4", pins.first().detail)
        assertEquals(Team.AWAY, pins.first().team)
    }

    @Test fun editedEventKeepsFirstTimeAndDeletedHasNoPin() {
        val edited = makeRecord(listOf(
            CommandKind.StartPeriod to 0.0,
            event(EventPayload.Goal(Team.HOME, 9, false)) to 100.0,
            event(EventPayload.Card(Team.AWAY, 4, CardKind.YELLOW)) to 200.0,
            CommandKind.EditEvent("c1", EventPayload.Goal(Team.HOME, 10, false)) to 300.0,
            CommandKind.DeleteEvent("c2") to 400.0,
        ))
        val state = MatchEngine.reduce(RuleSet.FIFA_STANDARD, edited.commands)
        val setup = MatchSetup(homeRoster = listOf(Player(10, "Son")))
        val pins = EventLocation.pins(state, setup, listOf(point(100.0, 1.0), point(200.0, 2.0), point(300.0, 3.0)))
        assertEquals(listOf(1.0), pins.map { it.latitude })
        assertEquals(listOf("#10 Son"), pins.map { it.detail })
    }

    @Test fun noPinsWithoutRoute() {
        val state = makeState(listOf(CommandKind.StartPeriod to 0.0, event(EventPayload.Goal(Team.HOME, null, false)) to 10.0))
        assertTrue(EventLocation.pins(state, MatchSetup(), emptyList()).isEmpty())
    }
}

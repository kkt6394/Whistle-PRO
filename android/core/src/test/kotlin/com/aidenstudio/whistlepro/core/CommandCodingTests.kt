package com.aidenstudio.whistlepro.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CommandCodingTests {
    private fun decode(json: String) = Command.fromJson(Json.parseToJsonElement(json).jsonObject)

    @Test fun decodesSimpleCommandWithoutId() {
        val c = decode("""{"type": "startPeriod", "t": 12.5}""")
        assertTrue(c.id.isNotEmpty())
        assertEquals(12.5, c.timestamp)
        assertEquals(CommandKind.StartPeriod, c.kind)
    }

    @Test fun missingIdsAreUnique() {
        val a = decode("""{"type": "startPeriod", "t": 0}""")
        val b = decode("""{"type": "startPeriod", "t": 0}""")
        assertNotEquals(a.id, b.id)
    }

    @Test fun decodesRecordGoal() {
        val c = decode("""{"type": "recordGoal", "t": 100, "id": "g1", "team": "home", "number": 9}""")
        assertEquals("g1", c.id)
        assertEquals(CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)), c.kind)
    }

    @Test fun decodesRecordCardWithoutNumber() {
        val c = decode("""{"type": "recordCard", "t": 1, "team": "away", "kind": "sinBin"}""")
        assertEquals(CommandKind.RecordEvent(EventPayload.Card(Team.AWAY, null, CardKind.SIN_BIN)), c.kind)
    }

    @Test fun decodesEditEventWithNestedPayload() {
        val c = decode("""{"type": "editEvent", "t": 5, "eventId": "g1", "event": {"type": "goal", "team": "away", "number": 11, "ownGoal": true}}""")
        assertEquals(CommandKind.EditEvent("g1", EventPayload.Goal(Team.AWAY, 11, true)), c.kind)
    }

    @Test fun unknownTypeThrows() {
        assertFailsWith<SerializationException> { decode("""{"type": "dance", "t": 0}""") }
        assertFailsWith<SerializationException> { decode("""{"type": "recordDance", "t": 0, "team": "home"}""") }
    }

    @Test fun everyKindRoundTrips() {
        val kinds = listOf(
            CommandKind.SetKickoffTeam(Team.HOME),
            CommandKind.StartPeriod,
            CommandKind.EndPeriod,
            CommandKind.AdjustPeriodLength(1800),
            CommandKind.ToggleStoppage,
            CommandKind.SetShootoutFirstTeam(Team.AWAY),
            CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, null, false)),
            CommandKind.RecordEvent(EventPayload.Card(Team.AWAY, 4, CardKind.YELLOW)),
            CommandKind.RecordEvent(EventPayload.Substitution(Team.HOME, 7, 14)),
            CommandKind.RecordEvent(EventPayload.PenaltyKick(Team.AWAY, 10, true)),
            CommandKind.EditEvent("x", EventPayload.Card(Team.HOME, 3, CardKind.RED)),
            CommandKind.DeleteEvent("x"),
            CommandKind.Undo,
            CommandKind.AbandonMatch("폭우"),
        )
        val commands = kinds.mapIndexed { i, kind -> Command("c$i", i.toDouble(), kind) }
        val text = commandsToJson(commands).toString()
        val back = commandsFromJson(Json.parseToJsonElement(text).jsonArray)
        assertEquals(commands, back)
    }
}

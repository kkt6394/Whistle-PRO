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

    /** Swift(64비트 Int)는 32비트를 넘는 정수를 읽고 엔진이 무시한다. 로그 전체가 실패하면 안 된다. */
    @Test fun integersBeyond32BitsDecodeAndEngineIgnoresThem() {
        val adjust = decode("""{"type": "adjustPeriodLength", "t": 1, "id": "a", "seconds": 3000000000}""")
        val goal = decode("""{"type": "recordGoal", "t": 2, "id": "g", "team": "home", "number": 3000000000}""")
        assertEquals(CommandKind.AdjustPeriodLength(Int.MAX_VALUE), adjust.kind)
        assertEquals(listOf("a"), MatchEngine.reduce(RuleSet.FIFA_STANDARD, listOf(adjust)).ignoredCommandIds)
        assertTrue(goal.kind is CommandKind.RecordEvent)
        assertEquals(CommandKind.AdjustPeriodLength(Int.MIN_VALUE), decode("""{"type": "adjustPeriodLength", "t": 1, "seconds": -3000000000}""").kind)
        // 정수 문법은 Double을 거치지 않고 정확히 읽는다
        assertEquals(CommandKind.AdjustPeriodLength(1800), decode("""{"type": "adjustPeriodLength", "t": 1, "seconds": 1800}""").kind)
        // 64비트도 넘으면 Swift처럼 실패
        assertFailsWith<SerializationException> { decode("""{"type": "adjustPeriodLength", "t": 1, "seconds": 10000000000000000000}""") }
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

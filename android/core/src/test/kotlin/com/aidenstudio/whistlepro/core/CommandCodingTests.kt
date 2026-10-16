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

    /** Swift(64비트 Int)처럼 32비트를 넘는 정수도 그대로 읽고 엔진이 범위 검사로 무시한다. 로그 전체가 실패하면 안 된다. */
    @Test fun integersBeyond32BitsDecodeAndEngineIgnoresThem() {
        val adjust = decode("""{"type": "adjustPeriodLength", "t": 1, "id": "a", "seconds": 3000000000}""")
        val goal = decode("""{"type": "recordGoal", "t": 2, "id": "g", "team": "home", "number": 3000000000}""")
        assertEquals(CommandKind.AdjustPeriodLength(3_000_000_000L), adjust.kind)
        assertEquals(listOf("a"), MatchEngine.reduce(RuleSet.FIFA_STANDARD, listOf(adjust)).ignoredCommandIds)
        assertTrue(goal.kind is CommandKind.RecordEvent)
        assertEquals(CommandKind.AdjustPeriodLength(-3_000_000_000L), decode("""{"type": "adjustPeriodLength", "t": 1, "seconds": -3000000000}""").kind)
        // 정수 문법은 Double을 거치지 않고 정확히 읽는다
        assertEquals(CommandKind.AdjustPeriodLength(1800), decode("""{"type": "adjustPeriodLength", "t": 1, "seconds": 1800}""").kind)
        // 64비트도 넘으면 Swift처럼 실패
        assertFailsWith<SerializationException> { decode("""{"type": "adjustPeriodLength", "t": 1, "seconds": 10000000000000000000}""") }
    }

    /** 선수 번호는 Swift(64비트 Int)처럼 32비트를 넘어도 그대로 읽는다: Int로 고정하면 3000000000과 3000000001이 같은 선수가 됐다 */
    @Test fun playerNumbersBeyond32BitsKeepExactValues() {
        val sub = decode("""{"type": "recordSubstitution", "t": 1, "team": "home", "numberOut": 3000000000, "numberIn": -3000000001}""")
        assertEquals(CommandKind.RecordEvent(EventPayload.Substitution(Team.HOME, 3_000_000_000L, -3_000_000_001L)), sub.kind)
        val first = card(Team.HOME, 3_000_000_000L, CardKind.YELLOW, 10.0)
        val second = card(Team.HOME, 3_000_000_001L, CardKind.YELLOW, 20.0)
        val s = play(listOf(cmd(start, 0.0), first, second))
        assertTrue(s.resolvedEvents.all { it.warnings.isEmpty() })
        // 다시 써도 같은 값
        assertEquals(sub, Command.fromJson(Json.parseToJsonElement(sub.toJson().toString()).jsonObject))
    }

    /** Swift JSONDecoder는 JSON 표준 숫자·true/false만 받는다 */
    @Test fun nonStandardLiteralsAreRejected() {
        for (literal in listOf("NaN", "Infinity", "-Infinity", "12d", "0x1p3", "9f", "1e400", "-1e400", "+5", "01", ".5", "5.", "1e", "TRUE")) {
            assertFailsWith<SerializationException>(literal) { decode("""{"type": "startPeriod", "t": $literal}""") }
            assertFailsWith<SerializationException>(literal) {
                decode("""{"type": "adjustPeriodLength", "t": 1, "seconds": $literal}""")
            }
        }
        for (literal in listOf("TRUE", "True", "1", "0", "yes")) {
            assertFailsWith<SerializationException>(literal) {
                decode("""{"type": "recordGoal", "t": 1, "team": "home", "ownGoal": $literal}""")
            }
        }
        for ((literal, value) in listOf("12" to 12.0, "-0.5" to -0.5, "1e3" to 1000.0, "1E+3" to 1000.0, "2.5e-1" to 0.25, "-0" to -0.0)) {
            assertEquals(value, decode("""{"type": "startPeriod", "t": $literal}""").timestamp, literal)
        }
        assertEquals(CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, null, true)),
            decode("""{"type": "recordGoal", "t": 1, "team": "home", "ownGoal": true}""").kind)
    }

    /** Swift JSONEncoder처럼 NaN·무한대 타임스탬프는 쓰지 않는다 (따옴표 없는 NaN은 Swift가 읽지 못한다) */
    @Test fun nonFiniteTimestampCannotBeEncoded() {
        for (t in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFailsWith<SerializationException>("$t") { Command("c", t, CommandKind.StartPeriod).toJson() }
        }
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

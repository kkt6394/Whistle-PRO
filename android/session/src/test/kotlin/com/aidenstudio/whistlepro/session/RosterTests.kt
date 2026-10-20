package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.Team
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun event(p: EventPayload) = CommandKind.RecordEvent(p)

class RosterTests {
    private val sample = MatchSetup(
        homeRoster = listOf(Player(10, "홍길동"), Player(7, "Kim")),
        awayRoster = listOf(Player(10, "Park")),
    )

    @Test fun decodesSetupWithoutRoster() {
        val json = JsonObject(MatchSetup().toJson() - "homeRoster" - "awayRoster")
        val setup = MatchSetup.fromJson(json)
        assertTrue(setup.homeRoster.isEmpty())
        assertTrue(setup.awayRoster.isEmpty())
    }

    @Test fun rosterRoundTrips() {
        assertEquals(sample, MatchSetup.fromJson(sample.toJson()))
    }

    /** Swift oldDecoderIgnoresRoster 대신: 모르는 키(나중 버전이 추가한 필드)가 있어도 읽는다 */
    @Test fun ignoresUnknownKeys() {
        val json = JsonObject(sample.toJson() + ("futureField" to JsonPrimitive("x")))
        assertEquals(sample, MatchSetup.fromJson(json))
    }

    @Test fun playerNameLooksUpByTeamAndNumber() {
        assertEquals("홍길동", sample.playerName(Team.HOME, 10))
        assertEquals("Park", sample.playerName(Team.AWAY, 10))
        assertNull(sample.playerName(Team.AWAY, 7))
        assertNull(sample.playerName(Team.HOME, null))
    }

    @Test fun parsesPastedLines() {
        val result = Roster.parse("10 홍길동\n7, Kim\n#9\tLee\n\n  \nKim 5\n123 Long\n11\n")
        assertEquals(listOf(Player(7, "Kim"), Player(9, "Lee"), Player(10, "홍길동")), result.players)
        assertEquals(3, result.skippedLines)
    }

    @Test fun parseKeepsLastDuplicateAndTruncatesName() {
        val result = Roster.parse("4 First\r\n4 Second\n5 " + "가".repeat(30))
        assertEquals(listOf("Second", "가".repeat(20)), result.players.map { it.name })
        assertEquals(0, result.skippedLines)
    }

    @Test fun validateFindsIssues() {
        val players = listOf(Player(100, "A"), Player(3, " "), Player(3, "B"), Player(3, "C"), Player(8, "x".repeat(21)))
        assertEquals(
            listOf(RosterIssue.NumberOutOfRange(100), RosterIssue.DuplicateNumber(3), RosterIssue.NameTooLong(8)),
            Roster.validate(players),
        )
        assertEquals(listOf<RosterIssue>(RosterIssue.TooManyPlayers), Roster.validate((0L until 41L).map { Player(it, "P") }))
        assertTrue(Roster.validate(sample.homeRoster).isEmpty())
    }

    @Test fun normalizedTrimsAndSorts() {
        assertEquals(listOf(Player(2, "A"), Player(9, "B")), Roster.normalized(listOf(Player(9, " B "), Player(2, "A"))))
    }

    @Test fun eventRowDetailIncludesName() {
        val state = makeState(listOf(
            CommandKind.StartPeriod to 0.0,
            event(EventPayload.Goal(Team.HOME, 10, false)) to 60.0,
            event(EventPayload.Card(Team.AWAY, 10, CardKind.YELLOW)) to 120.0,
            event(EventPayload.Substitution(Team.HOME, 7, 14)) to 180.0,
            event(EventPayload.Card(Team.AWAY, 23, CardKind.YELLOW)) to 240.0,
        ))
        assertEquals(listOf("#23", "#7 Kim → #14", "#10 Park", "#10 홍길동"), state.eventRows(sample).map { it.detail })
        assertEquals(listOf("#23", "#7 → #14", "#10", "#10"), state.eventRows.map { it.detail })
    }

    @Test fun starterRoundTripsAndOmitsWhenNotTrue() {
        val players = listOf(Player(1, "A", true), Player(2, "B"), Player(3, "C", false))
        val json = players.map { it.toJson() }
        assertEquals(JsonPrimitive(true), json[0]["starter"])
        assertNull(json[1]["starter"])
        assertNull(json[2]["starter"])
        val decoded = json.map { Player.fromJson(it) }
        assertEquals(true, decoded[0].starter)
        assertNull(decoded[1].starter)
        assertNull(decoded[2].starter)
    }

    @Test fun oldPlayerJsonDecodesWithoutStarter() {
        val player = Player.fromJson(JsonObject(mapOf("number" to JsonPrimitive(9), "name" to JsonPrimitive("x"))))
        assertEquals(Player(9, "x"), player)
        assertNull(player.starter)
    }

    @Test fun hasStartersOnlyWhenSomeoneIsMarked() {
        var setup = MatchSetup(homeRoster = listOf(Player(1, "A"), Player(2, "B")))
        assertTrue(!setup.hasStarters(Team.HOME))
        assertTrue(setup.starters(Team.HOME).isEmpty())
        setup = setup.copy(homeRoster = listOf(Player(1, "A"), Player(2, "B", true)))
        assertTrue(setup.hasStarters(Team.HOME))
        assertTrue(!setup.hasStarters(Team.AWAY))
        assertEquals(setOf(2L), setup.starters(Team.HOME))
    }

    /** M7b 리뷰: 붙여넣기 결과가 같은 번호의 이름을 바꾼 것까지 "추가"로 셌다 */
    @Test fun pasteCountsAddedAndRenamedSeparately() {
        val existing = listOf(Player(7, "Kim"), Player(10, "홍길동"))
        val pasted = listOf(Player(7, "Kim"), Player(10, "Son"), Player(11, "Lee"))
        val counts = Roster.pasteCounts(existing, pasted)
        assertEquals(1, counts.added)
        assertEquals(1, counts.renamed)
    }
}

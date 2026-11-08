package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.TeamPair
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Swift ReportOptionsTests와 같은 이름·같은 기대값 */
class ReportOptionsTests {
    private fun report(
        commands: List<Pair<CommandKind, Double>>, setup: MatchSetup = MatchSetup(),
        note: MatchNote? = null, referee: String? = null,
    ) = MatchReport(MatchBundle(makeRecord(setup, commands), null), note, referee)

    private fun goal(team: Team) = CommandKind.RecordEvent(EventPayload.Goal(team, 9, false))
    private fun card(team: Team, number: Long, kind: CardKind) = CommandKind.RecordEvent(EventPayload.Card(team, number, kind))

    /** 1H 22' 옐로 #4 (c1), 2H 옐로 #4 (c4) → 경고 누적 퇴장 */
    private val twoYellows = listOf(
        CommandKind.StartPeriod to 0.0,
        card(Team.AWAY, 4, CardKind.YELLOW) to 1320.0,
        CommandKind.EndPeriod to 2700.0,
        CommandKind.StartPeriod to 3600.0,
        card(Team.AWAY, 4, CardKind.YELLOW) to 4150.0,
        CommandKind.EndPeriod to 6300.0,
    )

    @Test fun oldJsonDecodesWithDefaults() {
        val note = MatchNote.fromJson(parseJson("""{"competition":"리그"}""".toByteArray()) as JsonObject)
        assertTrue(note.cardReasons.isEmpty() && note.reportSections == null)
        val setup = MatchSetup.fromJson(JsonObject(MatchSetup().toJson().filterKeys { it != "reportSections" }))
        assertNull(setup.reportSections)
        assertEquals(
            ReportSections(hidden = setOf(ReportBlock.TIMELINE, ReportBlock.SIGNATURES)),
            ReportSections.fromJson(parseJson("""{"signatures":false}""".toByteArray()) as JsonObject))
    }

    @Test fun emptyFieldsOmitted() {
        val json = MatchNote(competition = "리그").toJson()
        assertFalse(json.containsKey("cardReasons") || json.containsKey("reportSections"))
        assertFalse(MatchSetup().toJson().containsKey("reportSections"))
    }

    @Test fun noteRoundTrips() {
        val note = MatchNote(
            competition = "A", cardReasons = mapOf("c1" to CardReason.CAUTION_DISSENT),
            reportSections = ReportSections(hidden = setOf(ReportBlock.SIGNATURES)))
        assertEquals(note, MatchNote.fromJson(note.toJson()))
        assertEquals(JsonPrimitive("caution.dissent"), (note.toJson()["cardReasons"] as JsonObject)["c1"])
        assertEquals(JsonPrimitive(false), (note.toJson()["reportSections"] as JsonObject)["signatures"])
        assertFalse(note.isEmpty)
        assertFalse(MatchNote(cardReasons = mapOf("c1" to CardReason.SEND_OFF_VIOLENT)).isEmpty)
    }

    @Test fun setupRoundTripsSections() {
        val setup = MatchSetup(reportSections = ReportSections(hidden = setOf(ReportBlock.TOTALS)))
        assertEquals(setup.reportSections, MatchSetup.fromJson(setup.toJson()).reportSections)
    }

    @Test fun unknownReasonKeyIgnored() {
        val json = buildJsonObject {
            put("cardReasons", buildJsonObject {
                put("c1", JsonPrimitive("caution.zzz"))
                put("c2", JsonPrimitive("sendOff.violent"))
            })
        }
        assertEquals(mapOf("c2" to CardReason.SEND_OFF_VIOLENT), MatchNote.fromJson(json).cardReasons)
    }

    @Test fun editedKeepsReasonsAndSections() {
        val old = MatchNote(
            competition = "A", cardReasons = mapOf("e1" to CardReason.CAUTION_DISSENT),
            reportSections = ReportSections(hidden = setOf(ReportBlock.SIGNATURES)))
        val recorded = TeamColors.from(MatchSetup())
        val new = MatchNote.edited("B", null, recorded, recorded, keeping = old)
        assertTrue(new.competition == "B" && new.cardReasons == old.cardReasons && new.reportSections == old.reportSections)
        assertTrue(MatchNote.edited("B", null, recorded, recorded).cardReasons.isEmpty())
    }

    @Test fun sectionsPrecedence() {
        val fromSetup = ReportSections(hidden = setOf(ReportBlock.TOTALS))
        val fromNote = ReportSections(hidden = emptySet())
        val commands = listOf(CommandKind.StartPeriod to 0.0)
        val none = report(commands)
        assertTrue(none.sections == ReportSections.STANDARD && !none.sectionsFromMatch)
        val setupOnly = report(commands, MatchSetup(reportSections = fromSetup))
        assertTrue(setupOnly.sections == fromSetup && !setupOnly.sectionsFromMatch)
        val both = report(commands, MatchSetup(reportSections = fromSetup), MatchNote(reportSections = fromNote))
        assertTrue(both.sections == fromNote && both.sectionsFromMatch)
    }

    @Test fun secondYellowReasons() {
        val note = MatchNote(cardReasons = mapOf("c1" to CardReason.CAUTION_DISSENT, "c4" to CardReason.CAUTION_UNSPORTING))
        val r = report(twoYellows, note = note)
        assertEquals(listOf("c1", "c4"), r.cautions.map { it.eventId })
        assertEquals(listOf("c4"), r.sendOffs.map { it.eventId })
        val second = r.sendOffs[0]
        assertEquals(CardReason.CAUTION_UNSPORTING, r.cautionReason(second))
        assertEquals(CardReason.SEND_OFF_SECOND_CAUTION, r.sendOffReason(second))
        assertTrue(r.missingReasons.isEmpty())

        val partial = report(twoYellows, note = MatchNote(cardReasons = mapOf("c1" to CardReason.CAUTION_DISSENT)))
        assertEquals(listOf("c4"), partial.missingReasons.map { it.eventId })
    }

    @Test fun penaltyGoalsRoundTripAndKept() {
        val note = MatchNote(penaltyGoals = setOf("c2", "c1"))
        assertFalse(note.isEmpty)
        val json = note.toJson()
        assertEquals("[\"c1\",\"c2\"]", json["penaltyGoals"].toString())
        assertEquals(note, MatchNote.fromJson(json))
        assertFalse(MatchNote(competition = "A").toJson().containsKey("penaltyGoals"))
        val recorded = TeamColors.from(MatchSetup())
        assertEquals(setOf("c1", "c2"), MatchNote.edited("B", null, recorded, recorded, keeping = note).penaltyGoals)
    }

    /** 표시한 골만 페널티킥, 자책골은 ID가 들어 있어도 아니다 */
    @Test fun penaltyGoalOnlyForGoals() {
        val commands = listOf(
            CommandKind.StartPeriod to 0.0,
            CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)) to 600.0,
            CommandKind.RecordEvent(EventPayload.Goal(Team.AWAY, 11, false)) to 900.0,
            CommandKind.RecordEvent(EventPayload.Goal(Team.AWAY, 4, true)) to 1200.0,
        )
        val r = report(commands, note = MatchNote(penaltyGoals = setOf("c2", "c3")))
        assertEquals(listOf("c1", "c2", "c3"), r.goals.map { it.eventId })
        assertEquals(listOf(false, true, false), r.goals.map(r::isPenalty))
        val plain = report(commands)
        assertFalse(plain.goals.any(plain::isPenalty))
    }

    @Test fun sendOffReasonFromNoteForRed() {
        val commands = listOf(CommandKind.StartPeriod to 0.0, card(Team.HOME, 3, CardKind.RED) to 600.0)
        val r = report(commands, note = MatchNote(cardReasons = mapOf("c1" to CardReason.SEND_OFF_VIOLENT)))
        assertTrue(r.sendOffs.size == 1 && r.cautions.isEmpty())
        assertEquals(CardReason.SEND_OFF_VIOLENT, r.sendOffReason(r.sendOffs[0]))
        assertEquals(1, report(commands).missingReasons.size)
    }

    @Test fun sinBinTableShown() {
        val commands = listOf(CommandKind.StartPeriod to 0.0)
        val on = MatchSetup(rules = RuleSet(sinBin = true))
        assertTrue(report(commands, on).showsSinBinTable)
        assertFalse(report(commands).showsSinBinTable)
        val withBin = report(listOf(CommandKind.StartPeriod to 0.0, card(Team.HOME, 2, CardKind.SIN_BIN) to 300.0), on)
        assertTrue(withBin.showsSinBinTable && withBin.sinBins.size == 1)
        assertNull(withBin.sinBinReason(withBin.sinBins[0]))
        assertEquals(1, withBin.missingReasons.size)
    }

    @Test fun resultForAbandonedHasNoWinner() {
        val r = report(listOf(CommandKind.StartPeriod to 0.0, goal(Team.HOME) to 300.0, CommandKind.AbandonMatch("폭우") to 1200.0))
        assertEquals(ReportResult.Unfinished(ReportOutcome.Abandoned("폭우")), r.result)
    }

    @Test fun shootoutWinner() {
        val setup = MatchSetup(rules = RuleSet(penalties = true))
        val commands = mutableListOf(
            CommandKind.StartPeriod to 0.0,
            goal(Team.HOME) to 300.0, goal(Team.AWAY) to 600.0,
            CommandKind.EndPeriod to 2700.0, CommandKind.StartPeriod to 3600.0,
            goal(Team.HOME) to 3900.0, goal(Team.AWAY) to 4200.0,
            CommandKind.EndPeriod to 6300.0, CommandKind.SetShootoutFirstTeam(Team.HOME) to 6400.0,
        )
        var t = 6410.0
        val kicks = listOf(
            Team.HOME to true, Team.AWAY to true, Team.HOME to true, Team.AWAY to true,
            Team.HOME to true, Team.AWAY to true, Team.HOME to true, Team.AWAY to false,
            Team.HOME to false, Team.AWAY to false)
        for ((team, scored) in kicks) {
            commands += CommandKind.RecordEvent(EventPayload.PenaltyKick(team, null, scored)) to t
            t += 10
        }
        val r = report(commands, setup)
        assertEquals(TeamPair(2, 2), r.score)
        assertEquals(ReportResult.Win(Team.HOME, TeamPair(4, 3)), r.result)
        assertEquals(TimelineRow.Shootout(TeamPair(4, 3)), r.timelineRows.last())

        val drawn = report(listOf(
            CommandKind.StartPeriod to 0.0, goal(Team.HOME) to 300.0, goal(Team.AWAY) to 600.0,
            CommandKind.EndPeriod to 2700.0, CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0))
        assertEquals(ReportResult.Draw, drawn.result)
    }

    @Test fun emptyMatchHasOnlyPeriodEnds() {
        val r = report(listOf(
            CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0,
            CommandKind.StartPeriod to 3600.0, CommandKind.EndPeriod to 6300.0))
        assertEquals(
            listOf(TimelineRow.PeriodEnd("1H", TeamPair(0, 0)), TimelineRow.PeriodEnd("2H", TeamPair(0, 0))),
            r.timelineRows)
        assertTrue(r.cautions.isEmpty() && r.sendOffs.isEmpty() && r.sinBins.isEmpty() && r.goals.isEmpty())
    }

    @Test fun timelineRowsOrder() {
        val r = report(listOf(
            CommandKind.StartPeriod to 0.0,
            goal(Team.HOME) to 650.0,
            card(Team.AWAY, 4, CardKind.YELLOW) to 1320.0,
            goal(Team.AWAY) to 2150.0,
            CommandKind.EndPeriod to 2700.0, CommandKind.StartPeriod to 3600.0,
            CommandKind.RecordEvent(EventPayload.Substitution(Team.HOME, 7, 14)) to 3900.0,
            CommandKind.EndPeriod to 6300.0,
        ))
        val lines = r.timeline
        assertEquals(4, lines.size)
        assertEquals(
            listOf(
                TimelineRow.Event(lines[0]), TimelineRow.Event(lines[1]), TimelineRow.Event(lines[2]),
                TimelineRow.PeriodEnd("1H", TeamPair(1, 1)),
                TimelineRow.Event(lines[3]),
                TimelineRow.PeriodEnd("2H", TeamPair(1, 1)),
            ), r.timelineRows)
    }

    @Test fun unfinishedLastPeriodHasNoEnd() {
        val r = report(listOf(CommandKind.StartPeriod to 0.0, CommandKind.EndPeriod to 2700.0, CommandKind.StartPeriod to 3600.0))
        assertEquals(listOf(TimelineRow.PeriodEnd("1H", TeamPair(0, 0))), r.timelineRows)
        assertEquals(ReportResult.Unfinished(ReportOutcome.InProgress), r.result)
    }

    @Test fun missingInfoOrder() {
        val commands = listOf(CommandKind.StartPeriod to 0.0)
        val r = report(commands, note = MatchNote(competition = "리그"))
        assertEquals(listOf(ReportMissing.REFEREE, ReportMissing.VENUE), r.missingInfo)
        val full = report(commands, note = MatchNote(competition = "리그", venue = "구장"), referee = "홍길동")
        assertTrue(full.missingInfo.isEmpty())
        assertEquals(listOf(ReportMissing.REFEREE, ReportMissing.COMPETITION, ReportMissing.VENUE), report(commands).missingInfo)
    }

    @Test fun eventIDCarried() {
        assertEquals("c1", report(twoYellows).cautions.first().eventId)
    }
}

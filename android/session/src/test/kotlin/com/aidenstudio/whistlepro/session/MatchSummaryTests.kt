package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.MatchEngine
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 최종 리뷰: 목록이 모든 경기의 운동 데이터까지 읽지 않도록 가벼운 요약을 따로 둔다 */
class MatchSummaryTests {
    private val match = listOf(
        CommandKind.StartPeriod to 100.0,
        CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)) to 600.0,
        CommandKind.EndPeriod to 2800.0, CommandKind.StartPeriod to 3700.0, CommandKind.EndPeriod to 6400.0,
    )

    private fun resource(name: String): JsonObject =
        Json.parseToJsonElement(javaClass.classLoader.getResource(name)!!.readText()).jsonObject

    @Test fun summaryDescribesMatch() {
        val summary = MatchSummary.of(makeRecord(MatchSetup(homeName = "A", awayName = "B"), match, createdAt = 50.0))
        assertEquals("A", summary.homeName)
        assertEquals("B", summary.awayName)
        assertEquals(1, summary.homeScore)
        assertEquals(0, summary.awayScore)
        assertEquals(100.0, summary.startedAt)
        assertEquals(50.0, summary.createdAt)
        assertEquals(ReportOutcome.Completed, summary.outcome)
        assertNull(summary.shootout)
    }

    @Test fun abandonedSummary() {
        val summary = MatchSummary.of(makeRecord(listOf(CommandKind.StartPeriod to 0.0, CommandKind.AbandonMatch("폭우") to 10.0)))
        assertEquals(ReportOutcome.Abandoned("폭우"), summary.outcome)
    }

    /** 지난 경기 리뷰: 워치가 이미 재생한 상태로 요약을 만들어 같은 경기를 두 번 재생하지 않는다 */
    @Test fun summaryFromReducedStateMatches() {
        val record = makeRecord(MatchSetup(homeName = "A", awayName = "B"), match, createdAt = 50.0)
        val state = MatchEngine.reduce(record.setup.rules, record.commands)
        assertEquals(MatchSummary.of(record), MatchSummary.of(record, state))
    }

    /** 통계 값이 없는 옛 요약은 최신이 아니고, 값이 없는 키는 JSON에 쓰지 않는다 (시즌 통계는 0으로 센다) */
    @Test fun oldSummariesWithoutValuesCountAsZero() {
        val old = MatchSummary.of(makeRecord(emptyList(), createdAt = 5.0))
            .copy(summaryVersion = null, goals = null, yellows = null)
        assertFalse(old.isCurrent)
        assertEquals(0, old.goals ?: 0)
        assertEquals(0, old.yellows ?: 0)
        val json = old.toJson()
        assertFalse("goals" in json || "summaryVersion" in json || "shootout" in json)
        assertEquals(old, MatchSummary.fromJson(Json.parseToJsonElement(json.toString()).jsonObject))
    }

    /** Kotlin 추가: Swift Codable 형식(outcome은 {"abandoned":{"_0":...}})의 요약을 읽고 다시 쓰면 같다 */
    @Test fun readsSwiftSummary() {
        val json = resource("swift-summary.json")
        val s = MatchSummary.fromJson(json)
        assertEquals(UUID.fromString("6F9619FF-8B86-D011-B42D-00C04FC964FF"), s.id)
        assertEquals("FC 서울", s.homeName)
        assertEquals(TeamColor("#FF3B30"), s.homeColor)
        assertEquals(listOf(4, 3), s.shootout)
        assertEquals(ReportOutcome.Abandoned("폭우"), s.outcome)
        assertEquals(4, s.summaryVersion)
        assertEquals(2, s.goals)
        assertEquals(1.5, s.distance)
        assertEquals(SpeedZones(walking = 100.0, jogging = 200.0, running = 300.0, sprinting = 50.0), s.zones)
        assertEquals(2, s.periodCount)
        assertEquals(45, s.periodMinutes)
        assertTrue(s.isCurrent)
        assertEquals(s, MatchSummary.fromJson(Json.parseToJsonElement(s.toJson().toString()).jsonObject))
        assertEquals(json.keys, s.toJson().keys)
        assertEquals(json["outcome"], s.toJson()["outcome"])
    }

    /** 워치 지난 경기 상세: 골(자책골 포함)·옐로(경고 누적 포함)·퇴장(경고 누적 포함)·일시 퇴장 수 */
    @Test fun eventCountsForPastMatchDetail() {
        val commands = listOf(
            CommandKind.StartPeriod to 100.0,
            CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)) to 200.0,
            CommandKind.RecordEvent(EventPayload.Goal(Team.AWAY, 4, true)) to 300.0,
            CommandKind.RecordEvent(EventPayload.Card(Team.AWAY, 5, CardKind.YELLOW)) to 400.0,
            CommandKind.RecordEvent(EventPayload.Card(Team.AWAY, 5, CardKind.YELLOW)) to 500.0,
            CommandKind.RecordEvent(EventPayload.Card(Team.HOME, 3, CardKind.RED)) to 600.0,
            CommandKind.RecordEvent(EventPayload.Card(Team.HOME, 8, CardKind.SIN_BIN)) to 700.0,
        )
        val record = makeRecord(MatchSetup(rules = RuleSet(sinBin = true)), commands)
        val state = MatchEngine.reduce(record.setup.rules, record.commands)
        val summary = MatchSummary.of(record, state).withEventCounts(state)
        assertEquals(2, summary.goals)
        assertEquals(2, summary.yellows)
        assertEquals(2, summary.reds)
        assertEquals(1, summary.sinBins)
        // 통계 버전은 그대로: 폰 보관함은 이 요약을 최신 통계로 보지 않는다
        assertNull(summary.summaryVersion)
    }
}

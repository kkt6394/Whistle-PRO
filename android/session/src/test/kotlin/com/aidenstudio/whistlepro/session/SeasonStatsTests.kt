package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CardKind
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun summary(
    startedAt: Double, outcome: ReportOutcome = ReportOutcome.Completed, goals: Int = 0, yellows: Int = 0,
    reds: Int = 0, added: Int = 0, distance: Double? = null, heartRate: Int? = null,
    sprints: Int? = null, zones: SpeedZones? = null,
): MatchSummary = MatchSummary.of(makeRecord(emptyList(), createdAt = startedAt)).copy(
    startedAt = startedAt, outcome = outcome, goals = goals, yellows = yellows, reds = reds, sinBins = 0,
    addedSeconds = added, distance = distance, averageHeartRate = heartRate, sprints = sprints, zones = zones,
)

class SeasonStatsTests {
    @Test fun emptyHasNoAverages() {
        val stats = SeasonStats.aggregate(emptyList())
        assertEquals(0, stats.all.matches)
        assertNull(stats.cardsPerMatch)
        assertNull(stats.distancePerMatch)
        assertNull(stats.averageHeartRate)
    }

    @Test fun abandonedCountsButIsNotAveraged() {
        val stats = SeasonStats.aggregate(listOf(
            summary(1.0, goals = 3, yellows = 2, added = 240, distance = 10000.0, heartRate = 150, sprints = 20),
            summary(2.0, goals = 1, yellows = 1, reds = 1, added = 120, distance = 8000.0, heartRate = 160, sprints = 10),
            summary(3.0, outcome = ReportOutcome.Abandoned("weather"), goals = 5, yellows = 4, distance = 1000.0, heartRate = 100, sprints = 1),
        ))
        assertEquals(3, stats.all.matches)
        assertEquals(2, stats.completed.matches)
        assertEquals(1, stats.abandoned)
        assertEquals(9, stats.all.goals)
        assertEquals(8, stats.all.cards)
        assertEquals(2.0, stats.goalsPerMatch)
        assertEquals(2.0, stats.cardsPerMatch)
        assertEquals(180.0, stats.addedSecondsPerMatch)
        assertEquals(9000.0, stats.distancePerMatch)
        assertEquals(15.0, stats.sprintsPerMatch)
        assertEquals(155, stats.averageHeartRate)
    }

    /** M7a 리뷰: 거리 0인 운동 기록(수집 실패 등)은 경기당 이동거리를 낮추지 않는다 */
    @Test fun zeroDistanceIsNotAWorkoutMatch() {
        val stats = SeasonStats.aggregate(listOf(summary(1.0, distance = 10000.0), summary(2.0, distance = 0.0)))
        assertEquals(10000.0, stats.distancePerMatch)
    }

    /** M7a 리뷰: 진행 중 경기는 "중단"으로 세지 않는다 */
    @Test fun inProgressIsNotAbandoned() {
        val stats = SeasonStats.aggregate(listOf(
            summary(1.0), summary(2.0, outcome = ReportOutcome.InProgress), summary(3.0, outcome = ReportOutcome.Abandoned("x"))))
        assertEquals(1, stats.abandoned)
    }

    /** M7a 리뷰: 경고 누적 퇴장은 꺼낸 옐로에도 센다 (옐로 두 장 + 레드 한 장) */
    @Test fun secondYellowCountsAsYellowShown() {
        val record = makeRecord(listOf(
            CommandKind.StartPeriod to 0.0,
            CommandKind.RecordEvent(EventPayload.Card(Team.HOME, 4, CardKind.YELLOW)) to 100.0,
            CommandKind.RecordEvent(EventPayload.Card(Team.HOME, 4, CardKind.YELLOW)) to 200.0,
            CommandKind.EndPeriod to 2700.0,
        ))
        val s = MatchSummary.of(MatchBundle(record, null))
        assertEquals(2, s.yellows)
        assertEquals(1, s.reds)
        assertTrue(MatchSummary.CURRENT_VERSION >= 3)
    }

    @Test fun matchesWithoutWorkoutDoNotLowerWorkoutAverages() {
        val stats = SeasonStats.aggregate(listOf(
            summary(1.0, yellows = 2, distance = 10000.0, heartRate = 150, sprints = 20),
            summary(2.0, yellows = 4),
        ))
        assertEquals(3.0, stats.cardsPerMatch)
        assertEquals(10000.0, stats.distancePerMatch)
        assertEquals(20.0, stats.sprintsPerMatch)
        assertEquals(150, stats.averageHeartRate)
    }

    @Test fun rangeIncludesFromAndExcludesTo() {
        val list = listOf(summary(99.0), summary(100.0), summary(199.0), summary(200.0))
        assertEquals(2, SeasonStats.aggregate(list, from = 100.0, to = 200.0).all.matches)
        assertEquals(3, SeasonStats.aggregate(list, from = 100.0).all.matches)
        assertEquals(3, SeasonStats.aggregate(list, to = 200.0).all.matches)
    }

    @Test fun oldSummariesWithoutValuesCountAsZero() {
        val old = MatchSummary.of(makeRecord(emptyList(), createdAt = 5.0)).copy(summaryVersion = null, goals = null, yellows = null)
        val stats = SeasonStats.aggregate(listOf(old))
        assertEquals(1, stats.all.matches)
        assertEquals(0, stats.all.goals)
    }

    @Test fun zonesAreSummed() {
        val stats = SeasonStats.aggregate(listOf(
            summary(1.0, distance = 10.0, zones = SpeedZones(walking = 1.0, sprinting = 2.0)),
            summary(2.0, distance = 10.0, zones = SpeedZones(walking = 3.0, jogging = 1.0)),
            summary(3.0),
        ))
        assertEquals(SpeedZones(walking = 4.0, jogging = 1.0, sprinting = 2.0), stats.all.zones)
    }
}

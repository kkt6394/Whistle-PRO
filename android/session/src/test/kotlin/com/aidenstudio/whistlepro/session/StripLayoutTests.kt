package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.ResolvedEventKind
import com.aidenstudio.whistlepro.core.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StripLayoutTests {
    private val periods = listOf(
        PeriodSummary("1H", 2820, 0, 120, 2700),
        PeriodSummary("2H", 2700, 0, 0, 2700),
    )

    private fun line(team: Team, kind: ResolvedEventKind, period: Int, second: Long) =
        ReportLine(EventMinute.Regular(1), team, kind, "", period, second)

    private fun assertXs(expected: List<Double>, actual: List<Double>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 1e-9) }
    }

    @Test fun marksScaleByPlayed() {
        val marks = StripLayout.marks(
            periods, listOf(line(Team.HOME, ResolvedEventKind.GOAL, 0, 1410), line(Team.AWAY, ResolvedEventKind.GOAL, 1, 0)), 0.05)
        assertXs(listOf(1410.0 / 5520, 2820.0 / 5520), marks.map { it.x })
        assertEquals(listOf(0, 0), marks.map { it.row })
    }

    @Test fun emptyLinesGiveNoMarks() {
        assertTrue(StripLayout.marks(periods, emptyList(), 0.05).isEmpty())
    }

    @Test fun zeroPlayedGivesNothing() {
        val p = listOf(PeriodSummary("1H", 0, 0, 0, 2700))
        assertTrue(StripLayout.marks(p, listOf(line(Team.HOME, ResolvedEventKind.GOAL, 0, 0)), 0.05).isEmpty())
        assertTrue(StripLayout.labels(p, false).isEmpty())
    }

    @Test fun crowdedMarksStagger() {
        val lines = listOf(
            line(Team.HOME, ResolvedEventKind.YELLOW, 0, 2710), line(Team.HOME, ResolvedEventKind.YELLOW, 0, 2770),
            line(Team.HOME, ResolvedEventKind.YELLOW, 0, 2800), line(Team.AWAY, ResolvedEventKind.YELLOW, 0, 2780),
        )
        assertEquals(listOf(0, 1, 0, 0), StripLayout.marks(periods, lines, 0.05).map { it.row })
    }

    @Test fun boundariesAndLabelsCompleted() {
        assertXs(listOf(2820.0 / 5520), StripLayout.boundaries(periods))
        val labels = StripLayout.labels(periods, true)
        assertEquals(
            listOf<EventMinute>(EventMinute.Regular(0), EventMinute.Regular(45), EventMinute.Regular(90)),
            labels.map { it.minute })
        assertXs(listOf(0.0, 2820.0 / 5520, 1.0), labels.map { it.x })
    }

    @Test fun abandonedEndLabel() {
        val p = listOf(periods[0], PeriodSummary("2H", 900, 0, 0, 2700))
        assertEquals(StripLabel(1.0, EventMinute.Regular(60)), StripLayout.labels(p, false).last())
        val q = listOf(PeriodSummary("1H", 2880, 0, 180, 2700))
        assertEquals(StripLabel(1.0, EventMinute.Added(45, 3)), StripLayout.labels(q, false).last())
    }

    @Test fun abandonedExactlyAtNominalHasNoPlusZero() {
        // 규정 시간에 딱 멈추면 "90+0'"이 아니라 정규 분
        val p = listOf(periods[0], PeriodSummary("2H", 2700, 0, 0, 2700))
        assertEquals(StripLabel(1.0, EventMinute.Regular((2700L + 2700) / 60)), StripLayout.labels(p, false).last())
    }

    @Test fun restGoalSitsAtPeriodEnd() {
        val marks = StripLayout.marks(periods, listOf(line(Team.HOME, ResolvedEventKind.GOAL, 0, 2820)), 0.05)
        assertEquals(2820.0 / 5520, marks.first().x, 1e-9)
    }

    @Test fun visibleLabelsDropOnlyTheCrowdedBoundary() {
        // 연장: 0' 45' 90' 105' 120' — 105'만 끝 라벨과 붙는다
        val labels = listOf(0.0, 0.375, 0.75, 0.875, 1.0).map { StripLabel(it, EventMinute.Regular((it * 120).toLong())) }
        assertEquals(listOf(0, 1, 2, 4), StripLayout.visibleLabelIndices(labels, 0.2))
        assertEquals(listOf(0, 1, 2, 3, 4), StripLayout.visibleLabelIndices(labels, 0.1))
        assertEquals(listOf(0, 1), StripLayout.visibleLabelIndices(labels.take(2), 0.9))
    }
}

package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.Team
import kotlin.math.abs

/** 경기 띠의 한 표시 (골·카드 위치) */
data class StripMark(
    val lineIndex: Int,
    /** 0…1 */
    val x: Double,
    /** 0 또는 1 (가까운 표시끼리 위아래로 엇갈린다) */
    val row: Int,
)

data class StripLabel(val x: Double, val minute: EventMinute)

/** 경기 띠 좌표·라벨 계산. 구간 폭은 기간마다 실제 진행 시간(played)에 비례한다 (Swift `StripLayout`과 같다). */
object StripLayout {
    /**
     * x = (앞 기간 played 합 + second) / 전체 played 합. period·second가 null인 줄은 건너뛴다.
     * row: 같은 team의 직전 mark가 row 0이고 |Δx| < minGap이면 1, 그 밖엔 0. played 합이 0이면 빈 목록.
     */
    fun marks(periods: List<PeriodSummary>, lines: List<ReportLine>, minGap: Double): List<StripMark> {
        val total = periods.sumOf { it.played }
        if (total <= 0) return emptyList()
        val result = mutableListOf<StripMark>()
        val last = mutableMapOf<Team, StripMark>()
        lines.forEachIndexed { index, line ->
            val p = line.period ?: return@forEachIndexed
            val second = line.second ?: return@forEachIndexed
            if (p !in periods.indices) return@forEachIndexed
            val before = periods.take(p).sumOf { it.played }
            val x = (before + second).toDouble() / total.toDouble()
            var row = 0
            val prev = last[line.team]
            if (prev != null && prev.row == 0 && abs(x - prev.x) < minGap) row = 1
            val mark = StripMark(index, x, row)
            result += mark
            last[line.team] = mark
        }
        return result
    }

    /** 기간 경계 x (periods.size - 1개). played 합이 0이면 빈 목록. */
    fun boundaries(periods: List<PeriodSummary>): List<Double> {
        val total = periods.sumOf { it.played }
        if (total <= 0) return emptyList()
        var sum = 0L
        return periods.dropLast(1).map { sum += it.played; sum.toDouble() / total.toDouble() }
    }

    /**
     * 양 끝 + 경계 라벨. 0은 Regular(0). 경계·정상 종료 끝은 규정 누적 분.
     * completed == false면 끝 라벨은 마지막 기간이 멈춘 분 (규정 시간에 딱 멈췄으면 정규 분).
     */
    fun labels(periods: List<PeriodSummary>, completed: Boolean): List<StripLabel> {
        val total = periods.sumOf { it.played }
        val lastPeriod = periods.lastOrNull()
        if (total <= 0 || lastPeriod == null) return emptyList()
        val result = mutableListOf(StripLabel(0.0, EventMinute.Regular(0)))
        var nominalSum = 0L
        boundaries(periods).forEachIndexed { i, x ->
            nominalSum += periods[i].nominal
            result += StripLabel(x, EventMinute.Regular(nominalSum / 60))
        }
        val end = when {
            completed -> EventMinute.Regular((nominalSum + lastPeriod.nominal) / 60)
            lastPeriod.played <= lastPeriod.nominal -> EventMinute.Regular((nominalSum + lastPeriod.played) / 60)
            else -> EventMinute.Added((nominalSum + lastPeriod.nominal) / 60, (lastPeriod.played - lastPeriod.nominal + 59) / 60)
        }
        result += StripLabel(1.0, end)
        return result
    }

    /**
     * 보일 라벨 번호: 양 끝은 늘 남기고, 가운데 라벨은 앞에 남긴 라벨·끝 라벨과 [gap](0…1 폭 비율) 이상 떨어질 때만.
     * (연장 경기의 `105'`·`120'`처럼 끝에 붙는 경계 라벨만 빠지고 `45'`·`90'`은 남는다)
     */
    fun visibleLabelIndices(labels: List<StripLabel>, gap: Double): List<Int> {
        if (labels.size <= 2) return labels.indices.toList()
        val last = labels.lastIndex
        val kept = mutableListOf(0)
        for (i in 1 until last) {
            if (labels[i].x - labels[kept.last()].x >= gap && labels[last].x - labels[i].x >= gap) kept += i
        }
        kept += last
        return kept
    }
}

package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.ResolvedEventKind
import com.aidenstudio.whistlepro.core.Team

/** 팀별 합계 (제출용 보고서 아래 칸) */
data class ReportTotals(
    /** 골 + 자책골 (자책골은 득점한 팀에 센다) */
    val goals: Int,
    /** 꺼낸 옐로: 경고 누적 퇴장도 옐로를 한 장 더 꺼낸다 */
    val yellows: Int,
    /** 경고 누적 퇴장 + 레드 + 일시 퇴장 */
    val dismissals: Int,
    val substitutions: Int,
) {
    companion object {
        fun of(lines: List<ReportLine>) = ReportTotals(
            goals = lines.count { it.kind == ResolvedEventKind.GOAL || it.kind == ResolvedEventKind.OWN_GOAL },
            yellows = lines.count { it.kind == ResolvedEventKind.YELLOW || it.kind == ResolvedEventKind.SECOND_YELLOW },
            dismissals = lines.count {
                it.kind == ResolvedEventKind.SECOND_YELLOW || it.kind == ResolvedEventKind.RED || it.kind == ResolvedEventKind.SIN_BIN
            },
            substitutions = lines.count { it.kind == ResolvedEventKind.SUBSTITUTION },
        )
    }
}

/** 두 열 보고서의 한 줄: 홈 칸과 원정 칸 (없으면 비운다) */
data class ReportColumnRow(val home: ReportLine?, val away: ReportLine?)

/** `timeline`을 팀별로 나눠 i번째끼리 묶는다. 길이는 긴 쪽. */
val MatchReport.columnRows: List<ReportColumnRow>
    get() {
        val home = timeline.filter { it.columnTeam == Team.HOME }
        val away = timeline.filter { it.columnTeam == Team.AWAY }
        return (0 until maxOf(home.size, away.size)).map { i -> ReportColumnRow(home.getOrNull(i), away.getOrNull(i)) }
    }

/**
 * SNS 카드(360×640pt) 자리 (critique 11-09, 사용자 시안 B). 경기장·히트맵은 로고·날짜 아래와 팀 줄 위 사이 칸에만 들어가
 * 경기장 크기·모양이 달라도 글자와 겹치지 않는다. 아래에는 팀 → 점수 → 골 한 줄 → 숫자 셋. 아이폰 `SNSCardLayout`과 같은 값
 */
object SNSCardLayout {
    const val WIDTH = 360.0
    const val HEIGHT = 640.0
    /** 경기장 칸 위·아래 (pt). 위는 스토리가 덮는 84pt + 로고·날짜, 아래는 팀 줄 위 */
    const val BOX_TOP = 152.0
    const val BOX_BOTTOM = 415.0
    /** 골 한 줄에 넣는 최대 개수 (넘으면 앞 4개 + "+N") */
    const val MAXIMUM_GOALS = 5

    /** 골·자책골 줄만, 시간순. 많으면 앞 (최대-1)개와 나머지 개수 */
    fun goals(lines: List<ReportLine>): Pair<List<ReportLine>, Int> {
        val goals = lines.filter { it.kind == ResolvedEventKind.GOAL || it.kind == ResolvedEventKind.OWN_GOAL }
        if (goals.size <= MAXIMUM_GOALS) return goals to 0
        return goals.take(MAXIMUM_GOALS - 1) to (goals.size - (MAXIMUM_GOALS - 1))
    }

    /** 위성 사진에서 경기장(+골라인 쪽 여백)이 들어갈 세로 띠와 가운데 (화면 높이 비율) */
    val SATELLITE_BAND get() = (BOX_BOTTOM - BOX_TOP) / HEIGHT
    val SATELLITE_CENTER_Y get() = (BOX_TOP + BOX_BOTTOM) / 2 / HEIGHT

    /**
     * 일반 지도 바탕: 히트맵 범위(남북·동서 m, 둘레 30%·최소 90 m)를 칸 안에 넣는 1pt당 m와,
     * 히트맵 가운데가 칸 가운데에 오도록 지도 가운데를 남쪽으로 옮길 거리(m)
     */
    fun mapFrame(northSouthMeters: Double, eastWestMeters: Double): Pair<Double, Double> {
        val northSouth = kotlin.math.max(northSouthMeters * 1.3, 90.0)
        val eastWest = kotlin.math.max(eastWestMeters * 1.3, 90.0)
        val metersPerPoint = kotlin.math.max(eastWest / WIDTH, northSouth / (BOX_BOTTOM - BOX_TOP))
        return metersPerPoint to (HEIGHT / 2 - (BOX_TOP + BOX_BOTTOM) / 2) * metersPerPoint
    }
}

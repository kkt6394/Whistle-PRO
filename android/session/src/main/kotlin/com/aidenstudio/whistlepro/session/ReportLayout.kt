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

/** SNS 카드의 이벤트 칩: 8개까지 보이고 넘으면 7개 + "+N" */
object SNSCardChips {
    const val MAXIMUM = 8

    /** (보이는 줄, 더 있는 개수) */
    fun split(lines: List<ReportLine>): Pair<List<ReportLine>, Int> {
        if (lines.size <= MAXIMUM) return lines to 0
        return lines.take(MAXIMUM - 1) to (lines.size - (MAXIMUM - 1))
    }
}

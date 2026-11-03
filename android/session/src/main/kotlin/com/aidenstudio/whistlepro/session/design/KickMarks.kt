package com.aidenstudio.whistlepro.session.design

import com.aidenstudio.whistlepro.core.TeamPair

enum class KickMark { SCORED, MISSED, PENDING }

object KickMarks {
    /** 한 팀의 승부차기 점: 찬 킥(성공·실패) 뒤에 남은 기본 킥. 서든데스로 길어지면 최근 limit개만. */
    fun marks(results: List<Boolean>, perTeam: Int, limit: Int): List<KickMark> {
        val taken = results.map { if (it) KickMark.SCORED else KickMark.MISSED }
        val all = taken + List(maxOf(0, perTeam - results.size)) { KickMark.PENDING }
        return all.takeLast(maxOf(0, limit))
    }

    /**
     * 두 팀 승부차기 줄 (Swift `KickMarks.rows`). 서든데스로 길어지면 두 줄을 같은 킥 순번에서 잘라 같은 열이 같은 순번이 되게 한다.
     * 길이는 기본 킥 수와 더 많이 찬 팀 중 큰 쪽, 덜 찬 팀은 남은 킥(○)으로 채운다.
     */
    fun rows(home: List<Boolean>, away: List<Boolean>, perTeam: Int, limit: Int): TeamPair<List<KickMark>> {
        val length = maxOf(perTeam, home.size, away.size)
        val drop = maxOf(0, length - maxOf(0, limit))
        fun row(results: List<Boolean>): List<KickMark> {
            if (limit <= 0) return emptyList()
            val taken = results.map { if (it) KickMark.SCORED else KickMark.MISSED }
            val all = taken + List(maxOf(0, length - results.size)) { KickMark.PENDING }
            return all.drop(drop)
        }
        return TeamPair(row(home), row(away))
    }

    /** 앞에서 잘려 안 보이는 킥 순번 수 (Swift `hiddenCount`). 0보다 크면 줄 앞에 생략 표시를 둔다. */
    fun hiddenCount(home: List<Boolean>, away: List<Boolean>, perTeam: Int, limit: Int): Int =
        maxOf(0, maxOf(perTeam, home.size, away.size) - maxOf(0, limit))

    /**
     * 한 줄 폭에 들어가는 킥 칸 수 1..cap (Swift `fitting`). 줄은 [생략 표시][팀 막대][킥 점…], 간격은 점 크기의 0.35배.
     * 생략 표시 자리는 늘 비워 두어 서든데스로 넘어가도 칸 수가 바뀌지 않는다.
     */
    fun fitting(width: Double, mark: Double, cap: Int): Int {
        val gap = mark * GAP_RATIO
        val fixed = mark * ELLIPSIS_RATIO + mark * BAR_RATIO + gap
        val n = kotlin.math.floor((width - fixed) / (mark + gap)).toInt()
        return minOf(maxOf(1, cap), maxOf(1, n))
    }

    const val GAP_RATIO = 0.35
    const val ELLIPSIS_RATIO = 0.8
    const val BAR_RATIO = 0.3
}

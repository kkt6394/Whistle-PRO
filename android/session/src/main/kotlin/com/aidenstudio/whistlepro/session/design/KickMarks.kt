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
}

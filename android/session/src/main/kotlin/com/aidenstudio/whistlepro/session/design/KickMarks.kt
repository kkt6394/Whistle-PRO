package com.aidenstudio.whistlepro.session.design

enum class KickMark { SCORED, MISSED, PENDING }

object KickMarks {
    /** 한 팀의 승부차기 점: 찬 킥(성공·실패) 뒤에 남은 기본 킥. 서든데스로 길어지면 최근 limit개만. */
    fun marks(results: List<Boolean>, perTeam: Int, limit: Int): List<KickMark> {
        val taken = results.map { if (it) KickMark.SCORED else KickMark.MISSED }
        val all = taken + List(maxOf(0, perTeam - results.size)) { KickMark.PENDING }
        return all.takeLast(maxOf(0, limit))
    }
}

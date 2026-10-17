package com.aidenstudio.whistlepro.session.design

import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.TeamPair
import com.aidenstudio.whistlepro.session.SinBinBadge

/** 팀 칸 아래 한 줄 (B1 스펙 §3.3) */
sealed interface TeamLine {
    data object ShortHanded : TeamLine
    /** 일시 퇴장 한 명. total은 줄이 모자라 이 줄에서 줄였을 때만 있는 그 팀 일시 퇴장 전체 수 (`×3`) */
    data class SinBin(val badge: SinBinBadge, val total: Int?) : TeamLine
}

/** 한 팀의 인원 부족 여부와 일시 퇴장 (남은 시간 짧은 순) */
data class TeamStatus(val shortHanded: Boolean, val sinBins: List<SinBinBadge>) {
    /**
     * 칸에 들어가는 줄 수(최소 1)일 때 보일 줄.
     * 인원 부족 라벨이 먼저 오고, 한 줄뿐이면 일시 퇴장을 보인다 (인원 부족은 칸 배경으로 알린다).
     */
    fun lines(count: Int): List<TeamLine> {
        val lines = maxOf(1, count)
        if (shortHanded && (sinBins.isEmpty() || lines >= 2)) return listOf(TeamLine.ShortHanded) + sinBinLines(lines - 1)
        return sinBinLines(lines)
    }

    private fun sinBinLines(count: Int): List<TeamLine> {
        if (count <= 0) return emptyList()
        if (sinBins.size <= count) return sinBins.map { TeamLine.SinBin(it, null) }
        return sinBins.take(count - 1).map { TeamLine.SinBin(it, null) } + TeamLine.SinBin(sinBins[count - 1], sinBins.size)
    }

    companion object {
        fun split(shortHanded: List<Team>, sinBins: List<SinBinBadge>): TeamPair<TeamStatus> {
            // sortedBy는 안정 정렬이라 남은 시간이 같으면 원래 순서를 지킨다
            fun status(team: Team) = TeamStatus(team in shortHanded, sinBins.filter { it.team == team }.sortedBy { it.seconds })
            return TeamPair(status(Team.HOME), status(Team.AWAY))
        }
    }
}

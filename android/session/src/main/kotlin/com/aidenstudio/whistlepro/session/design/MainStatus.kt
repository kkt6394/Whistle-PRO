package com.aidenstudio.whistlepro.session.design

import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.session.SinBinBadge

/** 워치 메인 맨 아래 상태 줄 하나 (스펙 §4.1): 인원 부족 > 일시 퇴장 > 없음 */
sealed interface MainStatus {
    data class ShortHanded(val teams: List<Team>) : MainStatus
    /** 가장 먼저 끝나는 일시 퇴장과 그 밖의 일시 퇴장 수 */
    data class SinBin(val badge: SinBinBadge, val more: Int) : MainStatus

    companion object {
        fun select(shortHanded: List<Team>, sinBins: List<SinBinBadge>): MainStatus? {
            if (shortHanded.isNotEmpty()) return ShortHanded(shortHanded)
            // minByOrNull은 같은 값이면 앞의 것을 고른다 (Swift min(by:)와 같음)
            val first = sinBins.minByOrNull { it.seconds } ?: return null
            return SinBin(first, sinBins.size - 1)
        }
    }
}

enum class KickMark { SCORED, MISSED, PENDING }

object KickMarks {
    /** 한 팀의 승부차기 점: 찬 킥(성공·실패) 뒤에 남은 기본 킥. 서든데스로 길어지면 최근 limit개만. */
    fun marks(results: List<Boolean>, perTeam: Int, limit: Int): List<KickMark> {
        val taken = results.map { if (it) KickMark.SCORED else KickMark.MISSED }
        val all = taken + List(maxOf(0, perTeam - results.size)) { KickMark.PENDING }
        return all.takeLast(maxOf(0, limit))
    }
}

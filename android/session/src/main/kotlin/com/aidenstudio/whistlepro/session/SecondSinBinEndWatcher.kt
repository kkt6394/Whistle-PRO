package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.BarredPlayer
import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.Phase
import com.aidenstudio.whistlepro.core.barredPlayers
import java.util.UUID

/**
 * 두 번째 일시 퇴장이 방금 끝났는지 본다 (워치 끝 안내, Swift `SecondSinBinEndWatcher`).
 * 시계 틱마다 [update]를 부르면, 지난번에 없던 출전 금지 선수(교체로 아직 안 채운)가 생긴 틱에 한 번만 그 선수를 돌려준다.
 * 처음 보는 경기(앱 다시 실행·새 경기)는 이미 끝난 것을 본 것으로만 하고 알리지 않는다.
 * 승부차기·종료로 넘어가면 모든 두 번째 일시 퇴장이 한꺼번에 끝난 것으로 보이므로 알리지 않는다.
 */
class SecondSinBinEndWatcher {
    private var matchId: UUID? = null
    /** 지난 틱의 출전 금지 (채운 것 포함, 두 번째 일시 퇴장 이벤트 id). 채운 교체를 되돌려도 다시 알리지 않게 */
    private var seen: Set<String> = emptySet()

    fun update(state: MatchState, matchId: UUID, now: Double): BarredPlayer? {
        val barred = state.barredPlayers(now)
        val sameMatch = this.matchId == matchId
        val previous = seen
        this.matchId = matchId
        seen = barred.mapTo(HashSet()) { it.eventId }
        if (!sameMatch) return null
        if (state.phase == Phase.Penalties || state.phase is Phase.Finished) return null
        return barred.lastOrNull { !it.filled && it.eventId !in previous }
    }

    fun reset() {
        matchId = null
        seen = emptySet()
    }
}

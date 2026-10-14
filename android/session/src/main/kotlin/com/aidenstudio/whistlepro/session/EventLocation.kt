package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.ResolvedEventKind
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.resolvedEvents
import kotlin.math.abs

/** 지도에 찍는 이벤트 하나. 위치는 심판이 기록한 순간의 심판 위치다 (반칙 위치가 아니다). */
data class EventPin(
    val eventID: String,
    val kind: ResolvedEventKind,
    val team: Team,
    val minute: EventMinute,
    /** "#10 홍길동" (번호가 없으면 "") */
    val detail: String,
    val latitude: Double,
    val longitude: Double,
)

object EventLocation {
    /** 기록 시각과 이만큼 넘게 떨어진 GPS 지점은 쓰지 않는다 (초) */
    const val MAXIMUM_OFFSET = 5.0

    /** 기록 시각에 가장 가까운 쓸 만한 GPS 지점 (정확도 20m 이하, ±5초 안) */
    fun locate(route: List<RoutePoint>, t: Double): RoutePoint? {
        var best: RoutePoint? = null
        var bestOffset = Double.POSITIVE_INFINITY
        for (point in route) {
            if (!(point.accuracy >= 0 && point.accuracy <= WorkoutAnalysis.MAXIMUM_ACCURACY)) continue
            val offset = abs(point.t - t)
            if (offset <= MAXIMUM_OFFSET && offset < bestOffset) {
                best = point
                bestOffset = offset
            }
        }
        return best
    }

    /**
     * 위치를 찾은 이벤트만, 오래된 것부터. 승부차기 킥은 모두 같은 자리라 뺀다.
     * 고친 이벤트는 처음 기록한 시각을 쓴다.
     */
    fun pins(state: MatchState, setup: MatchSetup, route: List<RoutePoint>): List<EventPin> {
        if (route.isEmpty()) return emptyList()
        val timestamps = mutableMapOf<String, Double>()
        for (resolved in state.resolvedEvents) timestamps[resolved.event.id] = resolved.event.timestamp
        return state.eventRows(setup).reversed().mapNotNull { row ->
            if (row.kind == ResolvedEventKind.PENALTY_KICK) return@mapNotNull null
            val t = timestamps[row.id] ?: return@mapNotNull null
            val point = locate(route, t) ?: return@mapNotNull null
            EventPin(row.id, row.kind, row.team, row.minute, row.detail, point.latitude, point.longitude)
        }
    }
}

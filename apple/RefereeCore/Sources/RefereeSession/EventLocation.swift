import Foundation
import RefereeCore

/// 지도에 찍는 이벤트 하나. 위치는 심판이 기록한 순간의 심판 위치다 (반칙 위치가 아니다).
public struct EventPin: Equatable, Sendable {
    public var eventID: String
    public var kind: ResolvedEventKind
    public var team: Team
    public var minute: EventMinute
    /// "#10 홍길동" (번호가 없으면 "")
    public var detail: String
    public var latitude: Double
    public var longitude: Double
}

public enum EventLocation {
    /// 기록 시각과 이만큼 넘게 떨어진 GPS 지점은 쓰지 않는다 (초)
    public static let maximumOffset = 5.0

    /// 기록 시각에 가장 가까운 쓸 만한 GPS 지점 (정확도 20m 이하, ±5초 안)
    public static func locate(_ route: [RoutePoint], at t: Double) -> RoutePoint? {
        var best: RoutePoint?
        var bestOffset = Double.infinity
        for point in WorkoutAnalysis.usableRoute(route) {
            let offset = abs(point.t - t)
            if offset <= maximumOffset && offset < bestOffset {
                best = point
                bestOffset = offset
            }
        }
        return best
    }

    /// 위치를 찾은 이벤트만, 오래된 것부터. 승부차기 킥은 모두 같은 자리라 뺀다.
    /// 고친 이벤트는 처음 기록한 시각을 쓴다.
    public static func pins(state: MatchState, setup: MatchSetup, route: [RoutePoint]) -> [EventPin] {
        guard !route.isEmpty else { return [] }
        var timestamps: [String: Double] = [:]
        for resolved in state.resolvedEvents { timestamps[resolved.event.id] = resolved.event.timestamp }
        return state.eventRows(setup: setup).reversed().compactMap { row in
            guard row.kind != .penaltyKick, let t = timestamps[row.id], let point = locate(route, at: t) else { return nil }
            return EventPin(eventID: row.id, kind: row.kind, team: row.team, minute: row.minute, detail: row.detail,
                            latitude: point.latitude, longitude: point.longitude)
        }
    }
}

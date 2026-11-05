import Foundation
import RefereeCore

/// 두 번째 일시 퇴장이 방금 끝났는지 본다 (워치 끝 안내, Kotlin `SecondSinBinEndWatcher`).
/// 시계 틱마다 `update`를 부르면, 지난번에 없던 출전 금지 선수(교체로 아직 안 채운)가 생긴 틱에 한 번만 그 선수를 돌려준다.
/// 처음 보는 경기(앱 다시 실행·새 경기)는 이미 끝난 것을 본 것으로만 하고 알리지 않는다.
/// 승부차기·종료로 넘어가면 모든 두 번째 일시 퇴장이 한꺼번에 끝난 것으로 보이므로 알리지 않는다.
public struct SecondSinBinEndWatcher: Sendable {
    private var matchID: UUID?
    /// 지난 틱의 출전 금지 (채운 것 포함, 두 번째 일시 퇴장 이벤트 id). 채운 교체를 되돌려도 다시 알리지 않게
    private var seen: Set<String> = []

    public init() {}

    public mutating func update(_ state: MatchState, matchID: UUID, at now: Double) -> BarredPlayer? {
        let barred = state.barredPlayers(at: now)
        let ids = Set(barred.map(\.eventId))
        defer {
            self.matchID = matchID
            seen = ids
        }
        guard self.matchID == matchID else { return nil }
        switch state.phase {
        case .penalties, .finished: return nil
        default: break
        }
        return barred.last { !$0.filled && !seen.contains($0.eventId) }
    }

    public mutating func reset() {
        matchID = nil
        seen = []
    }
}

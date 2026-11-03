import Foundation
import RefereeCore

extension MatchState {
    /// 퇴장당한 선수의 등번호 (명단에서 흐리게). 고치는 중인 이벤트 자신은 빼고 센다.
    public func sentOffNumbers(of team: Team, excluding eventID: String? = nil) -> Set<Int> {
        Set(resolvedEvents
            .filter { $0.isSendingOff && $0.event.payload.team == team && $0.event.id != eventID }
            .compactMap { $0.event.payload.number(in: .primary) })
    }

    /// 경고 1장을 받고 아직 뛰는 선수의 등번호 (명단 칩의 작은 노란 카드, 2026-11-04 critique).
    /// 두 번째 경고·레드로 퇴장했으면 빠진다. 고치는 중인 이벤트 자신은 빼고 센다.
    public func cautionedNumbers(of team: Team, excluding eventID: String? = nil) -> Set<Int> {
        Set(resolvedEvents
            .filter { $0.kind == .yellow && $0.event.payload.team == team && $0.event.id != eventID }
            .compactMap { $0.event.payload.number(in: .primary) })
            .subtracting(sentOffNumbers(of: team, excluding: eventID))
    }

    /// 지금 뛰는 선수의 등번호. 선발 표시가 없으면 nil. 선발에서 시작해 교체를 따라가고 퇴장은 뺀다 (싱빈은 그대로).
    public func onPitchNumbers(team: Team, setup: MatchSetup, excluding eventID: String? = nil) -> Set<Int>? {
        guard setup.hasStarters(for: team) else { return nil }
        var pitch = setup.starters(of: team)
        for resolved in resolvedEvents
        where resolved.kind == .substitution && resolved.event.payload.team == team && resolved.event.id != eventID {
            if let out = resolved.event.payload.number(in: .primary) { pitch.remove(out) }
            if let into = resolved.event.payload.number(in: .incoming) { pitch.insert(into) }
        }
        return pitch.subtracting(sentOffNumbers(of: team, excluding: eventID))
    }
}

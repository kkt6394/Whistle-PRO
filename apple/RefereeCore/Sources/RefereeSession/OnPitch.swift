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

    /// 지금 일시 퇴장 중인 선수의 등번호 (번호 있는 것만).
    public func sinBinnedNumbers(team: Team, at now: Double) -> Set<Int> {
        Set(activeSinBins(at: now).filter { $0.team == team }.compactMap(\.number))
    }

    /// 출전 금지(두 번째 일시 퇴장을 마친) 선수의 등번호. filled가 nil이면 전부, true/false면 교체로 채운 쪽/아직 못 채운 쪽만.
    public func barredNumbers(team: Team, at now: Double, filled: Bool? = nil) -> Set<Int> {
        Set(barredPlayers(at: now)
            .filter { $0.team == team && (filled == nil || $0.filled == filled) }
            .map(\.number))
    }

    /// 지금 뛰는 선수의 등번호. 선발 표시가 없으면 nil. 선발에서 시작해 교체를 따라가고 퇴장·출전 금지는 뺀다 (일시 퇴장 중은 그대로).
    public func onPitchNumbers(team: Team, setup: MatchSetup, now: Double, excluding eventID: String? = nil) -> Set<Int>? {
        guard setup.hasStarters(for: team) else { return nil }
        var pitch = setup.starters(of: team)
        for resolved in resolvedEvents
        where resolved.kind == .substitution && resolved.event.payload.team == team && resolved.event.id != eventID {
            if let out = resolved.event.payload.number(in: .primary) { pitch.remove(out) }
            if let into = resolved.event.payload.number(in: .incoming) { pitch.insert(into) }
        }
        return pitch.subtracting(sentOffNumbers(of: team, excluding: eventID)).subtracting(barredNumbers(team: team, at: now))
    }
}

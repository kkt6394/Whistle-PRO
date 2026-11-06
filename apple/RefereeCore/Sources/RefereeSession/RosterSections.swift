import Foundation

/// 명단 묶음 순서: 나가는 선수를 고를 땐 뛰는 선수부터, 들어오는 선수를 고를 땐 벤치부터 (Kotlin RosterGrouping).
public enum RosterGrouping: Sendable {
    case outgoing, incoming, any
}

/// 명단 묶음 (Kotlin RosterGroup). id는 머리글 접근성 식별자.
public enum RosterGroup: String, Sendable {
    case barred = "roster.group.barred"
    case onPitch = "roster.group.onPitch"
    case bench = "roster.group.bench"
    case sentOff = "roster.group.sentOff"
}

/// 명단 한 묶음 (group이 nil이면 선발 표시가 없어 묶지 않은 번호순 한 목록).
public struct RosterSection: Equatable, Sendable {
    public let group: RosterGroup?
    public let players: [Player]
    public var id: String { group?.rawValue ?? "roster.all" }
}

public enum RosterSections {
    /// 뛰는 선수·벤치·출전 금지·퇴장 묶음 (Kotlin RosterSections.of). 빈 묶음은 빼고 각 묶음은 번호순.
    /// - 나가는 번호(outgoing): [출전 금지(아직 못 채운 선수), 뛰는 선수, 벤치, 퇴장] — 채우려고 찾는 사람이라 맨 위
    /// - 그 밖: [뛰는 선수, 벤치(들어오는 번호면 벤치 먼저), 출전 금지(전부), 퇴장]
    /// 퇴장한 선수는 퇴장 묶음에만 든다. 선발 표시가 없으면(onPitch nil) 번호순 한 목록.
    public static func of(players: [Player], sentOff: Set<Int>, onPitch: Set<Int>?, grouping: RosterGrouping,
                          barredUnfilled: Set<Int> = [], barredAll: Set<Int> = []) -> [RosterSection] {
        let sorted = players.sorted { $0.number < $1.number }
        guard let onPitch else { return [RosterSection(group: nil, players: sorted)] }
        let barredSet = (grouping == .outgoing ? barredUnfilled : barredAll).subtracting(sentOff)
        let rest = sorted.filter { !sentOff.contains($0.number) && !barredSet.contains($0.number) }
        let barred = RosterSection(group: .barred, players: sorted.filter { barredSet.contains($0.number) })
        let pitch = RosterSection(group: .onPitch, players: rest.filter { onPitch.contains($0.number) })
        let bench = RosterSection(group: .bench, players: rest.filter { !onPitch.contains($0.number) })
        let gone = RosterSection(group: .sentOff, players: sorted.filter { sentOff.contains($0.number) })
        let ordered: [RosterSection] = switch grouping {
        case .outgoing: [barred, pitch, bench, gone]
        case .incoming: [bench, pitch, barred, gone]
        case .any: [pitch, bench, barred, gone]
        }
        return ordered.filter { !$0.players.isEmpty }
    }
}

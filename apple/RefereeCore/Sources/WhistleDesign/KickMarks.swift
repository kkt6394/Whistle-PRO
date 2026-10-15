import RefereeCore

public enum KickMark: Equatable, Sendable {
    case scored, missed, pending
}

public enum KickMarks {
    /// 두 팀 승부차기 줄. 서든데스로 길어지면 두 줄을 같은 킥 순번에서 잘라 같은 열이 같은 순번이 되게 한다.
    /// 길이는 기본 킥 수와 더 많이 찬 팀 중 큰 쪽, 덜 찬 팀은 남은 킥(○)으로 채운다.
    public static func rows(home: [Bool], away: [Bool], perTeam: Int, limit: Int) -> TeamPair<[KickMark]> {
        let length = max(perTeam, home.count, away.count)
        let drop = max(0, length - max(0, limit))
        func row(_ results: [Bool]) -> [KickMark] {
            let taken = results.map { $0 ? KickMark.scored : .missed }
            let all = taken + Array(repeating: KickMark.pending, count: max(0, length - results.count))
            return limit <= 0 ? [] : Array(all.dropFirst(drop))
        }
        return TeamPair(home: row(home), away: row(away))
    }
}

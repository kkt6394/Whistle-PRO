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

    /// 앞에서 잘려 안 보이는 킥 순번 수. 0보다 크면 줄 앞에 생략 표시(…)를 둔다.
    public static func hiddenCount(home: [Bool], away: [Bool], perTeam: Int, limit: Int) -> Int {
        max(0, max(perTeam, home.count, away.count) - max(0, limit))
    }

    /// 한 줄 폭에 들어가는 킥 칸 수 (1...cap). 줄은 [생략 표시][팀 막대][킥 점…]이고 간격은 모두 점 크기의 0.35배.
    /// 생략 표시 자리는 늘 비워 두어 서든데스로 넘어가도 칸 수가 바뀌지 않는다 (2026-11-04 40mm에서 양끝이 잘렸다).
    public static func fitting(width: Double, mark: Double, cap: Int) -> Int {
        let gap = mark * gapRatio
        let fixed = mark * ellipsisRatio + mark * barRatio + gap
        let n = Int(((width - fixed) / (mark + gap)).rounded(.down))
        return min(max(1, cap), max(1, n))
    }

    public static let gapRatio = 0.35
    public static let ellipsisRatio = 0.8
    public static let barRatio = 0.3
}

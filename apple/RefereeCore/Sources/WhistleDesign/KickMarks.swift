public enum KickMark: Equatable, Sendable {
    case scored, missed, pending
}

public enum KickMarks {
    /// 한 팀의 승부차기 점: 찬 킥(성공·실패) 뒤에 남은 기본 킥. 서든데스로 길어지면 최근 limit개만.
    public static func marks(results: [Bool], perTeam: Int, limit: Int) -> [KickMark] {
        let taken = results.map { $0 ? KickMark.scored : .missed }
        let all = taken + Array(repeating: KickMark.pending, count: max(0, perTeam - results.count))
        return Array(all.suffix(max(0, limit)))
    }
}

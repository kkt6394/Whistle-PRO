import RefereeCore
import RefereeSession

/// 팀 칸 아래 한 줄 (B1 스펙 §3.3)
public enum TeamLine: Equatable, Sendable {
    case shortHanded
    /// 일시 퇴장 한 명. total은 줄이 모자라 이 줄에서 줄였을 때만 있는 그 팀 일시 퇴장 전체 수 (`×3`)
    case sinBin(SinBinBadge, total: Int?)
}

/// 한 팀의 인원 부족 여부와 일시 퇴장 (남은 시간 짧은 순)
public struct TeamStatus: Equatable, Sendable {
    public var shortHanded: Bool
    public var sinBins: [SinBinBadge]

    public init(shortHanded: Bool, sinBins: [SinBinBadge]) {
        self.shortHanded = shortHanded
        self.sinBins = sinBins
    }

    public static func split(shortHanded: [Team], sinBins: [SinBinBadge]) -> TeamPair<TeamStatus> {
        func status(_ team: Team) -> TeamStatus {
            // 남은 시간이 같으면 원래 순서를 지킨다
            let mine = sinBins.enumerated().filter { $0.element.team == team }
                .sorted { ($0.element.seconds, $0.offset) < ($1.element.seconds, $1.offset) }
                .map(\.element)
            return TeamStatus(shortHanded: shortHanded.contains(team), sinBins: mine)
        }
        return TeamPair(home: status(.home), away: status(.away))
    }

    /// 칸에 들어가는 줄 수(최소 1)일 때 보일 줄.
    /// 인원 부족 라벨이 먼저 오고, 한 줄뿐이면 일시 퇴장을 보인다 (인원 부족은 칸 배경으로 알린다).
    public func lines(_ count: Int) -> [TeamLine] {
        let count = max(1, count)
        if shortHanded && (sinBins.isEmpty || count >= 2) {
            return [.shortHanded] + sinBinLines(count - 1)
        }
        return sinBinLines(count)
    }

    private func sinBinLines(_ count: Int) -> [TeamLine] {
        guard count > 0 else { return [] }
        guard sinBins.count > count else { return sinBins.map { .sinBin($0, total: nil) } }
        return sinBins.prefix(count - 1).map { .sinBin($0, total: nil) } + [.sinBin(sinBins[count - 1], total: sinBins.count)]
    }
}

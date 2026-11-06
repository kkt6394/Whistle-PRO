import RefereeCore
import RefereeSession

/// 팀 칸 아래 한 줄 (B1 스펙 §3.3)
public enum TeamLine: Equatable, Sendable {
    case shortHanded
    /// 일시 퇴장 한 명. total은 줄이 모자라 이 줄에서 줄였을 때만 있는 그 팀 일시 퇴장 전체 수 (`×3`)
    case sinBin(SinBinBadge, total: Int?)
    /// 출전 금지(두 번째 일시 퇴장 끝, 교체로 아직 안 채움) 한 명 "#7 출전 금지". total은 줄여 보일 때만 그 팀 출전 금지 전체 수
    case barred(number: Int, total: Int?)
}

/// 한 팀의 인원 부족 여부와 일시 퇴장 (남은 시간 짧은 순), 아직 채우지 않은 출전 금지 등번호 (기록 순)
public struct TeamStatus: Equatable, Sendable {
    public var shortHanded: Bool
    public var sinBins: [SinBinBadge]
    public var barred: [Int]

    public init(shortHanded: Bool, sinBins: [SinBinBadge], barred: [Int] = []) {
        self.shortHanded = shortHanded
        self.sinBins = sinBins
        self.barred = barred
    }

    public static func split(shortHanded: [Team], sinBins: [SinBinBadge], barred: [BarredPlayer] = []) -> TeamPair<TeamStatus> {
        func status(_ team: Team) -> TeamStatus {
            // 남은 시간이 같으면 원래 순서를 지킨다
            let mine = sinBins.enumerated().filter { $0.element.team == team }
                .sorted { ($0.element.seconds, $0.offset) < ($1.element.seconds, $1.offset) }
                .map(\.element)
            return TeamStatus(shortHanded: shortHanded.contains(team), sinBins: mine,
                              barred: barred.filter { $0.team == team && !$0.filled }.map(\.number))
        }
        return TeamPair(home: status(.home), away: status(.away))
    }

    /// 칸에 들어가는 줄 수(최소 1)일 때 보일 줄: 인원 부족 라벨 · 일시 퇴장 · 출전 금지 순으로 같은 줄 수를 나눠 쓴다.
    /// 인원 부족 라벨이 먼저 오고, 한 줄뿐이면 일시 퇴장(없으면 출전 금지)을 보인다 (인원 부족은 칸 배경으로 알린다).
    /// 줄이 모자라면 뒤쪽부터 줄인다: 일시 퇴장이 줄을 다 쓰면 출전 금지는 숨고, 마지막 줄에 ×전체 수
    public func lines(_ count: Int) -> [TeamLine] {
        let count = max(1, count)
        if shortHanded && ((sinBins.isEmpty && barred.isEmpty) || count >= 2) {
            return [.shortHanded] + detailLines(count - 1)
        }
        return detailLines(count)
    }

    private func detailLines(_ count: Int) -> [TeamLine] {
        guard count > 0 else { return [] }
        guard sinBins.count < count else { return sinBinLines(count) }
        return sinBinLines(sinBins.count) + barredLines(count - sinBins.count)
    }

    private func sinBinLines(_ count: Int) -> [TeamLine] {
        guard count > 0 else { return [] }
        guard sinBins.count > count else { return sinBins.map { .sinBin($0, total: nil) } }
        return sinBins.prefix(count - 1).map { .sinBin($0, total: nil) } + [.sinBin(sinBins[count - 1], total: sinBins.count)]
    }

    private func barredLines(_ count: Int) -> [TeamLine] {
        guard count > 0 else { return [] }
        guard barred.count > count else { return barred.map { .barred(number: $0, total: nil) } }
        return barred.prefix(count - 1).map { .barred(number: $0, total: nil) } + [.barred(number: barred[count - 1], total: barred.count)]
    }
}

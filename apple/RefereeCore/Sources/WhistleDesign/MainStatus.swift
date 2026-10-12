import RefereeCore
import RefereeSession

/// 워치 메인 맨 아래 상태 줄 하나 (스펙 §4.1): 인원 부족 > 일시 퇴장 > 없음
public enum MainStatus: Equatable, Sendable {
    case shortHanded([Team])
    /// 가장 먼저 끝나는 일시 퇴장과 그 밖의 일시 퇴장 수
    case sinBin(SinBinBadge, more: Int)

    public static func select(shortHanded: [Team], sinBins: [SinBinBadge]) -> MainStatus? {
        if !shortHanded.isEmpty { return .shortHanded(shortHanded) }
        // min(by:)는 같은 값이면 앞의 것을 고른다
        guard let first = sinBins.min(by: { $0.seconds < $1.seconds }) else { return nil }
        return .sinBin(first, more: sinBins.count - 1)
    }
}

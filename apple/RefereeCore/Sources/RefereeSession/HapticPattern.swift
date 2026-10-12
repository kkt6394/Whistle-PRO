import RefereeCore

/// 플랫폼 진동 종류에 대응하는 값 (watchOS: WKHapticType)
public enum HapticTone: Sendable, Hashable {
    case notification, directionUp, success, retry, start, stop
}

public struct HapticPattern: Sendable, Hashable {
    public var tone: HapticTone
    public var repeats: Int
    /// 반복 사이 간격(초)
    public var interval: Double
}

extension AlertKind {
    /// 손목 감각만으로 구분되도록 종류마다 다른 패턴. 기간 종료가 가장 길다.
    public var hapticPattern: HapticPattern {
        switch self {
        case .warningBeforeEnd: HapticPattern(tone: .directionUp, repeats: 2, interval: 0.5)
        case .periodEnd: HapticPattern(tone: .notification, repeats: 3, interval: 0.8)
        case .breakEndingSoon: HapticPattern(tone: .directionUp, repeats: 1, interval: 0)
        case .breakEnd: HapticPattern(tone: .notification, repeats: 2, interval: 0.8)
        case .sinBinEnd: HapticPattern(tone: .retry, repeats: 2, interval: 0.6)
        }
    }
}

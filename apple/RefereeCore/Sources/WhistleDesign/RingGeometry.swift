/// 진행 테두리의 채움. 값은 테두리 경로 전체를 0...1로 본 위치 (B1 스펙 §2).
public struct RingFill: Equatable, Sendable {
    /// 규정 시간(또는 남은 휴식) 구간의 끝 (0부터)
    public var main: Double
    /// 추가시간 구간. 규정 시간을 넘었을 때만 있다.
    public var added: ClosedRange<Double>?

    public init(main: Double, added: ClosedRange<Double>?) {
        self.main = main
        self.added = added
    }
}

/// 진행 테두리 채움 비율 (B1 스펙 §2). 경로 전체 = 규정 시간 × 1.1.
public enum RingGeometry {
    /// 테두리는 "규정 시간 × 1.1"을 나타낸다. 규정 시간이 차지하는 몫
    public static let regulationShare = 1 / 1.1

    /// 기간 진행. 추가시간은 규정의 10%가 지나면 테두리 끝에서 멈춘다.
    public static func period(elapsed: Int, length: Int) -> RingFill {
        guard length > 0, elapsed > 0 else { return RingFill(main: 0, added: nil) }
        let regular = Double(min(elapsed, length)) / Double(length)
        let main = regular * regulationShare
        guard elapsed > length else { return RingFill(main: main, added: nil) }
        let cap = Double(length) * 0.1
        let over = min(Double(elapsed - length), cap) / cap
        return RingFill(main: main, added: regulationShare...(regulationShare + over * (1 - regulationShare)))
    }

    /// 휴식: 남은 시간만큼 찬 테두리 (끝나 갈수록 줄어든다)
    public static func rest(remaining: Int, total: Int) -> RingFill {
        guard total > 0 else { return RingFill(main: 0, added: nil) }
        return RingFill(main: min(max(Double(remaining) / Double(total), 0), 1), added: nil)
    }
}

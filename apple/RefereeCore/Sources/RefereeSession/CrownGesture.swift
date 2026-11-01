/// 크라운 제스처 판정 설정. 크라운 값은 한 바퀴 = 1.0 기준.
public struct CrownGestureConfig: Sendable, Equatable {
    /// 반 바퀴의 크라운 값
    public var halfTurn = 0.5
    /// 흔들기 한 번으로 셀 최소 회전
    public var wiggleStep = 0.08
    /// 방향 반전 3번이 들어와야 하는 시간(초)
    public var wiggleWindow = 1.0
    /// 이만큼 멈추면 처음부터
    public var idleReset = 0.4
    /// 실행 뒤 회전을 흡수하는 시간(초)
    public var cooldown = 0.6

    public init() {}
}

/// 판정된 크라운 제스처
public struct CrownGestureEvent: Sendable, Equatable {
    public var input: PhysicalInput
    /// 실행된 구간의 첫 delta 시각
    public var startedAt: Double

    public init(input: PhysicalInput, startedAt: Double) {
        self.input = input
        self.startedAt = startedAt
    }
}

/// 크라운 회전 표본(delta, 시각)을 제스처로 바꾼다. 반 바퀴 = primary/secondary, 흔들기 두 번 = undo.
/// 시계에 의존하지 않는다. 시각은 호출자가 초 단위로 넘긴다.
public struct CrownGestureDetector: Sendable {
    private let config: CrownGestureConfig
    private var lastAt: Double?
    private var cooldownUntil = -Double.infinity
    private var direction = 0.0
    private var segmentSize = 0.0
    private var segmentStart = 0.0
    private var reversals: [Double] = []

    public init(config: CrownGestureConfig = .init()) {
        self.config = config
    }

    /// delta > 0 = 시계 방향(위로)
    public mutating func feed(delta: Double, at: Double) -> CrownGestureEvent? {
        guard delta != 0 else { return nil }
        if let last = lastAt, at - last > config.idleReset { resetTracking() }
        lastAt = at
        if at < cooldownUntil { return nil }

        let sign: Double = delta > 0 ? 1 : -1
        if sign == direction {
            segmentSize += abs(delta)
        } else {
            if direction != 0, segmentSize >= config.wiggleStep { reversals.append(at) }
            direction = sign
            segmentSize = abs(delta)
            segmentStart = at
        }
        reversals.removeAll { at - $0 > config.wiggleWindow }

        if reversals.count >= 3 {
            fire(at: at)
            return CrownGestureEvent(input: .undo, startedAt: segmentStart)
        }
        if segmentSize >= config.halfTurn {
            let event = CrownGestureEvent(input: sign > 0 ? .primary : .secondary, startedAt: segmentStart)
            fire(at: at)
            return event
        }
        return nil
    }

    private mutating func resetTracking() {
        direction = 0
        segmentSize = 0
        reversals = []
    }

    private mutating func fire(at: Double) {
        resetTracking()
        cooldownUntil = at + config.cooldown
    }
}

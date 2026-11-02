/// 크라운 제스처 판정 설정. 크라운 값은 한 바퀴 = 1.0 기준 (digitalCrownRotation 값 → 바퀴 환산은 호출자 몫).
public struct CrownGestureConfig: Sendable, Equatable {
    /// 반 바퀴의 크라운 값
    public var halfTurn = 0.5
    /// 흔들기 한 번으로 셀 최소 회전 (바퀴). 0.08(29°)은 SE 실기기에서 작은 흔들기가 안 잡혀 0.03(11°)으로 (2026-11-02)
    public var wiggleStep = 0.03
    /// 방향 반전 3번이 들어와야 하는 시간(초)
    public var wiggleWindow = 1.5
    /// 이만큼 멈추면 처음부터
    public var idleReset = 0.4
    /// 실행 뒤 회전을 흡수하는 시간(초)
    public var cooldown = 0.6
    /// 반 바퀴를 넘은 획은 이만큼 멈춘 뒤에 실행한다. 그 전에 방향이 바뀌면 흔들기의 한 획으로 본다
    /// (SE 실기기 2026-11-02: 사용자의 "작은" 흔들기 획이 76~186°라 반 바퀴와 겹친다). 기록 시각은 획 시작이라 늦지 않는다
    public var strokeEnd = 0.15
    /// 획이 이만큼 넘어가면 멈추길 기다리지 않고 바로 실행한다 (크게 돌리는 손에 즉시 반응). 흔들기 획은 실기기에서 최대 186°였다
    public var fullStroke = 0.75

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

/// 크라운 회전 표본(delta, 시각)을 제스처로 바꾼다. 반 바퀴 = primary/secondary(획이 끝나면, 3/4바퀴 넘으면 즉시), 흔들기 두 번 = undo.
/// 시계에 의존하지 않는다. 시각은 호출자가 초 단위로 넘긴다. 반 바퀴 뒤 손이 멈춘 것은 표본 없이는 알 수 없으므로
/// 호출자가 `strokeEnd` 뒤에 `flush(at:)`를 불러 준다.
public struct CrownGestureDetector: Sendable {
    private let config: CrownGestureConfig
    private var lastAt: Double?
    private var cooldownUntil = -Double.infinity
    private var direction = 0.0
    private var segmentSize = 0.0
    private var segmentStart = 0.0
    private var reversals: [Double] = []
    /// 반 바퀴를 넘었지만 아직 획이 끝나지 않은 입력
    private var pending: CrownGestureEvent?

    public init(config: CrownGestureConfig = .init()) {
        self.config = config
    }

    /// 실행을 기다리는 입력이 있다 → 호출자는 `strokeEnd` 뒤 `flush(at:)`
    public var hasPending: Bool { pending != nil }

    /// delta > 0 = 시계 방향(위로)
    public mutating func feed(delta: Double, at: Double) -> CrownGestureEvent? {
        guard delta != 0 else { return nil }
        // 앞 획이 멈춘 채 끝났으면 먼저 실행하고, 이 표본은 새 획으로
        let flushed = flush(at: at)
        if let last = lastAt, at - last > config.idleReset { resetTracking() }
        lastAt = at
        if at < cooldownUntil { return flushed }

        let sign: Double = delta > 0 ? 1 : -1
        if sign == direction {
            segmentSize += abs(delta)
        } else {
            // 방향이 바뀌면 기다리던 반 바퀴는 흔들기의 한 획이 된다
            pending = nil
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
        if segmentSize >= config.fullStroke {
            // 3/4바퀴를 넘긴 획은 흔들기가 아니다 → 바로 실행
            let event = pending ?? CrownGestureEvent(input: sign > 0 ? .primary : .secondary, startedAt: segmentStart)
            fire(at: at)
            return event
        }
        if segmentSize >= config.halfTurn, pending == nil {
            pending = CrownGestureEvent(input: sign > 0 ? .primary : .secondary, startedAt: segmentStart)
        }
        return flushed
    }

    /// 반 바퀴를 넘긴 획이 `strokeEnd` 동안 멈춰 있었으면 실행한다
    public mutating func flush(at: Double) -> CrownGestureEvent? {
        guard let event = pending, let last = lastAt, at - last >= config.strokeEnd else { return nil }
        pending = nil
        fire(at: last + config.strokeEnd)
        return event
    }

    private mutating func resetTracking() {
        direction = 0
        segmentSize = 0
        reversals = []
        pending = nil
    }

    private mutating func fire(at: Double) {
        resetTracking()
        cooldownUntil = at + config.cooldown
    }
}

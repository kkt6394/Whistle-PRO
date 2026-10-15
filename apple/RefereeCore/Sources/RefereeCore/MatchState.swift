public struct PeriodRef: Hashable, Sendable {
    public enum Kind: String, Sendable { case regular, extra }

    public var kind: Kind
    /// 1부터 시작
    public var index: Int

    public init(_ kind: Kind, _ index: Int) {
        self.kind = kind
        self.index = index
    }

    /// 2기간: 1H/2H, 4기간: Q1~Q4, 연장: ET1/ET2
    public func label(periodCount: Int) -> String {
        switch kind {
        case .regular: periodCount == 4 ? "Q\(index)" : "\(index)H"
        case .extra: "ET\(index)"
        }
    }
}

public enum FinishReason: Equatable, Sendable {
    case completed
    case abandoned(reason: String)
}

public enum Phase: Equatable, Sendable {
    case notStarted
    case period(PeriodRef)
    /// 기간 사이 휴식 (스펙의 break)
    case interval(after: PeriodRef)
    case preExtraTime
    case penalties
    case finished(FinishReason)

    public var isPeriod: Bool { if case .period = self { true } else { false } }
    public var isInterval: Bool { if case .interval = self { true } else { false } }
}

public struct StoppageInterval: Equatable, Sendable {
    public var start: Double
    public var end: Double?
}

public struct PeriodRecord: Equatable, Sendable {
    public var ref: PeriodRef
    public var startedAt: Double
    public var endedAt: Double?
    /// 규정 길이(초). adjustPeriodLength로 진행 중에도 바뀔 수 있다.
    public var nominalLength: Int
    /// 이 기간 시작 시점의 누적 경기 시각(초) = 앞선 기간들의 규정 길이 합
    public var offset: Int
    public var stoppages: [StoppageInterval]
}

public struct EventTime: Equatable, Sendable {
    /// 기록 시점에 진행 중이었거나 마지막으로 끝난 기간
    public var period: PeriodRef
    /// 기간 진행 중에 기록됐으면 true (휴식·연장 대기·승부차기 중이면 false)
    public var duringPlay: Bool
    public var matchSecond: Int
}

public struct RecordedEvent: Equatable, Sendable {
    public var id: String
    public var timestamp: Double
    public var payload: EventPayload
    public var time: EventTime
}

public enum EngineError: Error, Equatable, Sendable {
    case invalidInPhase
    case invalidValue
    case unknownEvent
    case shootoutFirstTeamMissing
    case notYourTurn
}

/// 경기 상태. 직접 조작하지 말고 MatchEngine.reduce로 얻는다.
public struct MatchState: Equatable, Sendable {
    public let rules: RuleSet
    public internal(set) var phase: Phase = .notStarted
    public internal(set) var periods: [PeriodRecord] = []
    public internal(set) var events: [RecordedEvent] = []
    public internal(set) var regularKickoff: Team?
    public internal(set) var extraKickoff: Team?
    public internal(set) var shootoutFirstTeam: Team?
    /// 다음 정규 기간에 쓸 길이 (adjustPeriodLength로 변경)
    public internal(set) var regularPeriodLength: Int
    /// 다음 연장 기간에 쓸 길이
    public internal(set) var extraPeriodLength: Int
    /// 적용되지 못하고 무시된 명령 ID (로그 순서)
    public internal(set) var ignoredCommandIDs: [String] = []
    /// 경기를 끝낸 명령의 시각 (마지막 기간 종료·중단·승부를 가른 킥). 끝난 뒤 기록을 고쳐도 그대로
    public internal(set) var finishedAt: Double?
    /// 승부차기 중에 중단했다 (킥이 없어도 라벨은 PK)
    public internal(set) var abandonedDuringShootout = false

    public init(rules: RuleSet) {
        self.rules = rules
        regularPeriodLength = rules.periodLength
        extraPeriodLength = rules.extraTimePeriodLength
    }
}

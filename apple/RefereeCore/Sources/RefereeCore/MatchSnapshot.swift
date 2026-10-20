/// 특정 시점의 경기 모습. 공용 시나리오 비교와 UI 표시에 쓴다.
/// 키 정의: shared/engine-scenarios/README.md
public struct MatchSnapshot: Encodable, Equatable, Sendable {
    public struct Event: Encodable, Equatable, Sendable {
        public var id: String
        public var type: String
        public var team: Team
        public var number: Int?
        public var numberOut: Int?
        public var numberIn: Int?
        public var matchSecond: Int
        public var warnings: [String]
    }

    public struct SinBin: Encodable, Equatable, Sendable {
        public var eventId: String
        public var team: Team
        public var number: Int?
        public var remaining: Int
    }

    public struct Alert: Encodable, Equatable, Sendable {
        public var kind: String
        public var at: Double
    }

    public var phase: String
    public var period: String?
    public var finishReason: String?
    public var abandonReason: String?
    public var score: TeamPair<Int>
    public var shootoutScore: TeamPair<Int>
    public var nextShootoutTeam: Team?
    public var elapsed: Int?
    public var matchSecond: Int?
    public var remaining: Int?
    public var addedTime: Int?
    public var intervalRemaining: Int?
    public var stoppageTotals: [Int]
    public var stoppageRunning: Bool
    public var kickoffTeam: Team?
    public var playersOnField: TeamPair<Int>
    public var substitutions: TeamPair<Int>
    public var events: [Event]
    public var sinBins: [SinBin]
    public var warnings: [String]
    public var nextAlert: Alert?
    public var ignored: [String]
}

extension Phase {
    var name: String {
        switch self {
        case .notStarted: "notStarted"
        case .period: "period"
        case .interval: "interval"
        case .preExtraTime: "preExtraTime"
        case .penalties: "penalties"
        case .finished: "finished"
        }
    }
}

extension MatchSnapshot.Event {
    init(_ resolved: ResolvedEvent) {
        var number: Int?
        var numberOut: Int?
        var numberIn: Int?
        switch resolved.event.payload {
        case .goal(_, let n, _), .card(_, let n, _), .penaltyKick(_, let n, _):
            number = n
        case .substitution(_, let out, let into):
            numberOut = out
            numberIn = into
        }
        self.init(
            id: resolved.event.id,
            type: resolved.kind.rawValue,
            team: resolved.event.payload.team,
            number: number,
            numberOut: numberOut,
            numberIn: numberIn,
            matchSecond: resolved.event.time.matchSecond,
            warnings: resolved.warnings.map(\.rawValue))
    }
}

extension MatchState {
    public func snapshot(at now: Double, warningBeforeEnd: Int, breakWarningBeforeEnd: Int) -> MatchSnapshot {
        let reading = self.clock(at: now)
        let shootoutStatus = self.shootout
        var finishReason: String?
        var abandonReason: String?
        if case .finished(let reason) = phase {
            switch reason {
            case .completed:
                finishReason = "completed"
            case .abandoned(let why):
                finishReason = "abandoned"
                abandonReason = why
            }
        }
        return MatchSnapshot(
            phase: phase.name,
            period: currentPeriodLabel,
            finishReason: finishReason,
            abandonReason: abandonReason,
            score: score,
            shootoutScore: shootoutStatus.goals,
            nextShootoutTeam: phase == .penalties ? shootoutStatus.next : nil,
            elapsed: reading?.elapsed,
            matchSecond: reading?.matchSecond,
            remaining: reading?.remaining,
            addedTime: reading?.addedTime,
            intervalRemaining: intervalRemaining(at: now),
            stoppageTotals: stoppageTotals(at: now),
            stoppageRunning: reading?.stoppageRunning ?? false,
            kickoffTeam: kickoffTeam,
            playersOnField: playersOnField(at: now),
            substitutions: substitutionCounts,
            events: resolvedEvents.map { MatchSnapshot.Event($0) },
            sinBins: activeSinBins(at: now).map {
                MatchSnapshot.SinBin(eventId: $0.eventId, team: $0.team, number: $0.number, remaining: $0.remaining)
            },
            warnings: matchWarnings(at: now).map(\.code),
            nextAlert: nextAlert(after: now, warningBeforeEnd: warningBeforeEnd, breakWarningBeforeEnd: breakWarningBeforeEnd).map {
                MatchSnapshot.Alert(kind: $0.kind.rawValue, at: $0.at)
            },
            ignored: ignoredCommandIDs)
    }
}

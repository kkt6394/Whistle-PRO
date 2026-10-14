import Foundation
import RefereeCore

public struct ReportLine: Equatable, Sendable {
    public var minute: EventMinute
    public var team: Team
    public var kind: ResolvedEventKind
    public var detail: String
}

public struct PeriodSummary: Equatable, Sendable {
    public var label: String
    /// 실제 진행 시간(초)
    public var played: Int
    /// 기록된 중단 시간(초)
    public var stoppage: Int
    /// 규정 시간을 넘겨 진행한 시간(초)
    public var added: Int
}

public struct RuleLine: Equatable, Sendable {
    public var field: RuleSet.Field
    /// RuleSet.value(of:) 원시 값 (표시는 앱이 바꾼다)
    public var value: String
}

public enum ReportOutcome: Codable, Equatable, Sendable {
    case inProgress
    case completed
    case abandoned(String)
}

/// 경기 보고서·상세 화면에 쓰는 요약. 경기 기록을 재생해서 만든다.
public struct MatchReport: Equatable, Sendable {
    public var homeName: String
    public var awayName: String
    public var homeColor: TeamColor
    public var awayColor: TeamColor
    /// 첫 킥오프 시각 (킥오프 전이면 기록 생성 시각)
    public var startedAt: Double
    public var outcome: ReportOutcome
    public var score: TeamPair<Int>
    public var shootout: TeamPair<Int>?
    public var goals: [ReportLine]
    /// 옐로·경고 누적 퇴장·레드·일시 퇴장
    public var discipline: [ReportLine]
    public var substitutions: [ReportLine]
    public var periods: [PeriodSummary]
    /// FIFA 표준과 다른 규칙
    public var ruleDeviations: [RuleLine]
    public var workoutPeriods: [WorkoutStats]
    public var workoutOverall: WorkoutStats?

    public init(bundle: MatchBundle) {
        let setup = bundle.record.setup
        let state = MatchEngine.reduce(rules: setup.rules, commands: bundle.record.commands)
        let end = state.periods.last.map { $0.endedAt ?? $0.startedAt } ?? bundle.record.createdAt

        homeName = setup.homeName
        awayName = setup.awayName
        homeColor = setup.homeColor
        awayColor = setup.awayColor
        startedAt = state.periods.first?.startedAt ?? bundle.record.createdAt
        outcome = switch state.phase {
        case .finished(.completed): .completed
        case .finished(.abandoned(let reason)): .abandoned(reason)
        default: .inProgress
        }
        score = state.score
        let shootoutStatus = state.shootout
        shootout = shootoutStatus.kicks.home + shootoutStatus.kicks.away > 0 ? shootoutStatus.goals : nil

        let rows = Array(state.eventRows(setup: setup).reversed())
        func lines(_ kinds: Set<ResolvedEventKind>) -> [ReportLine] {
            rows.filter { kinds.contains($0.kind) }
                .map { ReportLine(minute: $0.minute, team: $0.team, kind: $0.kind, detail: $0.detail) }
        }
        goals = lines([.goal, .ownGoal])
        discipline = lines([.yellow, .secondYellow, .red, .sinBin])
        substitutions = lines([.substitution])

        let stoppages = state.stoppageTotals(at: end)
        periods = state.periods.enumerated().map { index, p in
            let played = max(0, wholeSeconds((p.endedAt ?? end) - p.startedAt))
            return PeriodSummary(
                label: p.ref.label(periodCount: setup.rules.periodCount),
                played: played,
                stoppage: stoppages[index],
                added: max(0, played - p.nominalLength))
        }
        ruleDeviations = setup.rules.deviations().map { RuleLine(field: $0, value: setup.rules.value(of: $0)) }

        if let workout = bundle.workout {
            workoutPeriods = WorkoutAnalysis.perPeriod(workout, state: state, now: end)
            workoutOverall = WorkoutAnalysis.overall(workout, state: state, now: end)
        } else {
            workoutPeriods = []
            workoutOverall = nil
        }
    }
}

/// 목록·시즌 통계용 가벼운 요약. 경기마다 <id>.summary.json으로 저장한다.
public struct MatchSummary: Codable, Equatable, Sendable, Identifiable {
    /// 통계 값이 들어간 요약 형식. 이보다 낮거나 없는 요약은 전체 기록에서 다시 만든다.
    public static let currentVersion = 2

    public var id: UUID
    public var createdAt: Double
    /// 첫 킥오프 시각 (킥오프 전이면 기록 생성 시각)
    public var startedAt: Double
    public var homeName: String
    public var awayName: String
    public var homeColor: TeamColor
    public var awayColor: TeamColor
    public var homeScore: Int
    public var awayScore: Int
    /// 승부차기 [홈, 원정] (승부차기가 없었으면 nil)
    public var shootout: [Int]?
    public var outcome: ReportOutcome

    // 아래는 버전 2부터 (이전 요약에는 없다)
    public var summaryVersion: Int?
    /// 골 + 자책골
    public var goals: Int?
    public var yellows: Int?
    /// 레드 + 경고 누적 퇴장
    public var reds: Int?
    public var sinBins: Int?
    /// 기간마다 규정 시간을 넘겨 진행한 시간의 합 (초)
    public var addedSeconds: Int?
    /// 운동 데이터 (없으면 nil)
    public var distance: Double?
    public var averageHeartRate: Int?
    public var sprints: Int?
    public var zones: SpeedZones?

    public init(record: MatchRecord) {
        self.init(bundle: MatchBundle(record: record, workout: nil))
    }

    public init(bundle: MatchBundle) {
        let record = bundle.record
        let report = MatchReport(bundle: bundle)
        id = record.id
        createdAt = record.createdAt
        startedAt = report.startedAt
        homeName = report.homeName
        awayName = report.awayName
        homeColor = report.homeColor
        awayColor = report.awayColor
        homeScore = report.score.home
        awayScore = report.score.away
        shootout = report.shootout.map { [$0.home, $0.away] }
        outcome = report.outcome
        summaryVersion = Self.currentVersion
        goals = report.goals.count
        yellows = report.discipline.filter { $0.kind == .yellow }.count
        reds = report.discipline.filter { $0.kind == .red || $0.kind == .secondYellow }.count
        sinBins = report.discipline.filter { $0.kind == .sinBin }.count
        addedSeconds = report.periods.reduce(0) { $0 + $1.added }
        distance = report.workoutOverall?.distance
        averageHeartRate = report.workoutOverall?.averageHeartRate
        sprints = report.workoutOverall?.sprints
        zones = report.workoutOverall?.zones
    }

    public var isCurrent: Bool { (summaryVersion ?? 1) >= Self.currentVersion }
}

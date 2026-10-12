import Foundation
import RefereeCore

public struct SinBinBadge: Equatable, Sendable {
    public var team: Team
    public var number: Int?
    /// 남은 시간 "4:12"
    public var remaining: String
}

public struct ShootoutDisplay: Equatable, Sendable {
    public var score: TeamPair<Int>
    /// 다음 키커 팀 (승부차기 진행 중이고 선축 지정 후에만)
    public var next: Team?
    /// 아직 먼저 찰 팀을 고르지 않았다
    public var needsFirstTeam: Bool
}

/// 워치 화면에 그대로 찍을 값. 뷰는 계산 없이 이것만 표시한다.
public struct MatchDisplay: Equatable, Sendable {
    public enum Screen: Equatable, Sendable {
        case notStarted, period, interval, preExtraTime, penalties, finished
    }

    public var screen: Screen
    /// 마지막으로 시작한 기간 (1H, Q2, ET1)
    public var periodLabel: String?
    /// 휴식·연장 대기 중일 때 다음 기간
    public var nextPeriodLabel: String?
    /// 큰 시계. 경과 모드는 규정 시간에서 멈추고 초과분은 addedTime으로 보여준다.
    public var mainClock: String
    /// "+2:13" (규정 시간 초과 시에만)
    public var addedTime: String?
    /// 이 기간 누적 중단 시간 "1:24" (중단이 있었거나 진행 중일 때만)
    public var stoppage: String?
    public var stoppageRunning: Bool
    /// 휴식 중이면 남은 시간, 연장 대기 중이면 기다린 시간
    public var intervalClock: String?
    public var score: TeamPair<Int>
    public var kickoffTeam: Team?
    public var sinBins: [SinBinBadge]
    /// 최소 인원 미만인 팀
    public var shortHandedTeams: [Team]
    /// 승부차기 중이거나 승부차기가 있었던 경기
    public var shootout: ShootoutDisplay?

    public init(state: MatchState, now: Double, clockDisplay: ClockDisplay) {
        screen = switch state.phase {
        case .notStarted: .notStarted
        case .period: .period
        case .interval: .interval
        case .preExtraTime: .preExtraTime
        case .penalties: .penalties
        case .finished: .finished
        }
        periodLabel = state.currentPeriodLabel
        nextPeriodLabel = switch state.phase {
        case .interval(let after): PeriodRef(after.kind, after.index + 1).label(periodCount: state.rules.periodCount)
        case .preExtraTime: PeriodRef(.extra, 1).label(periodCount: state.rules.periodCount)
        default: nil
        }
        if let reading = state.clock(at: now) {
            mainClock = Self.format(clockDisplay == .elapsed ? reading.matchSecond - reading.addedTime : reading.remaining)
            addedTime = reading.addedTime > 0 ? "+" + Self.formatShort(reading.addedTime) : nil
            stoppage = reading.stoppageTotal > 0 || reading.stoppageRunning ? Self.formatShort(reading.stoppageTotal) : nil
            stoppageRunning = reading.stoppageRunning
        } else {
            mainClock = Self.format(clockDisplay == .elapsed ? 0 : state.regularPeriodLength)
            addedTime = nil
            stoppage = nil
            stoppageRunning = false
        }
        if state.phase == .preExtraTime, let end = state.periods.last?.endedAt {
            intervalClock = Self.format(Int(max(0, now - end)))
        } else {
            intervalClock = state.intervalRemaining(at: now).map(Self.format)
        }
        score = state.score
        kickoffTeam = state.kickoffTeam
        sinBins = state.activeSinBins(at: now).map {
            SinBinBadge(team: $0.team, number: $0.number, remaining: Self.formatShort($0.remaining))
        }
        shortHandedTeams = state.matchWarnings(at: now).map {
            switch $0 {
            case .belowMinimumPlayers(let team): team
            }
        }
        let status = state.shootout
        if state.phase == .penalties || status.kicks.home + status.kicks.away > 0 {
            shootout = ShootoutDisplay(
                score: status.goals,
                next: state.phase == .penalties ? status.next : nil,
                needsFirstTeam: state.phase == .penalties && state.shootoutFirstTeam == nil)
        } else {
            shootout = nil
        }
    }

    /// "MM:SS". 100분이 넘으면 "100:00"처럼 분 자리가 늘어난다.
    public static func format(_ seconds: Int) -> String {
        let s = max(0, seconds)
        return String(format: "%02d:%02d", s / 60, s % 60)
    }

    /// "M:SS"
    public static func formatShort(_ seconds: Int) -> String {
        let s = max(0, seconds)
        return String(format: "%d:%02d", s / 60, s % 60)
    }
}

import Foundation
import RefereeCore

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
    /// 휴식 남은 시간 (휴식 중에만)
    public var intervalClock: String?
    public var score: TeamPair<Int>
    public var kickoffTeam: Team?

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
        intervalClock = state.intervalRemaining(at: now).map(Self.format)
        score = state.score
        kickoffTeam = state.kickoffTeam
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

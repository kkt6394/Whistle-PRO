import Foundation
import RefereeCore

public struct SinBinBadge: Equatable, Sendable {
    public var team: Team
    public var number: Int?
    /// 남은 시간 "4:12"
    public var remaining: String
    /// 남은 시간(초). 가장 먼저 끝나는 일시 퇴장을 고를 때 쓴다.
    public var seconds: Int
}

public struct ShootoutDisplay: Equatable, Sendable {
    public var score: TeamPair<Int>
    /// 다음 키커 팀 (승부차기 진행 중이고 선축 지정 후에만)
    public var next: Team?
    /// 아직 먼저 찰 팀을 고르지 않았다
    public var needsFirstTeam: Bool
    /// 팀별 킥 성공 여부 (찬 순서)
    public var results: TeamPair<[Bool]>
    /// 기본 키커 수
    public var kicksPerTeam: Int
}

/// 큰 시계 아래 작은 줄의 시계 (2026-10-15 사용자 결정)
public enum SubClock: Equatable, Sendable {
    /// 경과 모드: 정규시간까지 남은 시간 "32:26"
    case remaining(String)
    /// 남은 시간 모드: 흐른 경기 시간(90분 기준) "12:34"
    case elapsed(String)
    /// 추가시간: 축구 표기 "45+2:13" (큰 자리에는 +2:13)
    case overtime(String)
}

/// 휴식 화면 머리글에 쓸 휴식 종류 (2026-11-04 휴식 화면 critique)
public enum IntervalKind: Equatable, Sendable {
    case halfTime, quarterBreak, extraTimeBreak, preExtraTime
}

/// 휴식 시계의 상태 (2026-11-04 휴식 화면 critique)
public enum IntervalClockTone: Equatable, Sendable {
    /// 남은 휴식을 내려 센다
    case counting
    /// 남은 휴식 1분 이하
    case lastMinute
    /// 휴식 길이를 넘겼다: "+1:23"으로 위로 센다
    case overrun
    /// 연장 대기: 기다린 시간을 "+11:48"로 위로 센다
    case waiting
}

/// 링 표시용 진행값 (초)
public struct ClockProgress: Equatable, Sendable {
    public var elapsed: Int
    public var length: Int

    public init(elapsed: Int, length: Int) {
        self.elapsed = elapsed
        self.length = length
    }
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
    /// 큰 시계 아래 작은 줄 (기간 진행 중에만)
    public var subClock: SubClock?
    /// 이 기간 누적 중단 시간 "1:24" (중단이 있었거나 진행 중일 때만)
    public var stoppage: String?
    public var stoppageRunning: Bool
    /// 휴식 중이면 남은 시간 "09:50"(넘기면 "+1:23"), 연장 대기 중이면 기다린 시간 "+11:48"
    public var intervalClock: String?
    /// 휴식·연장 대기 종류 (머리글)
    public var intervalKind: IntervalKind?
    /// 휴식 시계 상태 (색·테두리)
    public var intervalTone: IntervalClockTone?
    public var score: TeamPair<Int>
    public var kickoffTeam: Team?
    public var sinBins: [SinBinBadge]
    /// 최소 인원 미만인 팀
    public var shortHandedTeams: [Team]
    /// 승부차기 중이거나 승부차기가 있었던 경기
    public var shootout: ShootoutDisplay?
    /// 기간 진행 중일 때 경과·규정 길이
    public var periodProgress: ClockProgress?
    /// 휴식 중일 때 경과·휴식 길이
    public var intervalProgress: ClockProgress?

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
            if case .period = state.phase {
                // 길이 = 규정 길이: (경과 - 추가시간)은 규정 안에서 흐른 시간, 거기에 남은 시간을 더하면 규정 길이다.
                // 추가시간 중에는 남은 시간이 0이고 경과 - 추가시간이 규정 길이다
                periodProgress = ClockProgress(elapsed: reading.elapsed, length: reading.elapsed - reading.addedTime + reading.remaining)
                let regulationEnd = reading.matchSecond - reading.addedTime
                subClock = if reading.addedTime > 0 {
                    .overtime("\(regulationEnd / 60)+" + Self.formatShort(reading.addedTime))
                } else if clockDisplay == .elapsed {
                    .remaining(Self.formatShort(reading.remaining))
                } else {
                    .elapsed(Self.formatShort(reading.matchSecond))
                }
            } else {
                periodProgress = nil
                subClock = nil
            }
        } else {
            mainClock = Self.format(clockDisplay == .elapsed ? 0 : state.regularPeriodLength)
            addedTime = nil
            stoppage = nil
            stoppageRunning = false
            periodProgress = nil
            subClock = nil
        }
        if state.phase == .preExtraTime, let end = state.periods.last?.endedAt {
            intervalClock = "+" + Self.formatShort(max(0, wholeSeconds(now - end)))
            intervalKind = .preExtraTime
            intervalTone = .waiting
        } else if case .interval(let after) = state.phase, let remaining = state.intervalRemaining(at: now),
                  let end = state.periods.last?.endedAt {
            intervalKind = switch after.kind {
            case .extra: .extraTimeBreak
            case .regular: state.rules.periodCount == 4 && after.index != 2 ? .quarterBreak : .halfTime
            }
            if remaining > 0 {
                intervalClock = Self.format(remaining)
                intervalTone = remaining <= 60 ? .lastMinute : .counting
            } else {
                // 휴식 길이를 넘긴 시간 (팀이 늦게 나왔을 때 얼마나 기다렸는지)
                let over = wholeSeconds(now - end) - state.intervalLength(after: after)
                intervalClock = "+" + Self.formatShort(max(0, over))
                intervalTone = .overrun
            }
        } else {
            intervalClock = nil
            intervalKind = nil
            intervalTone = nil
        }
        if case .interval(let after) = state.phase, let remaining = state.intervalRemaining(at: now) {
            let total = state.intervalLength(after: after)
            intervalProgress = ClockProgress(elapsed: max(0, total - remaining), length: total)
        } else {
            intervalProgress = nil
        }
        score = state.score
        kickoffTeam = state.kickoffTeam
        sinBins = state.activeSinBins(at: now).map {
            SinBinBadge(team: $0.team, number: $0.number, remaining: Self.formatShort($0.remaining), seconds: $0.remaining)
        }
        shortHandedTeams = state.matchWarnings(at: now).map {
            switch $0 {
            case .belowMinimumPlayers(let team): team
            }
        }
        let status = state.shootout
        if state.phase == .penalties || status.kicks.home + status.kicks.away > 0 {
            var results = TeamPair<[Bool]>(home: [], away: [])
            for e in state.events {
                if case .penaltyKick(let team, _, let scored) = e.payload { results[team].append(scored) }
            }
            shootout = ShootoutDisplay(
                score: status.goals,
                next: state.phase == .penalties ? status.next : nil,
                needsFirstTeam: state.phase == .penalties && state.shootoutFirstTeam == nil,
                results: results,
                kicksPerTeam: state.rules.penaltyKicksPerTeam)
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

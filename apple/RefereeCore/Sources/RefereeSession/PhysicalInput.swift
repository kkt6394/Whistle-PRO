import RefereeCore

/// 워치 물리 입력 (크라운 누름·뒤로 키 등). 누른 횟수로 구분한다.
public enum PhysicalInput: Sendable, Equatable {
    case primary, secondary, undo

    /// 1번 → primary, 2번 → secondary, 3번 → undo, 그 밖은 nil
    public init?(pressCount: Int) {
        switch pressCount {
        case 1: self = .primary
        case 2: self = .secondary
        case 3: self = .undo
        default: return nil
        }
    }
}

/// 물리 입력이 일으키는 동작
public enum PhysicalAction: Sendable, Equatable {
    case kickoff, pause, resume, openGoal, goal(Team), cancelGoal, endPeriod, startPeriod,
         shootoutFirstTeam(Team), shootoutKick(scored: Bool), setKickoffTeam(Team), undo, none

    /// 동작마다 다른 진동. 경고 알림 패턴과 겹치지 않는다. none은 진동 없음.
    public var hapticPattern: HapticPattern? {
        switch self {
        case .kickoff, .startPeriod: HapticPattern(tone: .start, repeats: 1, interval: 0)
        case .pause: HapticPattern(tone: .stop, repeats: 2, interval: 0.3)
        case .resume: HapticPattern(tone: .start, repeats: 2, interval: 0.3)
        case .openGoal: HapticPattern(tone: .notification, repeats: 1, interval: 0)
        case .goal(.home), .shootoutFirstTeam(.home), .setKickoffTeam(.home), .shootoutKick(scored: true):
            HapticPattern(tone: .success, repeats: 1, interval: 0)
        case .goal(.away), .shootoutFirstTeam(.away), .setKickoffTeam(.away):
            HapticPattern(tone: .success, repeats: 2, interval: 0.3)
        case .shootoutKick(scored: false): HapticPattern(tone: .stop, repeats: 2, interval: 0.3)
        case .endPeriod: HapticPattern(tone: .stop, repeats: 3, interval: 0.3)
        case .cancelGoal, .undo: HapticPattern(tone: .retry, repeats: 3, interval: 0.3)
        case .none: nil
        }
    }
}

/// 입력을 해석할 때 필요한 화면 상태
public enum PhysicalContext: Sendable, Equatable {
    /// 킥오프 대기 화면 (경기 표시 없음)
    case ready
    /// needsKickoffTeam: 연장 대기인데 선축(연장 전 동전 던지기, IFAB Law 8)이 아직 없음 (2026-11-07)
    case match(
        screen: MatchDisplay.Screen, stoppageRunning: Bool, shootout: ShootoutDisplay?, goalOpen: Bool,
        needsKickoffTeam: Bool = false)

    public init(display: MatchDisplay, goalOpen: Bool) {
        self = .match(
            screen: display.screen, stoppageRunning: display.stoppageRunning,
            shootout: display.shootout, goalOpen: goalOpen,
            needsKickoffTeam: display.screen == .preExtraTime && display.kickoffTeam == nil)
    }
}

/// 상태 + 입력 → 동작. 골 선택 중(goalOpen)은 기간 진행 중에만 의미가 있다.
public func physicalAction(for input: PhysicalInput, in context: PhysicalContext) -> PhysicalAction {
    guard case let .match(screen, stoppageRunning, shootout, goalOpen, needsKickoffTeam) = context else {
        return input == .primary ? .kickoff : .none
    }
    switch screen {
    case .notStarted:
        return .none
    case .period:
        if goalOpen {
            switch input {
            case .primary: return .goal(.home)
            case .secondary: return .goal(.away)
            case .undo: return .cancelGoal
            }
        }
        switch input {
        case .primary: return stoppageRunning ? .resume : .pause
        case .secondary: return stoppageRunning ? .endPeriod : .openGoal
        case .undo: return .undo
        }
    case .interval, .preExtraTime:
        // 2026-11-07 연장 선축이 없으면 승부차기 먼저 찰 팀처럼 한 번=홈·두 번=원정으로 먼저 기록한다.
        // 크라운으로 돌리다 선축 없이 연장이 시작되던 것. 휴식(정규 후반)은 킥오프 팀이 자동이라 그대로.
        if screen == .preExtraTime && needsKickoffTeam {
            switch input {
            case .primary: return .setKickoffTeam(.home)
            case .secondary: return .setKickoffTeam(.away)
            case .undo: return .undo
            }
        }
        switch input {
        case .primary: return .startPeriod
        case .secondary: return .none
        case .undo: return .undo
        }
    case .penalties:
        if input == .undo { return .undo }
        if shootout?.needsFirstTeam == true {
            return .shootoutFirstTeam(input == .primary ? .home : .away)
        }
        if shootout?.next != nil {
            return .shootoutKick(scored: input == .primary)
        }
        return .none
    case .finished:
        return input == .undo ? .undo : .none
    }
}

/// 물리 입력 시간 상수 (초)
public enum PhysicalTiming {
    /// 연속 누름을 한 묶음으로 보는 시간
    public static let pressWindow = 0.4
    /// 골 팀 선택을 기다리는 시간
    public static let goalPickTimeout = 3.0
    /// 중단 시간이 흐르는 동안 알려주는 간격
    public static let stoppageReminder = 30.0
}

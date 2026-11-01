import Testing
import RefereeCore
@testable import RefereeSession

struct PhysicalInputTests {
    private func shootout(needsFirstTeam: Bool = false, next: Team? = nil) -> ShootoutDisplay {
        ShootoutDisplay(
            score: TeamPair(home: 0, away: 0), next: next, needsFirstTeam: needsFirstTeam,
            results: TeamPair(home: [], away: []), kicksPerTeam: 5)
    }

    private func match(
        _ screen: MatchDisplay.Screen, stoppage: Bool = false, shootout: ShootoutDisplay? = nil,
        goalOpen: Bool = false
    ) -> PhysicalContext {
        .match(screen: screen, stoppageRunning: stoppage, shootout: shootout, goalOpen: goalOpen)
    }

    @Test func mappingTable() {
        let rows: [(String, PhysicalContext, [PhysicalAction])] = [
            ("ready", .ready, [.kickoff, .none, .none]),
            ("period", match(.period), [.pause, .openGoal, .undo]),
            ("stoppage", match(.period, stoppage: true), [.resume, .endPeriod, .undo]),
            ("goalOpen", match(.period, goalOpen: true), [.goal(.home), .goal(.away), .cancelGoal]),
            ("interval", match(.interval), [.startPeriod, .none, .undo]),
            ("preExtraTime", match(.preExtraTime), [.startPeriod, .none, .undo]),
            ("firstTeam", match(.penalties, shootout: shootout(needsFirstTeam: true)),
             [.shootoutFirstTeam(.home), .shootoutFirstTeam(.away), .undo]),
            ("kick", match(.penalties, shootout: shootout(next: .home)),
             [.shootoutKick(scored: true), .shootoutKick(scored: false), .undo]),
            ("decided", match(.penalties, shootout: shootout()), [.none, .none, .undo]),
            ("notStarted", match(.notStarted), [.none, .none, .none]),
            ("finished", match(.finished), [.none, .none, .undo]),
        ]
        let inputs: [PhysicalInput] = [.primary, .secondary, .undo]
        for (name, context, expected) in rows {
            for (input, action) in zip(inputs, expected) {
                #expect(physicalAction(for: input, in: context) == action, "\(name) \(input)")
            }
        }
    }

    @Test func goalOpenOutsidePeriodIsIgnored() {
        let context = match(.interval, goalOpen: true)
        #expect(physicalAction(for: .primary, in: context) == .startPeriod)
    }

    @Test func contextFromDisplay() {
        let state = makeState([(.startPeriod, 0), (.toggleStoppage, 100)])
        let display = MatchDisplay(state: state, now: 130, clockDisplay: .elapsed)
        let context = PhysicalContext(display: display, goalOpen: false)
        #expect(context == .match(screen: .period, stoppageRunning: true, shootout: nil, goalOpen: false))
    }

    @Test func pressCount() {
        #expect(PhysicalInput(pressCount: 1) == .primary)
        #expect(PhysicalInput(pressCount: 2) == .secondary)
        #expect(PhysicalInput(pressCount: 3) == .undo)
        #expect(PhysicalInput(pressCount: 0) == nil)
        #expect(PhysicalInput(pressCount: 4) == nil)
    }

    private let allActions: [PhysicalAction] = [
        .kickoff, .startPeriod, .pause, .resume, .openGoal, .goal(.home), .goal(.away),
        .shootoutFirstTeam(.home), .shootoutFirstTeam(.away), .shootoutKick(scored: true),
        .shootoutKick(scored: false), .endPeriod, .cancelGoal, .undo,
    ]

    @Test func hapticTable() {
        func p(_ tone: HapticTone, _ repeats: Int, _ interval: Double) -> HapticPattern {
            HapticPattern(tone: tone, repeats: repeats, interval: interval)
        }
        let expected: [(PhysicalAction, HapticPattern)] = [
            (.kickoff, p(.start, 1, 0)), (.startPeriod, p(.start, 1, 0)),
            (.pause, p(.stop, 2, 0.3)), (.resume, p(.start, 2, 0.3)),
            (.openGoal, p(.notification, 1, 0)),
            (.goal(.home), p(.success, 1, 0)), (.shootoutFirstTeam(.home), p(.success, 1, 0)),
            (.shootoutKick(scored: true), p(.success, 1, 0)),
            (.goal(.away), p(.success, 2, 0.3)), (.shootoutFirstTeam(.away), p(.success, 2, 0.3)),
            (.shootoutKick(scored: false), p(.stop, 2, 0.3)),
            (.endPeriod, p(.stop, 3, 0.3)),
            (.cancelGoal, p(.retry, 3, 0.3)), (.undo, p(.retry, 3, 0.3)),
        ]
        for (action, pattern) in expected {
            #expect(action.hapticPattern == pattern, "\(action)")
        }
        #expect(PhysicalAction.none.hapticPattern == nil)
    }

    @Test func hapticsDoNotCollideWithAlerts() {
        let kinds: [AlertKind] = [.warningBeforeEnd, .periodEnd, .breakEndingSoon, .breakEnd, .sinBinEnd]
        var taken = Set(kinds.map(\.hapticPattern))
        taken.insert(HapticPattern(tone: .stop, repeats: 1, interval: 0))
        for action in allActions {
            #expect(!taken.contains(action.hapticPattern!), "\(action)")
        }
    }

    @Test func hapticsDistinctWithinState() {
        let contexts: [PhysicalContext] = [
            .ready, match(.period), match(.period, stoppage: true), match(.period, goalOpen: true),
            match(.interval), match(.penalties, shootout: shootout(needsFirstTeam: true)),
            match(.penalties, shootout: shootout(next: .away)), match(.finished),
        ]
        for context in contexts {
            let patterns = [PhysicalInput.primary, .secondary, .undo]
                .compactMap { physicalAction(for: $0, in: context).hapticPattern }
            #expect(Set(patterns).count == patterns.count, "\(context)")
        }
    }
}

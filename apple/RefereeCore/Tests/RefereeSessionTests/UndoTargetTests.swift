import Testing
import RefereeCore
@testable import RefereeSession

/// 메뉴 [실행 취소] 아래 한 줄: 되돌릴 마지막 명령 (2026-11-04 A6 critique)
@MainActor
struct UndoTargetTests {
    private func started() -> (MatchSession, FakeClock) {
        let clock = FakeClock()
        clock.t = 1000
        let s = MatchSession.start(setup: MatchSetup(), store: MemoryStore(), now: { clock.t })
        s.send(.startPeriod)
        return (s, clock)
    }

    @Test func nothingToUndoRightAfterKickoff() {
        let (s, _) = started()
        #expect(s.undoTarget == nil)
    }

    @Test func recordedEventShowsItsRow() {
        let (s, clock) = started()
        clock.t = 1000 + 22 * 60 + 5
        s.send(.recordEvent(.card(team: .away, number: 8, kind: .yellow)))
        guard case .recorded(let row) = s.undoTarget else { Issue.record("기록 이벤트가 아님"); return }
        #expect(row.kind == .yellow)
        #expect(row.team == .away)
        #expect(row.detail == "#8")
        #expect(row.minute == .regular(23))
    }

    @Test func stoppageStartAndResume() {
        let (s, clock) = started()
        clock.t = 1100
        s.send(.toggleStoppage)
        #expect(s.undoTarget == .stoppage(started: true))
        clock.t = 1160
        s.send(.toggleStoppage)
        #expect(s.undoTarget == .stoppage(started: false))
    }

    @Test func periodEndThenSecondHalfStart() {
        let (s, clock) = started()
        clock.t = 1000 + 46 * 60
        s.send(.endPeriod)
        #expect(s.undoTarget == .periodEnded("1H"))
        clock.t += 15 * 60
        s.send(.startPeriod)
        #expect(s.undoTarget == .periodStarted("2H"))
    }

    @Test func editAndDeleteShowTheEventBeforeAndAfter() {
        let (s, clock) = started()
        clock.t = 1300
        s.send(.recordEvent(.goal(team: .home, number: 9, ownGoal: false)))
        let id = s.state.events[0].id
        s.send(.editEvent(id: id, .goal(team: .home, number: 10, ownGoal: false)))
        guard case .edited(let edited) = s.undoTarget else { Issue.record("수정이 아님"); return }
        #expect(edited.detail == "#10")
        s.send(.deleteEvent(id: id))
        guard case .deleted(let deleted) = s.undoTarget else { Issue.record("삭제가 아님"); return }
        #expect(deleted.detail == "#10")
        #expect(deleted.kind == .goal)
    }

    @Test func undoMovesToThePreviousCommand() {
        let (s, clock) = started()
        clock.t = 1300
        s.send(.recordEvent(.goal(team: .home, number: 9, ownGoal: false)))
        clock.t = 1400
        s.send(.toggleStoppage)
        s.undo()
        guard case .recorded = s.undoTarget else { Issue.record("앞 명령으로 가지 않음"); return }
    }
}

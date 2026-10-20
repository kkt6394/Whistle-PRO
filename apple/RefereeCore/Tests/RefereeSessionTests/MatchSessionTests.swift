import Testing
import RefereeCore
@testable import RefereeSession

@MainActor
struct MatchSessionTests {
    private func makeSession(_ setup: MatchSetup = MatchSetup()) -> (MatchSession, MemoryStore, FakeClock) {
        let store = MemoryStore()
        let clock = FakeClock()
        let session = MatchSession.start(setup: setup, store: store, now: { clock.t })
        return (session, store, clock)
    }

    @Test func startPersistsEmptyRecord() {
        let (s, store, _) = makeSession()
        #expect(store.active == s.record)
        #expect(s.record.commands.isEmpty)
        #expect(s.state.phase == .notStarted)
    }

    @Test func acceptedCommandIsStampedAppliedAndPersisted() {
        let (s, store, clock) = makeSession()
        clock.t = 1000
        #expect(s.send(.startPeriod))
        #expect(s.state.phase == .period(PeriodRef(.regular, 1)))
        #expect(store.active?.commands.map(\.timestamp) == [1000])
    }

    @Test func rejectedCommandIsNotLogged() {
        let (s, store, _) = makeSession()
        #expect(!s.send(.endPeriod))
        #expect(s.record.commands.isEmpty)
        #expect(store.active?.commands.isEmpty == true)
    }

    @Test func undoAfterRejectedDoubleTapUndoesTheRealCommand() {
        let (s, _, clock) = makeSession()
        s.send(.startPeriod)
        clock.t = 2700
        #expect(s.send(.endPeriod))
        clock.t = 2701
        #expect(!s.send(.endPeriod))
        #expect(s.undo())
        #expect(s.state.phase == .period(PeriodRef(.regular, 1)))
    }

    @Test func undoWithNothingToUndoReturnsFalse() {
        let (s, _, _) = makeSession()
        #expect(!s.canUndo)
        #expect(!s.undo())
        #expect(s.record.commands.isEmpty)
    }

    @Test func restoringFromSavedRecordRebuildsState() throws {
        let (s, store, clock) = makeSession()
        s.send(.startPeriod)
        clock.t = 100
        s.send(.toggleStoppage)
        let restored = MatchSession(record: try #require(store.active), store: store, now: { clock.t })
        #expect(restored.state == s.state)
    }

    @Test func archivesOnlyFinishedMatch() {
        let (s, store, _) = makeSession()
        s.send(.startPeriod)
        #expect(!s.archiveIfFinished())
        #expect(store.finished.isEmpty)

        s.send(.abandonMatch(reason: "폭우"))
        #expect(s.isFinished)
        #expect(s.archiveIfFinished())
        #expect(store.active == nil)
        #expect(store.finished.count == 1)
    }

    @Test func saveFailureIsReportedButMatchContinues() {
        let (s, store, _) = makeSession()
        store.failSaves = true
        #expect(s.send(.startPeriod))
        #expect(s.state.phase.isPeriod)
        #expect(s.saveError != nil)

        store.failSaves = false
        s.send(.toggleStoppage)
        #expect(s.saveError == nil)
        #expect(store.active?.commands.count == 2)
    }

    @Test func nextAlertUsesSetupWarning() {
        let (s, _, _) = makeSession(MatchSetup(warningBeforeEnd: 600))
        s.send(.startPeriod)
        let alert = s.nextAlert(after: 0)
        #expect(alert?.kind == .warningBeforeEnd)
        #expect(alert?.at == 2100)
    }

    @Test func nextAlertUsesSetupBreakWarning() {
        let (s, _, clock) = makeSession(MatchSetup(breakWarningBeforeEnd: 120))
        s.send(.startPeriod)
        clock.t = 2700
        s.send(.endPeriod)
        let alert = s.nextAlert(after: 2700)
        #expect(alert?.kind == .breakEndingSoon)
        #expect(alert?.at == 3480)
    }
}

/// 최종 리뷰: 킥오프를 실행 취소하면 경기 시작 버튼이 없는 화면에 갇힌다
@MainActor
struct KickoffUndoTests {
    @Test func kickoffItselfCannotBeUndone() {
        let store = MemoryStore()
        let clock = FakeClock()
        let s = MatchSession.start(setup: MatchSetup(), store: store, now: { clock.t })
        s.send(.setKickoffTeam(.home))
        s.send(.startPeriod)
        #expect(!s.canUndo)
        #expect(!s.undo())
        #expect(s.state.phase.isPeriod)

        clock.t = 100
        s.send(.toggleStoppage)
        #expect(s.canUndo)
        #expect(s.undo())
        #expect(s.state.phase.isPeriod)
        #expect(!s.canUndo)
    }
}

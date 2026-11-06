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

    @Test func commandCanBeStampedWithEarlierTime() {
        let (s, _, clock) = makeSession()
        clock.t = 1000
        s.send(.startPeriod)
        clock.t = 1300
        #expect(s.send(.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), at: 1240))
        #expect(s.record.commands.last?.timestamp == 1240)
        #expect(s.state.events.last?.timestamp == 1240)
    }

    @Test func earlierTimeIsClampedBetweenLastCommandAndNow() {
        let (s, _, clock) = makeSession()
        clock.t = 1000
        s.send(.startPeriod)
        clock.t = 1300
        s.send(.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), at: 900)
        #expect(s.record.commands.last?.timestamp == 1000)
        s.send(.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), at: 2000)
        #expect(s.record.commands.last?.timestamp == 1300)
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

    private func makeSession(_ setup: MatchSetup = MatchSetup()) -> (MatchSession, MemoryStore, FakeClock) {
        let store = MemoryStore()
        let clock = FakeClock()
        return (MatchSession.start(setup: setup, store: store, now: { clock.t }), store, clock)
    }

    /// [킥오프 취소]는 첫 기간(전반)이 진행 중일 때만: 기간을 끝냈거나 그 뒤(하프타임·후반·종료)는 안 된다 (2026-11-06)
    @Test func canCancelKickoffOnlyInFirstPeriod() {
        let (s, _, clock) = makeSession()
        #expect(!s.canCancelKickoff, "킥오프 전")
        clock.t = 1000
        s.send(.setKickoffTeam(.away))
        s.send(.startPeriod)
        #expect(s.canCancelKickoff, "킥오프 직후")
        s.send(.toggleStoppage)
        s.send(.recordEvent(.goal(team: .home, number: 9, ownGoal: false)))
        #expect(s.canCancelKickoff, "전반 중 기록이 있어도")
        clock.t = 3700
        s.send(.endPeriod)
        #expect(!s.canCancelKickoff, "하프타임")
        #expect(s.undo())
        #expect(s.canCancelKickoff, "전반 종료를 되돌리면 다시 전반")
        s.send(.endPeriod)
        clock.t = 4600
        s.send(.startPeriod)
        #expect(!s.canCancelKickoff, "후반")
        s.send(.abandonMatch(reason: "비"))
        #expect(s.isFinished)
        #expect(!s.canCancelKickoff, "종료")
    }

    /// 전반에 바로 중단한 경기도 끝난 경기라 취소할 수 없다. 쿼터제도 첫 쿼터만
    @Test func canCancelKickoffNotAfterAbandonAndOnlyFirstQuarter() {
        let (s, _, clock) = makeSession()
        clock.t = 10
        s.send(.startPeriod)
        s.send(.abandonMatch(reason: "비"))
        #expect(!s.canCancelKickoff)

        let (q, _, qc) = makeSession(MatchSetup(rules: RuleSet(periodCount: 4, periodLength: 600, quarterBreakLength: 60)))
        qc.t = 10
        q.send(.startPeriod)
        #expect(q.canCancelKickoff)
        qc.t = 700
        q.send(.endPeriod)
        qc.t = 800
        q.send(.startPeriod)
        #expect(!q.canCancelKickoff, "2쿼터")
    }

    /// Ruling 11·12: 취소하면 버린 경기 setup 전체(이름·규칙·기간)가 설정(draft)으로, 팀 색은 지금 설정과 다를 때만 이번 경기만 색으로
    @Test func kickoffCancelRestoresWholeSetupWithColorsAsOverride() {
        let draft = MatchSetup()
        let played = MatchSetup(rules: RuleSet(periodLength: 1800, halfTimeLength: 600), homeName: "청구", awayName: "영덕",
                                homeColor: .navy, awayColor: .maroon)
        let restored = KickoffCancel.restore(setup: played, draft: draft)
        // draft에는 색만 빼고 경기 setup 그대로
        #expect(restored.draft.homeName == "청구")
        #expect(restored.draft.awayName == "영덕")
        #expect(restored.draft.rules == played.rules)
        #expect(TeamColors(setup: restored.draft) == TeamColors(setup: draft))
        // 색은 이번 경기만 색으로, 합치면 버린 경기 setup과 같다
        #expect(restored.colorOverride == TeamColors(setup: played))
        #expect(restored.colorOverride!.applied(to: restored.draft) == played)

        // 색이 같으면 이번 경기만 색은 없다
        let sameColors = TeamColors(setup: draft).applied(to: played)
        let plain = KickoffCancel.restore(setup: sameColors, draft: draft)
        #expect(plain.colorOverride == nil)
        #expect(plain.draft == sameColors)
    }
}

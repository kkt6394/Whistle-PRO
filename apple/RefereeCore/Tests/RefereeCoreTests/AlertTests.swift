import Testing
@testable import RefereeCore

struct AlertTests {
    @Test func periodHasWarningThenEnd() {
        let s = play([cmd(.startPeriod, at: 0)])
        #expect(s.alerts(after: 0, warningBeforeEnd: 300, breakWarningBeforeEnd: 300) == [
            MatchAlert(kind: .warningBeforeEnd, at: 2400),
            MatchAlert(kind: .periodEnd, at: 2700),
        ])
        #expect(s.nextAlert(after: 2400, warningBeforeEnd: 300, breakWarningBeforeEnd: 300) == MatchAlert(kind: .periodEnd, at: 2700))
        #expect(s.nextAlert(after: 2700, warningBeforeEnd: 300, breakWarningBeforeEnd: 300) == nil)
    }

    @Test func warningCanBeDisabledOrTooLong() {
        let s = play(RuleSet(periodLength: 240), [cmd(.startPeriod, at: 0)])
        #expect(s.alerts(after: 0, warningBeforeEnd: 0, breakWarningBeforeEnd: 300) == [MatchAlert(kind: .periodEnd, at: 240)])
        #expect(s.alerts(after: 0, warningBeforeEnd: 300, breakWarningBeforeEnd: 300) == [MatchAlert(kind: .periodEnd, at: 240)])
    }

    @Test func intervalHasEndingSoonThenEnd() {
        let s = play([cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2750)])
        #expect(s.alerts(after: 2750, warningBeforeEnd: 300, breakWarningBeforeEnd: 300) == [
            MatchAlert(kind: .breakEndingSoon, at: 3350),
            MatchAlert(kind: .breakEnd, at: 3650),
        ])
    }

    @Test func breakWarningCanBeChangedOrDisabled() {
        let s = play([cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2750)])
        #expect(s.alerts(after: 2750, warningBeforeEnd: 300, breakWarningBeforeEnd: 60) == [
            MatchAlert(kind: .breakEndingSoon, at: 3590),
            MatchAlert(kind: .breakEnd, at: 3650),
        ])
        #expect(s.alerts(after: 2750, warningBeforeEnd: 300, breakWarningBeforeEnd: 0) == [
            MatchAlert(kind: .breakEnd, at: 3650),
        ])
    }

    @Test func shortIntervalSkipsEndingSoon() {
        let s = play(RuleSet(halfTimeLength: 90), [cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2700)])
        #expect(s.alerts(after: 2700, warningBeforeEnd: 300, breakWarningBeforeEnd: 300) == [MatchAlert(kind: .breakEnd, at: 2790)])
    }

    @Test func zeroIntervalHasNoAlerts() {
        let s = play(RuleSet(halfTimeLength: 0), [cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2700)])
        #expect(s.alerts(after: 2700, warningBeforeEnd: 300, breakWarningBeforeEnd: 300).isEmpty)
    }

    @Test func sinBinEndIsAlerted() {
        let s = play(RuleSet(sinBin: true), [cmd(.startPeriod, at: 0), card(.home, 4, .sinBin, at: 100)])
        #expect(s.nextAlert(after: 100, warningBeforeEnd: 300, breakWarningBeforeEnd: 300) == MatchAlert(kind: .sinBinEnd, at: 700))
    }

    @Test func noAlertsWhenFinished() {
        #expect(play(twoHalves()).alerts(after: 6300, warningBeforeEnd: 300, breakWarningBeforeEnd: 300).isEmpty)
    }
}

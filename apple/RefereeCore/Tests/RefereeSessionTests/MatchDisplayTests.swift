import Testing
import RefereeCore
@testable import RefereeSession

struct MatchDisplayTests {
    @Test func notStartedShowsZeroOrFullLength() {
        let s = makeState([])
        let elapsed = MatchDisplay(state: s, now: 0, clockDisplay: .elapsed)
        #expect(elapsed.screen == .notStarted)
        #expect(elapsed.mainClock == "00:00")
        #expect(MatchDisplay(state: s, now: 0, clockDisplay: .remaining).mainClock == "45:00")
    }

    @Test func runningClockInBothModes() {
        let s = makeState([(.startPeriod, 0)])
        let d = MatchDisplay(state: s, now: 754, clockDisplay: .elapsed)
        #expect(d.screen == .period)
        #expect(d.periodLabel == "1H")
        #expect(d.mainClock == "12:34")
        #expect(d.addedTime == nil)
        #expect(MatchDisplay(state: s, now: 754, clockDisplay: .remaining).mainClock == "32:26")
    }

    @Test func addedTimeFreezesMainClockAtRegulation() {
        let s = makeState([(.startPeriod, 0)])
        let d = MatchDisplay(state: s, now: 2833, clockDisplay: .elapsed)
        #expect(d.mainClock == "45:00")
        #expect(d.addedTime == "+2:13")
        #expect(MatchDisplay(state: s, now: 2833, clockDisplay: .remaining).mainClock == "00:00")
    }

    @Test func secondHalfContinuesFromFirstHalfLength() {
        let s = makeState([(.startPeriod, 0), (.endPeriod, 2760), (.startPeriod, 3660)])
        #expect(MatchDisplay(state: s, now: 3720, clockDisplay: .elapsed).mainClock == "46:00")
    }

    @Test func stoppageShownWhileRunningOrAccumulated() {
        let running = makeState([(.startPeriod, 0), (.toggleStoppage, 100)])
        let d = MatchDisplay(state: running, now: 130, clockDisplay: .elapsed)
        #expect(d.stoppage == "0:30")
        #expect(d.stoppageRunning)
        #expect(MatchDisplay(state: makeState([(.startPeriod, 0)]), now: 130, clockDisplay: .elapsed).stoppage == nil)
    }

    /// 큰 시계 아래 작은 줄 (2026-10-15 사용자 결정): 정규시간엔 큰 시계의 반대(경과 ↔ 남은), 추가시간엔 "45+2:13"
    @Test func subClockShowsOtherRegulationClock() {
        let s = makeState([(.startPeriod, 0)])
        #expect(MatchDisplay(state: s, now: 754, clockDisplay: .elapsed).subClock == .remaining("32:26"))
        #expect(MatchDisplay(state: s, now: 754, clockDisplay: .remaining).subClock == .elapsed("12:34"))
    }

    @Test func subClockInAddedTimeUsesFootballNotation() {
        let first = makeState([(.startPeriod, 0)])
        #expect(MatchDisplay(state: first, now: 2833, clockDisplay: .elapsed).subClock == .overtime("45+2:13"))
        #expect(MatchDisplay(state: first, now: 2833, clockDisplay: .remaining).subClock == .overtime("45+2:13"))
        let second = makeState([(.startPeriod, 0), (.endPeriod, 2760), (.startPeriod, 3660)])
        #expect(MatchDisplay(state: second, now: 3660 + 2730, clockDisplay: .elapsed).subClock == .overtime("90+0:30"))
    }

    @Test func subClockOnlyDuringPeriod() {
        #expect(MatchDisplay(state: makeState([]), now: 0, clockDisplay: .elapsed).subClock == nil)
        let interval = makeState([(.startPeriod, 0), (.endPeriod, 2700)])
        #expect(MatchDisplay(state: interval, now: 2760, clockDisplay: .elapsed).subClock == nil)
    }

    @Test func intervalShowsCountdownAndNextPeriod() {
        let s = makeState([(.setKickoffTeam(.home), 0), (.startPeriod, 0), (.endPeriod, 2700)])
        let d = MatchDisplay(state: s, now: 2760, clockDisplay: .elapsed)
        #expect(d.screen == .interval)
        #expect(d.intervalClock == "14:00")
        #expect(d.nextPeriodLabel == "2H")
        #expect(d.kickoffTeam == .away)
        #expect(d.intervalKind == .halfTime)
        #expect(d.intervalTone == .counting)
        // 남은 1분부터 lastMinute
        let last = MatchDisplay(state: s, now: 3540, clockDisplay: .elapsed)
        #expect(last.intervalClock == "01:00")
        #expect(last.intervalTone == .lastMinute)
        // 휴식 길이(15분)를 넘기면 넘긴 시간을 위로 센다
        let over = MatchDisplay(state: s, now: 3600 + 83, clockDisplay: .elapsed)
        #expect(over.intervalClock == "+1:23")
        #expect(over.intervalTone == .overrun)
        #expect(MatchDisplay(state: s, now: 9999, clockDisplay: .elapsed).intervalClock == "+106:39")
    }

    @Test func intervalKindNamesTheBreak() {
        let quarters = RuleSet(periodCount: 4, periodLength: 900, halfTimeLength: 600, quarterBreakLength: 120)
        let q1 = makeState(quarters, [(.startPeriod, 0), (.endPeriod, 900)])
        #expect(MatchDisplay(state: q1, now: 960, clockDisplay: .elapsed).intervalKind == .quarterBreak)
        let q2 = makeState(quarters, [(.startPeriod, 0), (.endPeriod, 900), (.startPeriod, 1020), (.endPeriod, 1920)])
        #expect(MatchDisplay(state: q2, now: 1980, clockDisplay: .elapsed).intervalKind == .halfTime)
        let et = makeState(RuleSet(extraTime: true), [(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300),
                                                      (.startPeriod, 6600), (.endPeriod, 7500)])
        #expect(MatchDisplay(state: et, now: 7520, clockDisplay: .elapsed).intervalKind == .extraTimeBreak)
        #expect(MatchDisplay(state: makeState([]), now: 0, clockDisplay: .elapsed).intervalKind == nil)
    }

    @Test func longMatchesKeepThreeDigitMinutes() {
        #expect(MatchDisplay.format(6000) == "100:00")
        #expect(MatchDisplay.format(65) == "01:05")
        #expect(MatchDisplay.formatShort(65) == "1:05")
    }

    @Test func finishedScreen() {
        let s = makeState([(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300)])
        let d = MatchDisplay(state: s, now: 7000, clockDisplay: .elapsed)
        #expect(d.screen == .finished)
        #expect(d.periodLabel == "2H")
    }

    @Test func periodProgressDuringPeriodOnly() {
        let s = makeState([(.startPeriod, 0)])
        #expect(MatchDisplay(state: s, now: 754, clockDisplay: .elapsed).periodProgress == ClockProgress(elapsed: 754, length: 2700))
        #expect(MatchDisplay(state: s, now: 2833, clockDisplay: .remaining).periodProgress == ClockProgress(elapsed: 2833, length: 2700))
        #expect(MatchDisplay(state: makeState([]), now: 0, clockDisplay: .elapsed).periodProgress == nil)
    }

    @Test func intervalProgressDuringBreakOnly() {
        let s = makeState([(.startPeriod, 0), (.endPeriod, 2760)])
        let d = MatchDisplay(state: s, now: 2760 + 300, clockDisplay: .elapsed)
        #expect(d.intervalProgress == ClockProgress(elapsed: 300, length: 900))
        #expect(d.periodProgress == nil)
        #expect(MatchDisplay(state: makeState([(.startPeriod, 0)]), now: 10, clockDisplay: .elapsed).intervalProgress == nil)
    }

    @Test func shootoutResultsInKickOrder() {
        let rules = RuleSet(penalties: true, penaltyKicksPerTeam: 3)
        let s = makeState(rules, [(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300),
                                  (.setShootoutFirstTeam(.home), 6400),
                                  (.recordEvent(.penaltyKick(team: .home, number: nil, scored: true)), 6410),
                                  (.recordEvent(.penaltyKick(team: .away, number: nil, scored: false)), 6420),
                                  (.recordEvent(.penaltyKick(team: .home, number: nil, scored: false)), 6430)])
        let shootout = MatchDisplay(state: s, now: 6440, clockDisplay: .elapsed).shootout
        #expect(shootout?.results == TeamPair(home: [true, false], away: [false]))
        #expect(shootout?.kicksPerTeam == 3)
    }
}

import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

/// 일시퇴장 IFAB 세션 값: 일시 퇴장 중·출전 금지 번호, 2번째 배지, 알림 진동, 보고서 표시 (Kotlin SinBinIFABSessionTest와 같은 케이스)
struct SinBinIFABSessionTests {
    private static let rules = RuleSet(sinBin: true, sinBinLength: 600)

    private static func setup() -> MatchSetup {
        MatchSetup(homeRoster: (1...18).map { Player(number: $0, name: "P\($0)", starter: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11].contains($0) ? true : nil) })
    }

    /// 홈 7번 두 번째 일시퇴장 (100·800, 1400에 끝남) 뒤 추가 명령
    private static func state(_ extra: [(CommandKind, Double)] = []) -> MatchState {
        makeState(rules, [
            (.startPeriod, 0),
            (.recordEvent(.card(team: .home, number: 7, kind: .sinBin)), 100),
            (.recordEvent(.card(team: .home, number: 7, kind: .sinBin)), 800),
        ] + extra)
    }

    @Test func onPitchExcludesBarred() {
        let s = Self.state()
        #expect(s.onPitchNumbers(team: .home, setup: Self.setup(), now: 1500)?.contains(7) == false)
        #expect(s.onPitchNumbers(team: .home, setup: Self.setup(), now: 1500)?.count == 10)
        // 아직 일시 퇴장 중이면 그대로
        #expect(s.onPitchNumbers(team: .home, setup: Self.setup(), now: 1000)?.contains(7) == true)
    }

    @Test func barredFilledSplit() {
        let s = Self.state([(.recordEvent(.substitution(team: .home, numberOut: 7, numberIn: 12)), 1500)])
        #expect(s.barredNumbers(team: .home, at: 1600, filled: true) == [7])
        #expect(s.barredNumbers(team: .home, at: 1600, filled: false).isEmpty)
        #expect(s.barredNumbers(team: .home, at: 1600, filled: nil) == [7])
        let before = Self.state()
        #expect(before.barredNumbers(team: .home, at: 1500, filled: false) == [7])
        #expect(before.barredNumbers(team: .away, at: 1500, filled: nil).isEmpty)
    }

    @Test func sinBinnedNumbersDuringSinBin() {
        let s = Self.state()
        #expect(s.sinBinnedNumbers(team: .home, at: 900) == [7])
        #expect(s.sinBinnedNumbers(team: .home, at: 1500).isEmpty)
        #expect(s.sinBinnedNumbers(team: .away, at: 900).isEmpty)
    }

    @Test func secondBadgeFlag() {
        let s = Self.state()
        let first = MatchDisplay(state: makeState(Self.rules, [(.startPeriod, 0), (.recordEvent(.card(team: .home, number: 7, kind: .sinBin)), 100)]), now: 200, clockDisplay: .elapsed)
        #expect(first.sinBins.map(\.second) == [false])
        let second = MatchDisplay(state: s, now: 900, clockDisplay: .elapsed)
        #expect(second.sinBins.map(\.second) == [true])
    }

    @Test func secondSinBinEndHaptic() {
        #expect(AlertKind.secondSinBinEnd.hapticPattern == HapticPattern(tone: .retry, repeats: 3, interval: 0.6))
        #expect(AlertKind.sinBinEnd.hapticPattern.repeats == 2)
    }

    @Test func reportLineMarksSecond() {
        let record = makeRecord(MatchSetup(rules: Self.rules), [
            (.startPeriod, 0),
            (.recordEvent(.card(team: .home, number: 7, kind: .sinBin)), 100),
            (.recordEvent(.card(team: .home, number: 7, kind: .sinBin)), 800),
        ])
        let report = MatchReport(bundle: MatchBundle(record: record, workout: nil))
        #expect(report.discipline.map(\.secondSinBin) == [false, true])
    }
}

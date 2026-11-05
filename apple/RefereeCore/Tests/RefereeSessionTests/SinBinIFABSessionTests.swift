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

    @Test func reportLineBarredOnlyWhenSecondSinBinFinishedWithoutSendOff() {
        let sinBin = CommandKind.recordEvent(.card(team: .home, number: 7, kind: .sinBin))
        // 두 번째가 1400에 끝난 뒤 1500에 기간 종료 → 출전 금지
        let served = MatchReport(bundle: MatchBundle(record: makeRecord(MatchSetup(rules: Self.rules),
            [(.startPeriod, 0), (sinBin, 100), (sinBin, 800), (.endPeriod, 1500)]), workout: nil))
        #expect(served.discipline.map(\.secondSinBin) == [false, true])
        #expect(served.discipline.map(\.barred) == [false, true])
        // 두 번째 중(1000) 퇴장 → "2번째"만, 출전 금지 아님
        let sentOff = MatchReport(bundle: MatchBundle(record: makeRecord(MatchSetup(rules: Self.rules),
            [(.startPeriod, 0), (sinBin, 100), (sinBin, 800),
             (.recordEvent(.card(team: .home, number: 7, kind: .red)), 1000), (.endPeriod, 1500)]), workout: nil))
        #expect(sentOff.discipline.filter(\.secondSinBin).map(\.barred) == [false])
        #expect(sentOff.discipline.allSatisfy { !$0.barred })
    }

    /// 워치 끝 안내: 두 번째 일시 퇴장이 끝난 틱에 한 번만, 다음 틱엔 없음. 처음 보는 경기·채운 뒤엔 알리지 않음
    @Test func secondSinBinEndedAppearsOnceThenClears() {
        let id = UUID()
        let s = Self.state()
        var watcher = SecondSinBinEndWatcher()
        #expect(watcher.update(s, matchID: id, at: 1390) == nil)
        #expect(watcher.update(s, matchID: id, at: 1399) == nil)
        let ended = watcher.update(s, matchID: id, at: 1401)
        #expect(ended?.team == .home && ended?.number == 7 && ended?.filled == false)
        #expect(watcher.update(s, matchID: id, at: 1402) == nil)
        // 앱을 다시 실행하면(새 감시) 이미 끝난 것은 알리지 않는다
        var restarted = SecondSinBinEndWatcher()
        #expect(restarted.update(s, matchID: id, at: 1500) == nil)
        #expect(restarted.update(s, matchID: id, at: 1501) == nil)
        // 교체로 채운 출전 금지는 끝나도 알리지 않는다 (끝나기 전에 채운 경우)
        let filled = Self.state([(.recordEvent(.substitution(team: .home, numberOut: 7, numberIn: 12)), 1000)])
        var w2 = SecondSinBinEndWatcher()
        _ = w2.update(filled, matchID: id, at: 1390)
        #expect(w2.update(filled, matchID: id, at: 1401) == nil)
    }
}

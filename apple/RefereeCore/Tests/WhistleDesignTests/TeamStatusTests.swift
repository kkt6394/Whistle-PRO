import Testing
import RefereeCore
@testable import RefereeSession
@testable import WhistleDesign

struct TeamStatusTests {
    func bin(_ team: Team, _ number: Int?, _ seconds: Int) -> SinBinBadge {
        SinBinBadge(team: team, number: number, remaining: "\(seconds / 60):\(String(format: "%02d", seconds % 60))", seconds: seconds)
    }

    @Test func splitsByTeamShortestFirst() {
        let a = bin(.away, 8, 300), b = bin(.home, 7, 90), c = bin(.away, 4, 45)
        let status = TeamStatus.split(shortHanded: [.away], sinBins: [a, b, c])
        #expect(status.home == TeamStatus(shortHanded: false, sinBins: [b]))
        #expect(status.away == TeamStatus(shortHanded: true, sinBins: [c, a]))
    }

    @Test func equalTimesKeepListedOrder() {
        let a = bin(.home, 2, 120), b = bin(.home, 9, 120)
        #expect(TeamStatus.split(shortHanded: [], sinBins: [a, b]).home.sinBins == [a, b])
    }

    @Test func noLinesWhenNothing() {
        #expect(TeamStatus(shortHanded: false, sinBins: []).lines(3) == [])
    }

    @Test func allFitWithoutCount() {
        let s = TeamStatus(shortHanded: false, sinBins: [bin(.home, 7, 60), bin(.home, 9, 200)])
        #expect(s.lines(2) == [.sinBin(s.sinBins[0], total: nil), .sinBin(s.sinBins[1], total: nil)])
    }

    /// 넘치면 마지막 줄에 그 팀 전체 인원 수 (스펙 §3.3 "#4 0:45 ×3")
    @Test func overflowCollapsesLastLine() {
        let s = TeamStatus(shortHanded: false, sinBins: [bin(.away, 4, 45), bin(.away, 8, 300), bin(.away, 9, 500)])
        #expect(s.lines(1) == [.sinBin(s.sinBins[0], total: 3)])
        #expect(s.lines(2) == [.sinBin(s.sinBins[0], total: nil), .sinBin(s.sinBins[1], total: 3)])
    }

    @Test func atLeastOneLine() {
        let s = TeamStatus(shortHanded: false, sinBins: [bin(.home, 7, 60), bin(.home, 9, 200)])
        #expect(s.lines(0) == s.lines(1))
        #expect(s.lines(-2) == s.lines(1))
    }

    @Test func shortHandedLabelFirst() {
        let s = TeamStatus(shortHanded: true, sinBins: [bin(.away, 4, 45), bin(.away, 8, 300)])
        #expect(s.lines(3) == [.shortHanded, .sinBin(s.sinBins[0], total: nil), .sinBin(s.sinBins[1], total: nil)])
        #expect(s.lines(2) == [.shortHanded, .sinBin(s.sinBins[0], total: 2)])
    }

    @Test func shortHandedWithoutSinBinsShowsLabel() {
        #expect(TeamStatus(shortHanded: true, sinBins: []).lines(1) == [.shortHanded])
    }

    /// 한 줄뿐이면 일시 퇴장이 먼저 (2026-10-15 결정: 인원 부족은 빨간 배경·음성 안내로)
    @Test func singleLinePrefersSinBinOverLabel() {
        let s = TeamStatus(shortHanded: true, sinBins: [bin(.away, 4, 45), bin(.away, 8, 300)])
        #expect(s.lines(1) == [.sinBin(s.sinBins[0], total: 2)])
    }

    /// 출전 금지 줄 (Ruling 8): 교체로 채우기 전까지 일시 퇴장 줄 뒤에 "#7 출전 금지", 채우면 사라진다
    @Test func teamStatusShowsUnfilledBarredLine() {
        let rules = RuleSet(sinBin: true, sinBinLength: 600)
        func state(_ extra: [(CommandKind, Double)]) -> MatchState {
            let commands: [(CommandKind, Double)] = [
                (.startPeriod, 0),
                (.recordEvent(.card(team: .home, number: 7, kind: .sinBin)), 100),
                (.recordEvent(.card(team: .home, number: 7, kind: .sinBin)), 800),
            ] + extra
            return MatchEngine.reduce(rules: rules, commands: commands.map { Command(timestamp: $0.1, kind: $0.0) })
        }
        func home(_ s: MatchState, _ now: Double) -> TeamStatus {
            let d = MatchDisplay(state: s, now: now, clockDisplay: .elapsed)
            return TeamStatus.split(shortHanded: d.shortHandedTeams, sinBins: d.sinBins, barred: d.barred).home
        }
        // 두 번째 일시 퇴장 중: 출전 금지 줄 없음
        #expect(home(state([]), 1000).barred.isEmpty)
        // 끝난 뒤 채우기 전: 출전 금지 줄
        let before = home(state([]), 1500)
        #expect(before.barred == [7])
        #expect(before.lines(2) == [.barred(number: 7, total: nil)])
        // 다른 일시 퇴장이 있으면 그 뒤
        let both = home(state([(.recordEvent(.card(team: .home, number: 9, kind: .sinBin)), 1450)]), 1500)
        #expect(both.lines(2).map { if case .barred = $0 { "b" } else { "s" } } == ["s", "b"])
        #expect(both.lines(1).count == 1)
        if case .sinBin = both.lines(1)[0] {} else { Issue.record("한 줄이면 일시 퇴장이 먼저") }
        // 교체로 채우면 사라진다
        let filled = home(state([(.recordEvent(.substitution(team: .home, numberOut: 7, numberIn: 12)), 1500)]), 1600)
        #expect(filled.barred.isEmpty)
        #expect(filled.lines(2).isEmpty)
    }
}

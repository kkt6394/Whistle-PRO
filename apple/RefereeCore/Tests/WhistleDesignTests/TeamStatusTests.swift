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
}

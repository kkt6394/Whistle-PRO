import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct StripLayoutTests {
    let periods = [PeriodSummary(label: "1H", played: 2820, stoppage: 0, added: 120, nominal: 2700),
                   PeriodSummary(label: "2H", played: 2700, stoppage: 0, added: 0, nominal: 2700)]
    func line(_ team: Team, _ kind: ResolvedEventKind, period: Int, second: Int) -> ReportLine {
        var l = ReportLine(minute: .regular(1), team: team, kind: kind, detail: ""); l.period = period; l.second = second; return l
    }

    @Test func marksScaleByPlayed() {
        let marks = StripLayout.marks(periods: periods, lines: [line(.home, .goal, period: 0, second: 1410), line(.away, .goal, period: 1, second: 0)], minGap: 0.05)
        let expected: [Double] = [1410.0 / 5520, 2820.0 / 5520]
        #expect(marks.map(\.x) == expected)
        #expect(marks.map(\.row) == [0, 0])
    }
    @Test func emptyLinesGiveNoMarks() { #expect(StripLayout.marks(periods: periods, lines: [], minGap: 0.05).isEmpty) }
    @Test func zeroPlayedGivesNothing() {
        let p = [PeriodSummary(label: "1H", played: 0, stoppage: 0, added: 0, nominal: 2700)]
        #expect(StripLayout.marks(periods: p, lines: [line(.home, .goal, period: 0, second: 0)], minGap: 0.05).isEmpty)
        #expect(StripLayout.labels(periods: p, completed: false).isEmpty)
    }
    @Test func crowdedMarksStagger() {
        let lines = [line(.home, .yellow, period: 0, second: 2710), line(.home, .yellow, period: 0, second: 2770),
                     line(.home, .yellow, period: 0, second: 2800), line(.away, .yellow, period: 0, second: 2780)]
        #expect(StripLayout.marks(periods: periods, lines: lines, minGap: 0.05).map(\.row) == [0, 1, 0, 0])
    }
    @Test func boundariesAndLabelsCompleted() {
        #expect(StripLayout.boundaries(periods: periods) == [2820.0 / 5520])
        #expect(StripLayout.labels(periods: periods, completed: true) == [
            StripLabel(x: 0, minute: .regular(0)), StripLabel(x: 2820.0 / 5520, minute: .regular(45)), StripLabel(x: 1, minute: .regular(90))])
    }
    @Test func abandonedEndLabel() {
        let p = [periods[0], PeriodSummary(label: "2H", played: 900, stoppage: 0, added: 0, nominal: 2700)]
        #expect(StripLayout.labels(periods: p, completed: false).last == StripLabel(x: 1, minute: .regular(60)))
        let q = [PeriodSummary(label: "1H", played: 2880, stoppage: 0, added: 180, nominal: 2700)]
        #expect(StripLayout.labels(periods: q, completed: false).last == StripLabel(x: 1, minute: .added(base: 45, extra: 3)))
    }
    @Test func abandonedExactlyAtNominalHasNoPlusZero() {
        // 규정 시간에 딱 멈추면 "90+0'"이 아니라 정규 분
        let p = [periods[0], PeriodSummary(label: "2H", played: 2700, stoppage: 0, added: 0, nominal: 2700)]
        #expect(StripLayout.labels(periods: p, completed: false).last == StripLabel(x: 1, minute: .regular((2700 + 2700) / 60)))
    }
    @Test func restGoalSitsAtPeriodEnd() {
        let marks = StripLayout.marks(periods: periods, lines: [line(.home, .goal, period: 0, second: 2820)], minGap: 0.05)
        #expect(marks.first?.x == 2820.0 / 5520)
    }
}

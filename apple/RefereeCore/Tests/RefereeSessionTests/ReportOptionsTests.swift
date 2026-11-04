import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct ReportOptionsTests {
    private func report(_ commands: [(CommandKind, Double)], setup: MatchSetup = MatchSetup(),
                        note: MatchNote? = nil, referee: String? = nil) -> MatchReport {
        MatchReport(bundle: MatchBundle(record: makeRecord(setup, commands), workout: nil), note: note, referee: referee)
    }

    /// 1H 22' 옐로 #4 (c1), 2H 옐로 #4 (c4) → 경고 누적 퇴장
    private let twoYellows: [(CommandKind, Double)] = [
        (.startPeriod, 0),
        (.recordEvent(.card(team: .away, number: 4, kind: .yellow)), 1320),
        (.endPeriod, 2700),
        (.startPeriod, 3600),
        (.recordEvent(.card(team: .away, number: 4, kind: .yellow)), 4150),
        (.endPeriod, 6300),
    ]

    private static let oldSetupJSON: String = {
        let data = try! JSONEncoder().encode(MatchSetup())
        var object = try! JSONSerialization.jsonObject(with: data) as! [String: Any]
        object["reportSections"] = nil
        return String(decoding: try! JSONSerialization.data(withJSONObject: object), as: UTF8.self)
    }()

    @Test func oldJsonDecodesWithDefaults() throws {
        let note = try JSONDecoder().decode(MatchNote.self, from: Data(#"{"competition":"리그"}"#.utf8))
        #expect(note.cardReasons.isEmpty && note.reportSections == nil)
        let setup = try JSONDecoder().decode(MatchSetup.self, from: Data(Self.oldSetupJSON.utf8))
        #expect(setup.reportSections == nil)
        #expect(try JSONDecoder().decode(ReportSections.self, from: Data(#"{"signatures":false}"#.utf8))
                == ReportSections(hidden: [.timeline, .signatures]))
    }

    @Test func emptyFieldsOmitted() throws {
        let json = String(decoding: try JSONEncoder().encode(MatchNote(competition: "리그")), as: UTF8.self)
        #expect(!json.contains("cardReasons") && !json.contains("reportSections"))
        #expect(!String(decoding: try JSONEncoder().encode(MatchSetup()), as: UTF8.self).contains("reportSections"))
    }

    @Test func noteRoundTrips() throws {
        var note = MatchNote(competition: "A")
        note.cardReasons = ["c1": .cautionDissent]
        note.reportSections = ReportSections(hidden: [.signatures])
        let back = try JSONDecoder().decode(MatchNote.self, from: JSONEncoder().encode(note))
        #expect(back == note)
        #expect(!note.isEmpty)
        #expect(!MatchNote(cardReasons: ["c1": .sendOffViolent]).isEmpty)
    }

    @Test func editedKeepsReasonsAndSections() {
        var old = MatchNote(competition: "A")
        old.cardReasons = ["e1": .cautionDissent]
        old.reportSections = ReportSections(hidden: [.signatures])
        let recorded = TeamColors(setup: MatchSetup())
        let new = MatchNote.edited(competition: "B", venue: nil, colors: recorded, recorded: recorded, keeping: old)
        #expect(new.competition == "B" && new.cardReasons == old.cardReasons && new.reportSections == old.reportSections)
        #expect(MatchNote.edited(competition: "B", venue: nil, colors: recorded, recorded: recorded).cardReasons.isEmpty)
    }

    @Test func sectionsPrecedence() {
        let fromSetup = ReportSections(hidden: [.totals])
        let fromNote = ReportSections(hidden: [])
        let commands: [(CommandKind, Double)] = [(.startPeriod, 0)]
        let none = report(commands)
        #expect(none.sections == .standard && !none.sectionsFromMatch)
        let setupOnly = report(commands, setup: MatchSetup(reportSections: fromSetup))
        #expect(setupOnly.sections == fromSetup && !setupOnly.sectionsFromMatch)
        let both = report(commands, setup: MatchSetup(reportSections: fromSetup), note: MatchNote(reportSections: fromNote))
        #expect(both.sections == fromNote && both.sectionsFromMatch)
    }

    @Test func secondYellowReasons() {
        let note = MatchNote(cardReasons: ["c1": .cautionDissent, "c4": .cautionUnsporting])
        let r = report(twoYellows, note: note)
        #expect(r.cautions.map(\.eventID) == ["c1", "c4"])
        #expect(r.sendOffs.map(\.eventID) == ["c4"])
        let second = r.sendOffs[0]
        #expect(r.cautionReason(second) == .cautionUnsporting)
        #expect(r.sendOffReason(second) == .sendOffSecondCaution)
        #expect(r.missingReasons.isEmpty)

        let partial = report(twoYellows, note: MatchNote(cardReasons: ["c1": .cautionDissent]))
        #expect(partial.missingReasons.map(\.eventID) == ["c4"])
    }

    @Test func sendOffReasonFromNoteForRed() {
        let commands: [(CommandKind, Double)] = [
            (.startPeriod, 0), (.recordEvent(.card(team: .home, number: 3, kind: .red)), 600),
        ]
        let r = report(commands, note: MatchNote(cardReasons: ["c1": .sendOffViolent]))
        #expect(r.sendOffs.count == 1 && r.cautions.isEmpty)
        #expect(r.sendOffReason(r.sendOffs[0]) == .sendOffViolent)
        #expect(report(commands).missingReasons.count == 1)
    }

    @Test func sinBinTableShown() {
        let commands: [(CommandKind, Double)] = [(.startPeriod, 0)]
        #expect(report(commands, setup: MatchSetup(rules: RuleSet(sinBin: true))).showsSinBinTable)
        #expect(!report(commands).showsSinBinTable)
        let withBin = report([(.startPeriod, 0), (.recordEvent(.card(team: .home, number: 2, kind: .sinBin)), 300)],
                             setup: MatchSetup(rules: RuleSet(sinBin: true)))
        #expect(withBin.showsSinBinTable && withBin.sinBins.count == 1)
        #expect(withBin.sinBinReason(withBin.sinBins[0]) == nil)
        #expect(withBin.missingReasons.count == 1)
    }

    @Test func resultForAbandonedHasNoWinner() {
        let r = report([(.startPeriod, 0), (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 300),
                        (.abandonMatch(reason: "폭우"), 1200)])
        #expect(r.result == .unfinished(.abandoned("폭우")))
    }

    @Test func shootoutWinner() {
        let setup = MatchSetup(rules: RuleSet(penalties: true))
        var commands: [(CommandKind, Double)] = [
            (.startPeriod, 0),
            (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 300),
            (.recordEvent(.goal(team: .away, number: 9, ownGoal: false)), 600),
            (.endPeriod, 2700), (.startPeriod, 3600),
            (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 3900),
            (.recordEvent(.goal(team: .away, number: 9, ownGoal: false)), 4200),
            (.endPeriod, 6300), (.setShootoutFirstTeam(.home), 6400),
        ]
        var t = 6410.0
        for (team, scored) in [(Team.home, true), (.away, true), (.home, true), (.away, true),
                               (.home, true), (.away, true), (.home, true), (.away, false),
                               (.home, false), (.away, false)] {
            commands.append((.recordEvent(.penaltyKick(team: team, number: nil, scored: scored)), t))
            t += 10
        }
        let r = report(commands, setup: setup)
        #expect(r.score == TeamPair(home: 2, away: 2))
        #expect(r.result == .win(.home, shootout: TeamPair(home: 4, away: 3)))
        #expect(r.timelineRows.last == .shootout(TeamPair(home: 4, away: 3)))

        let drawn = report([(.startPeriod, 0), (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 300),
                            (.recordEvent(.goal(team: .away, number: 9, ownGoal: false)), 600),
                            (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300)])
        #expect(drawn.result == .draw)
    }

    @Test func emptyMatchHasOnlyPeriodEnds() {
        let r = report([(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300)])
        #expect(r.timelineRows == [.periodEnd(label: "1H", score: TeamPair(home: 0, away: 0)),
                                   .periodEnd(label: "2H", score: TeamPair(home: 0, away: 0))])
        #expect(r.cautions.isEmpty && r.sendOffs.isEmpty && r.sinBins.isEmpty && r.goals.isEmpty)
    }

    @Test func timelineRowsOrder() {
        let r = report([
            (.startPeriod, 0),
            (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 650),
            (.recordEvent(.card(team: .away, number: 4, kind: .yellow)), 1320),
            (.recordEvent(.goal(team: .away, number: 11, ownGoal: false)), 2150),
            (.endPeriod, 2700), (.startPeriod, 3600),
            (.recordEvent(.substitution(team: .home, numberOut: 7, numberIn: 14)), 3900),
            (.endPeriod, 6300),
        ])
        let lines = r.timeline
        #expect(lines.count == 4)
        #expect(r.timelineRows == [
            .event(lines[0]), .event(lines[1]), .event(lines[2]),
            .periodEnd(label: "1H", score: TeamPair(home: 1, away: 1)),
            .event(lines[3]),
            .periodEnd(label: "2H", score: TeamPair(home: 1, away: 1)),
        ])
    }

    @Test func unfinishedLastPeriodHasNoEnd() {
        let r = report([(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600)])
        #expect(r.timelineRows == [.periodEnd(label: "1H", score: TeamPair(home: 0, away: 0))])
        #expect(r.result == .unfinished(.inProgress))
    }

    @Test func missingInfoOrder() {
        let commands: [(CommandKind, Double)] = [(.startPeriod, 0)]
        let r = report(commands, note: MatchNote(competition: "리그"))
        #expect(r.missingInfo == [.referee, .venue])
        let full = report(commands, note: MatchNote(competition: "리그", venue: "구장"), referee: "홍길동")
        #expect(full.missingInfo.isEmpty)
        #expect(report(commands).missingInfo == [.referee, .competition, .venue])
    }

    @Test func eventIDCarried() {
        let r = report(twoYellows)
        #expect(r.cautions.first?.eventID == "c1")
    }
}

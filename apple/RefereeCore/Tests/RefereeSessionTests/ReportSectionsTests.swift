import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

/// 보고서 구성(블록 순서·켬/끔)과 카드 사유 저장 값 (Kotlin ReportSectionsTests와 같은 이름·같은 기대값)
struct ReportSectionsTests {
    private func decode(_ json: String) throws -> ReportSections {
        try JSONDecoder().decode(ReportSections.self, from: Data(json.utf8))
    }

    @Test func standardOrderAndTimelineOff() {
        let s = ReportSections.standard
        #expect(s.order == [.info, .score, .goals, .substitutions, .cautions, .sendOffs, .otherReport,
                            .periodTimes, .format, .totals, .timeline, .signatures])
        #expect(s.hidden == [.timeline] && s.teamSignatures)
        #expect(!s.isOn(.timeline) && s.isOn(.info))
        #expect(s.visibleBlocks == s.order.filter { $0 != .timeline })
    }

    @Test func oldShapeDecodes() throws {
        // 2026-11-03 모양: 일곱 불 값을 모두 적었다. timeline은 저장된 그대로
        let old = try decode(#"{"substitutions":false,"totals":true,"periodTimes":true,"format":false,"timeline":true,"signatures":true,"teamSignatures":false}"#)
        #expect(old.order == ReportBlock.defaultOrder)
        #expect(old.hidden == [.substitutions, .format])
        #expect(old.isOn(.timeline) && !old.teamSignatures)
        #expect(old.isOn(.info) && old.isOn(.score) && old.isOn(.goals) && old.isOn(.cautions)
                && old.isOn(.sendOffs) && old.isOn(.otherReport))
        // 옛 모양에 timeline 키가 없으면 새 기본값(끔)
        #expect(try decode("{}") == .standard)
        #expect(try decode(#"{"timeline":false}"#) == .standard)
    }

    @Test func roundTripsAndWritesLegacyKeys() throws {
        var s = ReportSections.standard
        s.move(.otherReport, by: -6)
        s.set(.totals, on: false)
        s.set(.timeline, on: true)
        s.teamSignatures = false
        let data = try JSONEncoder().encode(s)
        #expect(try JSONDecoder().decode(ReportSections.self, from: data) == s)
        let object = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        #expect(object["hidden"] as? [String] == ["totals"])
        #expect((object["order"] as? [String])?.first == "otherReport")
        // 옛 앱이 읽는 불 값
        #expect(object["totals"] as? Bool == false && object["timeline"] as? Bool == true)
    }

    @Test func unknownKeysDroppedAndMissingBlocksInserted() throws {
        let s = try decode(#"{"order":["signatures","zzz","score","goals","score"],"hidden":["goals","yyy"]}"#)
        // 빠진 블록은 기본 순서에서 바로 앞 블록 뒤에, 앞 블록이 없으면 맨 앞에
        #expect(s.order == [.info, .signatures, .score, .goals, .substitutions, .cautions, .sendOffs, .otherReport,
                            .periodTimes, .format, .totals, .timeline])
        // 목록에 없던 timeline은 기본값(끔), 저장된 hidden은 그대로
        #expect(s.hidden == [.goals, .timeline])
        #expect(s.visibleBlocks.first == .info && s.visibleBlocks[1] == .signatures)
        // 목록에 있던 timeline은 저장된 켬을 지킨다
        let on = try decode(#"{"order":["timeline","info"],"hidden":[]}"#)
        #expect(on.isOn(.timeline) && on.order.first == .timeline)
    }

    @Test func moveAndToggle() {
        var s = ReportSections.standard
        s.move(fromOffsets: IndexSet(integer: 0), toOffset: 3)
        #expect(Array(s.order.prefix(3)) == [.score, .goals, .info])
        s.move(fromOffsets: IndexSet(integer: 11), toOffset: 0)
        #expect(s.order.first == .signatures)
        s.move(.signatures, by: -1)
        #expect(s.order.first == .signatures)
        s.move(.signatures, by: 1)
        #expect(Array(s.order.prefix(2)) == [.score, .signatures])
        s.move(.timeline, by: 1)
        #expect(s.order.last == .timeline)
        #expect(s.order.count == ReportBlock.allCases.count)
        let off = s.setting(.score, on: false)
        #expect(!off.isOn(.score) && !off.visibleBlocks.contains(.score) && s.isOn(.score))
        var none = ReportSections.standard
        for block in ReportBlock.allCases { none.set(block, on: false) }
        #expect(none.visibleBlocks.isEmpty)
    }

    @Test func cardReasonLists() throws {
        // IFAB 원문 순서. 퇴장 사유를 더하면 여기 목록만 늘리면 된다
        #expect(CardReason.cautions.map(\.rawValue) == [
            "caution.delay", "caution.dissent", "caution.entering", "caution.distance",
            "caution.persistent", "caution.unsporting", "caution.reviewArea", "caution.reviewSignal",
        ])
        #expect(CardReason.sendOffs.map(\.rawValue) == [
            "sendOff.handball", "sendOff.handballNonDeliberate", "sendOff.dogso", "sendOff.seriousFoul",
            "sendOff.spitting", "sendOff.violent", "sendOff.offensive", "sendOff.secondCaution", "sendOff.vor",
        ])
        // 모든 사유가 두 목록 중 정확히 하나에 들어 있다 (사유를 더할 때 목록을 빠뜨리지 않게)
        #expect(Set(CardReason.cautions + CardReason.sendOffs) == Set(CardReason.allCases))
        #expect(CardReason.cautions.allSatisfy { $0.rawValue.hasPrefix("caution.") })
        #expect(CardReason.sendOffs.allSatisfy { $0.rawValue.hasPrefix("sendOff.") })
        for reason in CardReason.allCases {
            let data = try JSONEncoder().encode([reason])
            #expect(try JSONDecoder().decode([CardReason].self, from: data) == [reason])
        }
    }

    @Test func oldLeavingReasonReadsAsEntering() throws {
        #expect(CardReason(storedValue: "caution.leaving") == .cautionEntering)
        #expect(try JSONDecoder().decode([CardReason].self, from: Data(#"["caution.leaving"]"#.utf8)) == [.cautionEntering])
        let note = try JSONDecoder().decode(MatchNote.self, from: Data(
            #"{"cardReasons":{"c1":"caution.leaving","c2":"sendOff.vor","c3":"caution.zzz"}}"#.utf8))
        #expect(note.cardReasons == ["c1": .cautionEntering, "c2": .sendOffVor])
    }

    @Test func noteNewFieldsRoundTripAndBlankIsNil() throws {
        let note = MatchNote(otherReport: "  킥오프 지연: 10분\n관중 소란  ", matchNumber: " 12 ", assistant1: "박부심",
                             assistant2: "   ", fourthOfficial: "최대기")
        #expect(note.otherReport == "킥오프 지연: 10분\n관중 소란" && note.matchNumber == "12" && note.assistant2 == nil)
        #expect(!note.isEmpty && !MatchNote(otherReport: "x").isEmpty && MatchNote(matchNumber: " ").isEmpty)
        let data = try JSONEncoder().encode(note)
        #expect(try JSONDecoder().decode(MatchNote.self, from: data) == note)
        #expect(!String(decoding: data, as: UTF8.self).contains("assistant2"))
        #expect(!String(decoding: try JSONEncoder().encode(MatchNote(competition: "A")), as: UTF8.self).contains("otherReport"))
        var edited = note
        edited.otherReport = "  "
        #expect(edited.otherReport == nil)
        #expect(try JSONDecoder().decode(MatchNote.self, from: Data(#"{"matchNumber":"  "}"#.utf8)).matchNumber == nil)
    }

    @Test func editedKeepsOtherReportAndOfficials() {
        let old = MatchNote(otherReport: "부상", matchNumber: "7", assistant1: "A1", assistant2: "A2", fourthOfficial: "F")
        let recorded = TeamColors(setup: MatchSetup())
        let kept = MatchNote.edited(competition: "B", venue: nil, colors: recorded, recorded: recorded, keeping: old)
        #expect(kept.otherReport == "부상" && kept.matchNumber == "7" && kept.assistant2 == "A2" && kept.fourthOfficial == "F")
        let changed = MatchNote.edited(competition: "B", venue: nil, colors: recorded, recorded: recorded,
                                       matchNumber: "8", assistant1: nil, assistant2: "새", fourthOfficial: " ", keeping: old)
        #expect(changed.otherReport == "부상" && changed.matchNumber == "8" && changed.assistant1 == nil
                && changed.assistant2 == "새" && changed.fourthOfficial == nil)
    }

    @Test func reportExposesNoteFields() {
        let record = makeRecord(MatchSetup(), [(.startPeriod, 0)])
        let note = MatchNote(otherReport: "경기장 상태: 젖음", matchNumber: "A-3", assistant2: "이부심", fourthOfficial: "최대기")
        let r = MatchReport(bundle: MatchBundle(record: record, workout: nil), note: note)
        #expect(r.otherReport == "경기장 상태: 젖음" && r.matchNumber == "A-3")
        #expect(r.assistants == ["이부심"] && r.fourthOfficial == "최대기")
        let plain = MatchReport(bundle: MatchBundle(record: record, workout: nil))
        #expect(plain.otherReport == nil && plain.assistants.isEmpty && plain.matchNumber == nil)
    }
}

import Testing
@testable import RefereeSession

@Suite struct DemoNamesTests {
    @Test func rostersCoverDemoEventNumbers() {
        for names in [DemoNames.ko, DemoNames.en] {
            let home = Dictionary(uniqueKeysWithValues: names.homeRoster.map { ($0.number, $0) })
            let away = Dictionary(uniqueKeysWithValues: names.awayRoster.map { ($0.number, $0) })
            // 데모 이벤트가 쓰는 번호: 홈 선발 1·4·5·7·9·10, 후보 14·17 / 원정 선발 4·11
            #expect([1, 4, 5, 7, 9, 10].allSatisfy { home[$0]?.starter == true })
            #expect([14, 17].allSatisfy { home[$0] != nil && home[$0]?.starter == nil })
            #expect([4, 11].allSatisfy { away[$0]?.starter == true })
            // 실제 경기처럼 선발 11명씩
            #expect(names.homeRoster.filter { $0.starter == true }.count == 11)
            #expect(names.awayRoster.filter { $0.starter == true }.count == 11)
            #expect(Roster.validate(names.homeRoster).isEmpty && Roster.validate(names.awayRoster).isEmpty)
        }
    }
    @Test func fromArguments() {
        #expect(DemoNames.fromArguments(["-demo", "storeMain", "-demoNames", "en"])?.homeName == "Riverside FC")
        #expect(DemoNames.fromArguments(["-demoNames", "ko"])?.homeName == "FC 서울숲")
        #expect(DemoNames.fromArguments(["-demo", "detail"]) == nil)
        #expect(DemoNames.fromArguments(["-demoNames"]) == nil)
    }
    @Test func enHasNoHangul() {
        let all = [DemoNames.en.homeName, DemoNames.en.awayName, DemoNames.en.presetName, DemoNames.en.competition, DemoNames.en.venue, DemoNames.en.referee]
            + (DemoNames.en.homeRoster + DemoNames.en.awayRoster).map(\.name)
        #expect(all.allSatisfy { $0.unicodeScalars.allSatisfy { !(0xAC00...0xD7A3).contains($0.value) } })
    }
}

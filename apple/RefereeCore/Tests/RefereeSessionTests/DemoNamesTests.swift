import Testing
@testable import RefereeSession

@Suite struct DemoNamesTests {
    @Test func rostersCoverDemoEventNumbers() {
        for names in [DemoNames.ko, DemoNames.en] {
            #expect(names.homeRoster.map(\.number) == [1, 4, 5, 7, 9, 10, 14, 17])
            #expect(names.awayRoster.map(\.number) == [4, 11])
            #expect(names.homeRoster.prefix(6).allSatisfy { $0.starter == true })
            #expect(names.homeRoster.suffix(2).allSatisfy { $0.starter == nil })
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

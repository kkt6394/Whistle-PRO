import Testing
@testable import RefereeSession

/// 명단 묶음 (Kotlin RosterSectionsTests)
struct RosterSectionsTests {
    private let players = [Player(number: 10, name: "c"), Player(number: 1, name: "a"), Player(number: 5, name: "b"),
                           Player(number: 14, name: "d"), Player(number: 7, name: "e")]

    private func numbers(_ sections: [RosterSection]) -> [String] {
        sections.map { "\($0.id):\($0.players.map(\.number))" }
    }

    @Test func outgoingPutsOnPitchFirstAndSentOffLast() {
        let sections = RosterSections.of(players: players, sentOff: [5], onPitch: [1, 10], grouping: .outgoing)
        #expect(numbers(sections) == ["roster.group.onPitch:[1, 10]", "roster.group.bench:[7, 14]", "roster.group.sentOff:[5]"])
    }

    @Test func incomingPutsBenchFirstAndDropsEmptyGroups() {
        let sections = RosterSections.of(players: players, sentOff: [], onPitch: [1, 10], grouping: .incoming)
        #expect(numbers(sections) == ["roster.group.bench:[5, 7, 14]", "roster.group.onPitch:[1, 10]"])
    }

    @Test func noStartersIsOneSortedList() {
        let sections = RosterSections.of(players: players, sentOff: [5], onPitch: nil, grouping: .any)
        #expect(numbers(sections) == ["roster.all:[1, 5, 7, 10, 14]"])
    }

    // 일시퇴장 IFAB: 나가는 번호에서는 아직 못 채운 출전 금지 선수가 맨 위 (채운 선수는 벤치)
    @Test func outgoingPutsUnfilledBarredFirst() {
        let sections = RosterSections.of(players: players, sentOff: [5], onPitch: [1, 10], grouping: .outgoing,
                                         barredUnfilled: [7], barredAll: [7, 14])
        #expect(numbers(sections) == ["roster.group.barred:[7]", "roster.group.onPitch:[1, 10]",
                                      "roster.group.bench:[14]", "roster.group.sentOff:[5]"])
    }

    // 다른 단계에서는 출전 금지 전부가 벤치 다음, 퇴장 앞
    @Test func otherStepsPutAllBarredAfterBenchBeforeSentOff() {
        let any = RosterSections.of(players: players, sentOff: [5], onPitch: [1, 10], grouping: .any,
                                    barredUnfilled: [7], barredAll: [7, 14])
        #expect(numbers(any) == ["roster.group.onPitch:[1, 10]", "roster.group.barred:[7, 14]", "roster.group.sentOff:[5]"])
        let incoming = RosterSections.of(players: players, sentOff: [], onPitch: [1], grouping: .incoming,
                                         barredUnfilled: [7], barredAll: [7])
        #expect(numbers(incoming) == ["roster.group.bench:[5, 10, 14]", "roster.group.onPitch:[1]", "roster.group.barred:[7]"])
    }

    // 출전 금지 뒤 퇴장(레드)까지 받으면 퇴장 묶음에만
    @Test func sentOffWinsOverBarred() {
        let sections = RosterSections.of(players: players, sentOff: [7], onPitch: [1, 10], grouping: .outgoing,
                                         barredUnfilled: [7], barredAll: [7])
        #expect(numbers(sections) == ["roster.group.onPitch:[1, 10]", "roster.group.bench:[5, 14]", "roster.group.sentOff:[7]"])
    }
}

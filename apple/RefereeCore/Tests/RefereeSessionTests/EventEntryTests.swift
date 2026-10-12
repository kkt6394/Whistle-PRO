import Testing
import RefereeCore
@testable import RefereeSession

struct EventEntryTests {
    @Test func sinBinOnlyWhenRuleEnabled() {
        #expect(EventKindChoice.available(for: .fifaStandard) == [.goal, .ownGoal, .yellow, .red, .substitution])
        #expect(EventKindChoice.available(for: RuleSet(sinBin: true)).contains(.sinBin))
    }

    @Test func choicesMapToPayloads() {
        #expect(EventKindChoice.ownGoal.payload(team: .away, number: 3, secondNumber: nil)
                == .goal(team: .away, number: 3, ownGoal: true))
        #expect(EventKindChoice.substitution.payload(team: .home, number: 7, secondNumber: 14)
                == .substitution(team: .home, numberOut: 7, numberIn: 14))
        #expect(EventKindChoice.sinBin.payload(team: .home, number: nil, secondNumber: nil)
                == .card(team: .home, number: nil, kind: .sinBin))
        #expect(EventKindChoice.substitution.needsSecondNumber)
        #expect(!EventKindChoice.goal.needsSecondNumber)
    }

    @Test func numberEntryKeepsTwoDigits() {
        var e = NumberEntry()
        #expect(e.value == nil)
        e.append(1)
        e.append(2)
        e.append(3)
        #expect(e.value == 12)
        e.deleteLast()
        #expect(e.value == 1)

        var zero = NumberEntry()
        zero.append(0)
        #expect(zero.value == 0)
        zero.append(7)
        #expect(zero.digits == "7")
        #expect(NumberEntry(value: 9).digits == "9")
    }

    @Test func numberSlotsReadAndReplace() {
        let sub = EventPayload.substitution(team: .home, numberOut: 7, numberIn: 14)
        #expect(sub.number(in: .primary) == 7)
        #expect(sub.number(in: .incoming) == 14)
        #expect(sub.withNumber(15, in: .incoming) == .substitution(team: .home, numberOut: 7, numberIn: 15))

        let card = EventPayload.card(team: .away, number: 4, kind: .yellow)
        #expect(card.withNumber(5, in: .primary) == .card(team: .away, number: 5, kind: .yellow))
        #expect(card.withNumber(nil, in: .primary) == .card(team: .away, number: nil, kind: .yellow))
        #expect(card.number(in: .incoming) == nil)
    }
}

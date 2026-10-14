import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct EventLocationTests {
    private func point(_ t: Double, lat: Double = 37, accuracy: Double = 5) -> RoutePoint {
        RoutePoint(t: t, latitude: lat, longitude: 127, speed: 1, accuracy: accuracy)
    }

    @Test func locatesNearestPointWithinFiveSeconds() {
        let route = [point(95, lat: 1), point(98, lat: 2), point(103, lat: 3), point(110, lat: 4)]
        #expect(EventLocation.locate(route, at: 100)?.latitude == 2)
        #expect(EventLocation.locate(route, at: 105)?.latitude == 3)
    }

    @Test func noLocationWithoutNearbyPoint() {
        #expect(EventLocation.locate([point(90), point(106)], at: 100) == nil)
        #expect(EventLocation.locate([], at: 100) == nil)
    }

    @Test func ignoresInaccuratePoints() {
        let route = [point(100, lat: 1, accuracy: 25), point(100, lat: 2, accuracy: -1), point(104, lat: 3)]
        #expect(EventLocation.locate(route, at: 100)?.latitude == 3)
    }

    @Test func eventPinsSkipShootout() {
        let state = makeState(RuleSet(penalties: true), [
            (.startPeriod, 0),
            (.recordEvent(.card(team: .away, number: 4, kind: .yellow)), 100),
            (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300),
            (.setShootoutFirstTeam(.home), 6400),
            (.recordEvent(.penaltyKick(team: .home, number: 9, scored: true)), 6410),
        ])
        let route = [point(100, lat: 1), point(6410, lat: 2)]
        let pins = EventLocation.pins(state: state, setup: MatchSetup(), route: route)
        #expect(pins.map(\.kind) == [.yellow])
        #expect(pins.first?.latitude == 1)
        #expect(pins.first?.detail == "#4")
        #expect(pins.first?.team == .away)
    }

    @Test func editedEventKeepsFirstTimeAndDeletedHasNoPin() {
        var commands: [(CommandKind, Double)] = [
            (.startPeriod, 0),
            (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 100),
            (.recordEvent(.card(team: .away, number: 4, kind: .yellow)), 200),
        ]
        commands.append((.editEvent(id: "c1", .goal(team: .home, number: 10, ownGoal: false)), 300))
        commands.append((.deleteEvent(id: "c2"), 400))
        let edited = makeRecord(commands)
        let state = MatchEngine.reduce(rules: .fifaStandard, commands: edited.commands)
        let setup = MatchSetup(homeRoster: [Player(number: 10, name: "Son")])
        let pins = EventLocation.pins(state: state, setup: setup, route: [point(100, lat: 1), point(200, lat: 2), point(300, lat: 3)])
        #expect(pins.map(\.latitude) == [1])
        #expect(pins.map(\.detail) == ["#10 Son"])
    }

    @Test func noPinsWithoutRoute() {
        let state = makeState([(.startPeriod, 0), (.recordEvent(.goal(team: .home, number: nil, ownGoal: false)), 10)])
        #expect(EventLocation.pins(state: state, setup: MatchSetup(), route: []).isEmpty)
    }
}

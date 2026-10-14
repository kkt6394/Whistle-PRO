import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

private func summary(startedAt: Double, outcome: ReportOutcome = .completed, goals: Int = 0, yellows: Int = 0,
                     reds: Int = 0, added: Int = 0, distance: Double? = nil, heartRate: Int? = nil,
                     sprints: Int? = nil, zones: SpeedZones? = nil) -> MatchSummary {
    var s = MatchSummary(record: makeRecord([], createdAt: startedAt))
    s.startedAt = startedAt
    s.outcome = outcome
    s.goals = goals
    s.yellows = yellows
    s.reds = reds
    s.sinBins = 0
    s.addedSeconds = added
    s.distance = distance
    s.averageHeartRate = heartRate
    s.sprints = sprints
    s.zones = zones
    return s
}

struct SeasonStatsTests {
    @Test func emptyHasNoAverages() {
        let stats = SeasonStats.aggregate([])
        #expect(stats.all.matches == 0)
        #expect(stats.cardsPerMatch == nil)
        #expect(stats.distancePerMatch == nil)
        #expect(stats.averageHeartRate == nil)
    }

    @Test func abandonedCountsButIsNotAveraged() {
        let stats = SeasonStats.aggregate([
            summary(startedAt: 1, goals: 3, yellows: 2, added: 240, distance: 10000, heartRate: 150, sprints: 20),
            summary(startedAt: 2, goals: 1, yellows: 1, reds: 1, added: 120, distance: 8000, heartRate: 160, sprints: 10),
            summary(startedAt: 3, outcome: .abandoned("weather"), goals: 5, yellows: 4, distance: 1000, heartRate: 100, sprints: 1),
        ])
        #expect(stats.all.matches == 3)
        #expect(stats.completed.matches == 2)
        #expect(stats.abandoned == 1)
        #expect(stats.all.goals == 9)
        #expect(stats.all.cards == 8)
        #expect(stats.goalsPerMatch == 2)
        #expect(stats.cardsPerMatch == 2)
        #expect(stats.addedSecondsPerMatch == 180)
        #expect(stats.distancePerMatch == 9000)
        #expect(stats.sprintsPerMatch == 15)
        #expect(stats.averageHeartRate == 155)
    }

    @Test func matchesWithoutWorkoutDoNotLowerWorkoutAverages() {
        let stats = SeasonStats.aggregate([
            summary(startedAt: 1, yellows: 2, distance: 10000, heartRate: 150, sprints: 20),
            summary(startedAt: 2, yellows: 4),
        ])
        #expect(stats.cardsPerMatch == 3)
        #expect(stats.distancePerMatch == 10000)
        #expect(stats.sprintsPerMatch == 20)
        #expect(stats.averageHeartRate == 150)
    }

    @Test func rangeIncludesFromAndExcludesTo() {
        let list = [summary(startedAt: 99), summary(startedAt: 100), summary(startedAt: 199), summary(startedAt: 200)]
        #expect(SeasonStats.aggregate(list, from: 100, to: 200).all.matches == 2)
        #expect(SeasonStats.aggregate(list, from: 100).all.matches == 3)
        #expect(SeasonStats.aggregate(list, to: 200).all.matches == 3)
    }

    @Test func oldSummariesWithoutValuesCountAsZero() {
        var old = MatchSummary(record: makeRecord([], createdAt: 5))
        old.summaryVersion = nil
        old.goals = nil
        old.yellows = nil
        let stats = SeasonStats.aggregate([old])
        #expect(stats.all.matches == 1)
        #expect(stats.all.goals == 0)
    }

    @Test func zonesAreSummed() {
        let stats = SeasonStats.aggregate([
            summary(startedAt: 1, distance: 10, zones: SpeedZones(walking: 1, sprinting: 2)),
            summary(startedAt: 2, distance: 10, zones: SpeedZones(walking: 3, jogging: 1)),
            summary(startedAt: 3),
        ])
        #expect(stats.all.zones == SpeedZones(walking: 4, jogging: 1, sprinting: 2))
    }
}

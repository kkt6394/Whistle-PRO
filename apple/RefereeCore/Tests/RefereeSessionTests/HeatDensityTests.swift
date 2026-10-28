import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

private let metersPerDegree = 111_320.0

private func point(north: Double, east: Double) -> RoutePoint {
    RoutePoint(t: 0, latitude: 37.5 + north / metersPerDegree,
               longitude: 127.0 + east / (metersPerDegree * cos(37.5 * .pi / 180)), speed: 3, accuracy: 5)
}

struct HeatDensityTests {
    @Test func emptyCellsHaveNoDensity() {
        #expect(Heatmap.density(cells: []) == nil)
    }

    @Test func peakIsOneAtTheBusiestPlaceAndFadesOutward() throws {
        let route = Array(repeating: point(north: 0, east: 0), count: 9) + [point(north: 0, east: 40)]
        let d = try #require(Heatmap.density(cells: Heatmap.cells(route)))
        #expect(d.values.count == d.width * d.height)
        #expect(d.values.max() == 1)
        #expect(d.values.min()! >= 0)
        // 가장 진한 곳은 9번 머문 서쪽 칸
        let peakIndex = d.values.firstIndex(of: 1)!
        #expect(peakIndex % d.width < d.width / 2)
        // 테두리(반경의 3배 여백)는 거의 0
        #expect(d.value(x: 0, y: 0) < 0.02)
        #expect(d.value(x: d.width - 1, y: d.height - 1) < 0.02)
    }

    @Test func northIsTheFirstRow() throws {
        let route = [point(north: 30, east: 0), point(north: 30, east: 0), point(north: 30, east: 0), point(north: 0, east: 0)]
        let d = try #require(Heatmap.density(cells: Heatmap.cells(route)))
        let peakRow = d.values.firstIndex(of: 1)! / d.width
        #expect(peakRow < d.height / 2)
        #expect(d.maxLatitude > 37.5 + 30 / metersPerDegree)
    }

    @Test func largeAreaStaysWithinMaximumSide() throws {
        let route = [point(north: 0, east: 0), point(north: 900, east: 1200)]
        let d = try #require(Heatmap.density(cells: Heatmap.cells(route), maximumSide: 256))
        #expect(d.width <= 256 && d.height <= 256)
        #expect(d.width > d.height)
    }
}

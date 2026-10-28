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

    // 경기장 좌표 히트 격자
    private static let pitch = Pitch(center: GeoPoint(latitude: 37.5, longitude: 127.0), length: 100, width: 64, bearing: 0)

    /// 경기장 중심에서 (앞 m, 오른쪽 m)만큼 떨어진 칸 하나. 방위 0이라 앞=북, 오른쪽=동
    private func pitchCell(forward: Double, right: Double) -> [HeatCell] {
        let latitude = Self.pitch.center.latitude + forward / metersPerDegree
        let longitude = Self.pitch.center.longitude + right / (metersPerDegree * cos(Self.pitch.center.latitude * .pi / 180))
        return [HeatCell(minLatitude: latitude, minLongitude: longitude, maxLatitude: latitude, maxLongitude: longitude,
                         count: 1, intensity: 1)]
    }

    @Test func pitchHeatCentersOnPitchCenter() throws {
        let d = try #require(PitchHeat.density(cells: pitchCell(forward: 0, right: 0), pitch: Self.pitch))
        let peak = d.values.firstIndex(of: 1)!
        #expect(d.values.max() == 1)
        #expect(abs(peak % d.width - d.width / 2) <= 1)
        #expect(abs(peak / d.width - d.height / 2) <= 1)
        #expect(d.minLatitude == -PitchHeat.margin)
        #expect(d.maxLatitude == Self.pitch.length + PitchHeat.margin)
        #expect(d.minLongitude == -PitchHeat.margin)
        #expect(d.maxLongitude == Self.pitch.width + PitchHeat.margin)
    }

    @Test func pitchHeatTopRowIsGoalLineA() throws {
        // 방위 0이면 A는 남쪽 골라인: 중심에서 남쪽으로 45 m = 골라인 A에서 5 m
        let d = try #require(PitchHeat.density(cells: pitchCell(forward: -45, right: 0), pitch: Self.pitch))
        let peakRow = d.values.firstIndex(of: 1)! / d.width
        #expect(peakRow < d.height / 4)
    }

    @Test func heatOutsideMarginIsDropped() {
        #expect(PitchHeat.density(cells: pitchCell(forward: 0, right: 32 + 30), pitch: Self.pitch) == nil)
    }
}

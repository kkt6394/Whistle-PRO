import Testing
@testable import RefereeSession

/// 히트맵 전체가 보이는 지도 범위 (상세 화면 지도·활동 카드 스냅숏이 함께 쓴다)
struct HeatmapBoundsTests {
    private func cell(_ lat: Double, _ lon: Double) -> HeatCell {
        HeatCell(minLatitude: lat, minLongitude: lon, maxLatitude: lat + 0.0001, maxLongitude: lon + 0.0001, count: 1, intensity: 1)
    }

    @Test func noCellsNoBounds() {
        #expect(Heatmap.bounds(cells: []) == nil)
    }

    @Test func centerAndSizeCoverAllCellsAndPins() throws {
        let bounds = try #require(Heatmap.bounds(cells: [cell(37.5, 127.0), cell(37.501, 127.002)], pinCoordinates: [(37.4995, 127.0)]))
        #expect(abs(bounds.centerLatitude - (37.4995 + 37.5011) / 2) < 1e-9)
        #expect(abs(bounds.centerLongitude - (127.0 + 127.0021) / 2) < 1e-9)
        // 남북 약 0.0016° ≈ 178m, 동서 0.0021° × cos(37.5°) ≈ 185m
        #expect(abs(bounds.northSouthMeters - 0.0016 * 111_320) < 1)
        #expect(bounds.eastWestMeters > 180 && bounds.eastWestMeters < 190)
    }

    /// 여백을 두고 보는 거리: 넓은 쪽의 2.5배, 최소 150m
    @Test func viewingDistanceHasMarginAndFloor() throws {
        let wide = try #require(Heatmap.bounds(cells: [cell(37.5, 127.0), cell(37.501, 127.002)]))
        #expect(abs(wide.viewingDistance - max(wide.northSouthMeters, wide.eastWestMeters) * 2.5) < 1e-6)
        let tiny = try #require(Heatmap.bounds(cells: [cell(37.5, 127.0)]))
        #expect(tiny.viewingDistance == 150)
    }

    @Test func koreaUsesStandardMap() throws {
        #expect(try #require(Heatmap.bounds(cells: [cell(37.5, 127.0)])).inKorea)
        #expect(try #require(Heatmap.bounds(cells: [cell(51.5, -0.1)])).inKorea == false)
    }
}

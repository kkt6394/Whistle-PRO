import Foundation

/// 지도 위 한 점.
public struct GeoPoint: Codable, Equatable, Hashable, Sendable {
    public var latitude: Double
    public var longitude: Double

    public init(latitude: Double, longitude: Double) {
        self.latitude = latitude
        self.longitude = longitude
    }
}

/// 경기장 좌표(m). x는 왼쪽 터치라인에서 오른쪽으로(0…width), y는 위쪽 골라인(A)에서 아래로(0…length).
public struct PitchPoint: Codable, Equatable, Hashable, Sendable {
    public var x: Double
    public var y: Double

    public init(x: Double, y: Double) {
        self.x = x
        self.y = y
    }
}

/// 직사각형 경기장. bearing은 골라인 A→B 방향의 방위각(도, 북=0, 시계 방향, 0..<360).
/// 경기장 크기라 중심 기준 평면 근사를 쓴다(WorkoutAnalysis.distance와 같다).
public struct Pitch: Codable, Equatable, Hashable, Sendable {
    public var center: GeoPoint
    public var length: Double
    public var width: Double
    public var bearing: Double

    public init(center: GeoPoint, length: Double, width: Double, bearing: Double) {
        self.center = center
        self.length = length
        self.width = width
        self.bearing = bearing
    }

    private static let metersPerDegree = 111_320.0
    private static let minimumArea = 100.0

    private var metersPerDegreeLongitude: Double { Pitch.metersPerDegree * cos(center.latitude * .pi / 180) }
    private var directionNorth: Double { cos(bearing * .pi / 180) }
    private var directionEast: Double { sin(bearing * .pi / 180) }

    /// A왼쪽, A오른쪽, B오른쪽, B왼쪽 순서.
    public var corners: [GeoPoint] {
        [
            geo(PitchPoint(x: 0, y: 0)),
            geo(PitchPoint(x: width, y: 0)),
            geo(PitchPoint(x: width, y: length)),
            geo(PitchPoint(x: 0, y: length)),
        ]
    }

    public func local(_ p: GeoPoint) -> PitchPoint {
        let north = (p.latitude - center.latitude) * Pitch.metersPerDegree
        let east = (p.longitude - center.longitude) * metersPerDegreeLongitude
        // 오른쪽 = 진행 방향을 시계 방향으로 90도 돌린 쪽
        let along = north * directionNorth + east * directionEast
        let across = -north * directionEast + east * directionNorth
        return PitchPoint(x: across + width / 2, y: along + length / 2)
    }

    public func geo(_ p: PitchPoint) -> GeoPoint {
        let across = p.x - width / 2
        let along = p.y - length / 2
        let north = along * directionNorth - across * directionEast
        let east = along * directionEast + across * directionNorth
        return GeoPoint(
            latitude: center.latitude + north / Pitch.metersPerDegree,
            longitude: center.longitude + east / metersPerDegreeLongitude
        )
    }

    /// 경기장에서 margin(m) 안쪽까지 포함해 안에 있는지.
    public func contains(_ p: GeoPoint, margin: Double) -> Bool {
        let l = local(p)
        return l.x >= -margin && l.x <= width + margin && l.y >= -margin && l.y <= length + margin
    }

    /// 찍은 네 모서리를 직사각형으로 다듬는다. 순서는 상관없다.
    /// 네 개가 아니거나 넓이가 100㎡ 미만이면 nil. 첫 번째 점 쪽 골라인이 A.
    public static func fromCorners(_ corners: [GeoPoint]) -> Pitch? {
        guard corners.count == 4 else { return nil }
        let centroid = GeoPoint(
            latitude: corners.map(\.latitude).reduce(0, +) / 4,
            longitude: corners.map(\.longitude).reduce(0, +) / 4
        )
        let metersPerDegreeLongitude = metersPerDegree * cos(centroid.latitude * .pi / 180)
        // 무게중심 기준 (북, 동) m
        let pts = corners.map {
            [($0.latitude - centroid.latitude) * metersPerDegree,
             ($0.longitude - centroid.longitude) * metersPerDegreeLongitude]
        }
        let first = pts[0]
        // 각도순 정렬로 꼬인 순서를 푼다
        let ring = pts.sorted { atan2($0[1], $0[0]) < atan2($1[1], $1[0]) }
        func side(_ i: Int) -> [Double] {
            [ring[(i + 1) % 4][0] - ring[i][0], ring[(i + 1) % 4][1] - ring[i][1]]
        }
        func size(_ v: [Double]) -> Double { (v[0] * v[0] + v[1] * v[1]).squareRoot() }
        let pairA = (size(side(0)) + size(side(2))) / 2
        let pairB = (size(side(1)) + size(side(3))) / 2
        // 긴 쪽이 터치라인. 마주 보는 두 변은 반대 방향이라 한쪽을 뒤집어 평균한다
        let touch = pairA >= pairB ? 0 : 1
        let length = max(pairA, pairB)
        let width = min(pairA, pairB)
        // 넓이는 꼭짓점 공식으로. 한 줄로 늘어선 점은 0이 된다
        var twiceArea = 0.0
        for i in 0..<4 {
            let a = ring[i]
            let b = ring[(i + 1) % 4]
            twiceArea += a[0] * b[1] - b[0] * a[1]
        }
        let area = abs(twiceArea) / 2
        if area < minimumArea { return nil }
        let s1 = side(touch)
        let s2 = side(touch + 2)
        var north = (s1[0] - s2[0]) / 2
        var east = (s1[1] - s2[1]) / 2
        let norm = (north * north + east * east).squareRoot()
        if norm == 0 { return nil }
        north /= norm
        east /= norm
        // 첫 점이 중심보다 진행 방향 앞쪽이면 A가 반대편이므로 방향을 뒤집는다
        if first[0] * north + first[1] * east > 0 {
            north = -north
            east = -east
        }
        let bearing = (atan2(east, north) * 180 / .pi + 360).truncatingRemainder(dividingBy: 360)
        return Pitch(center: centroid, length: length, width: width, bearing: bearing)
    }
}

/// 라인 치수(m). 정규 치수를 작은 경기장에서는 줄인다.
public struct PitchMarkings: Equatable, Sendable {
    public let scale: Double

    public init(scale: Double) {
        self.scale = scale
    }

    public var centerCircleRadius: Double { 9.15 * scale }
    public var penaltyAreaWidth: Double { 40.32 * scale }
    public var penaltyAreaDepth: Double { 16.5 * scale }
    public var goalAreaWidth: Double { 18.32 * scale }
    public var goalAreaDepth: Double { 5.5 * scale }
    public var penaltyMarkDistance: Double { 11.0 * scale }

    public static func of(length: Double, width: Double) -> PitchMarkings {
        let scale = min(1.0, min((width - 4) / 40.32, (length / 2 - 1) / (16.5 + 9.15)))
        return PitchMarkings(scale: max(0.2, scale))
    }
}

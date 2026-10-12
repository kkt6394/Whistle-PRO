import Foundation

public struct WorkoutSample: Codable, Equatable, Sendable {
    /// Unix epoch 초
    public var t: Double
    public var value: Double

    public init(t: Double, value: Double) {
        self.t = t
        self.value = value
    }
}

public struct RoutePoint: Codable, Equatable, Sendable {
    public var t: Double
    public var latitude: Double
    public var longitude: Double
    /// m/s (음수면 측정 불가)
    public var speed: Double
    /// 수평 정확도 m (음수면 무효)
    public var accuracy: Double

    public init(t: Double, latitude: Double, longitude: Double, speed: Double, accuracy: Double) {
        self.t = t
        self.latitude = latitude
        self.longitude = longitude
        self.speed = speed
        self.accuracy = accuracy
    }
}

/// 한 경기 동안 수집한 운동 데이터. 거리·칼로리는 운동 시작부터의 누적값이다.
public struct WorkoutLog: Codable, Equatable, Sendable {
    public var matchID: UUID
    /// bpm
    public var heartRate: [WorkoutSample] = []
    /// 누적 m
    public var distance: [WorkoutSample] = []
    /// 누적 kcal
    public var energy: [WorkoutSample] = []
    public var route: [RoutePoint] = []

    public init(matchID: UUID) {
        self.matchID = matchID
    }
}

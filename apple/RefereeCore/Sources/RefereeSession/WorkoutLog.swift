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
    /// 운동 세션을 새로 시작한 시각들 (경기 첫 시작 제외). 그 뒤 누적값은 0부터 다시 쌓인다.
    /// 값이 줄어드는지로만 재시작을 알면 재시작 뒤 첫 값이 더 클 때 적게 셌다 (백로그)
    public var restarts: [Double] = []

    public init(matchID: UUID) {
        self.matchID = matchID
    }

    private enum CodingKeys: String, CodingKey {
        case matchID, heartRate, distance, energy, route, restarts
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        matchID = try c.decode(UUID.self, forKey: .matchID)
        heartRate = try c.decode([WorkoutSample].self, forKey: .heartRate)
        distance = try c.decode([WorkoutSample].self, forKey: .distance)
        energy = try c.decode([WorkoutSample].self, forKey: .energy)
        route = try c.decode([RoutePoint].self, forKey: .route)
        restarts = try c.decodeIfPresent([Double].self, forKey: .restarts) ?? []
    }

    /// 재시작이 없으면 키를 쓰지 않는다 (예전 파일과 같은 형식)
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(matchID, forKey: .matchID)
        try c.encode(heartRate, forKey: .heartRate)
        try c.encode(distance, forKey: .distance)
        try c.encode(energy, forKey: .energy)
        try c.encode(route, forKey: .route)
        if !restarts.isEmpty { try c.encode(restarts, forKey: .restarts) }
    }
}

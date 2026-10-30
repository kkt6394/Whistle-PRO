import Foundation
import RefereeCore

/// 리플레이 영상의 그림 경기장 원근. 경기장 좌표(`pitch.local`)에서 B 골라인이 위, 먼 쪽(위)이 작다.
/// 시안 replay-layout.html의 `project`와 같은 식이다.
public struct ReplayPerspective {
    /// 경기장 둘레 여백 (m)
    static let margin = 14.0
    static let tilt = 0.62
    static let centerY = 0.44

    public let pitch: Pitch
    public let width: Double
    public let height: Double
    private let zoom: Double

    public init(pitch: Pitch, width: Double, height: Double) {
        self.pitch = pitch
        self.width = width
        self.height = height
        // 여백을 포함한 경기장 폭이 화면 폭을 채운다
        zoom = width / (pitch.width + 2 * ReplayPerspective.margin)
    }

    public func point(_ p: GeoPoint) -> (x: Double, y: Double, scale: Double) {
        let local = pitch.local(p)
        let dx = (local.x - pitch.width / 2) * zoom
        // 음수 = 먼 쪽(위)
        let dy = (pitch.length / 2 - local.y) * zoom
        let depth = 1 - dy / (height * 1.6)
        let scale = 1 / min(max(depth, 0.6), 2.2)
        return (width / 2 + dx * scale, height * ReplayPerspective.centerY + dy * ReplayPerspective.tilt * scale, scale)
    }
}

import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct ReplayPerspectiveTests {
    private let pitch = Pitch(center: GeoPoint(latitude: 37.5, longitude: 127.0), length: 100, width: 64, bearing: 20)

    @Test func farSideIsSmaller() {
        let perspective = ReplayPerspective(pitch: pitch, width: 390, height: 600)
        let a = perspective.point(pitch.geo(PitchPoint(x: 32, y: 0)))
        let b = perspective.point(pitch.geo(PitchPoint(x: 32, y: 100)))
        #expect(b.scale < a.scale)
        #expect(b.y < a.y)
    }

    @Test func centerIsNearMiddle() {
        let perspective = ReplayPerspective(pitch: pitch, width: 390, height: 600)
        let c = perspective.point(pitch.center)
        #expect(abs(c.x - 195) < 1e-9)
        #expect(abs(c.y - 600 * 0.487) < 1e-9)
    }
}

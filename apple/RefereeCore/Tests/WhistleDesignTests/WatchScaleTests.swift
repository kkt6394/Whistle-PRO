import CoreGraphics
import Testing
@testable import WhistleDesign

struct WatchScaleTests {
    @Test func fortyMillimetreIsOne() {
        #expect(WatchScale(width: 162).factor == 1)
        // 안전 영역 폭(158)도 40mm로 본다: 1보다 작아지지 않는다
        #expect(WatchScale(width: 158).factor == 1)
    }

    @Test func ultraIsAboutOnePointTwoFive() {
        #expect(abs(WatchScale(width: 205).factor - 205.0 / 162) < 0.0001)
        #expect(WatchScale(width: 207).factor > 1.25)
    }

    @Test func scaledMultiplies() {
        #expect(WatchScale(width: 324).scaled(10) == 20)
        #expect(WatchScale(width: 0).scaled(10) == 10)
    }
}

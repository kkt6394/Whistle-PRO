import CoreGraphics
import Testing
@testable import WhistleDesign

struct ScreenCornerTests {
    /// 40·41·42·44·45·46·49mm (스펙 §2: 7개 크기 모두 표에 있다)
    @Test func tableCoversSevenSizes() {
        #expect(ScreenCorner.table.count >= 7)
        for size in MainLayoutTests.screens {
            #expect(ScreenCorner.table.contains { $0.size == size }, "\(size) 없음")
        }
    }

    @Test func exactSizeUsesItsOwnRadius() {
        for entry in ScreenCorner.table {
            #expect(ScreenCorner.radius(for: entry.size) == entry.radius)
        }
    }

    @Test func unknownSizeUsesNearest() {
        let small = ScreenCorner.table.min { $0.size.width < $1.size.width }!
        let large = ScreenCorner.table.max { $0.size.width < $1.size.width }!
        #expect(ScreenCorner.radius(for: CGSize(width: small.size.width - 3, height: small.size.height - 2)) == small.radius)
        #expect(ScreenCorner.radius(for: CGSize(width: large.size.width + 20, height: large.size.height + 20)) == large.radius)
    }

    @Test func radiiArePlausible() {
        for entry in ScreenCorner.table {
            #expect(entry.radius > 10 && entry.radius < entry.size.width / 3, "\(entry)")
        }
    }
}

import CoreGraphics
import Testing
@testable import WhistleDesign

struct WatchScreenTests {
    /// 40·41·42·44·45·46mm·Ultra 1·2·Ultra 3 (B1 스펙 §2: 모든 크기가 표에 있다)
    @Test func tableCoversEveryScreen() {
        for size in MainLayoutTests.screens {
            #expect(WatchScreen.table.contains { $0.size == size }, "\(size) 없음")
        }
    }

    @Test func exactSizeUsesItsOwnEntry() {
        for entry in WatchScreen.table {
            #expect(WatchScreen.metrics(for: entry.size) == entry)
        }
    }

    @Test func unknownSizeUsesNearest() {
        let small = WatchScreen.table.min { $0.size.width < $1.size.width }!
        let large = WatchScreen.table.max { $0.size.width < $1.size.width }!
        #expect(WatchScreen.metrics(for: CGSize(width: small.size.width - 3, height: small.size.height - 2)).cornerRadius == small.cornerRadius)
        #expect(WatchScreen.metrics(for: CGSize(width: large.size.width + 20, height: large.size.height + 20)).cornerRadius == large.cornerRadius)
    }

    /// 시스템 시각은 화면 오른쪽 위에 있다 (시뮬레이터에서 잰 글자 범위)
    @Test func systemClockSitsTopRight() {
        for m in WatchScreen.table {
            #expect(m.cornerRadius > 10 && m.cornerRadius < m.size.width / 3, "\(m)")
            #expect(m.clockTop > 0 && m.clockBottom > m.clockTop && m.clockBottom < m.size.height * 0.2, "\(m)")
            #expect(m.clockLeft > m.size.width * 0.5 && m.clockLeft < m.size.width, "\(m)")
        }
    }
}

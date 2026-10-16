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

    /// 화면 끝에서 inset만큼 들어온 버튼은 화면 R - inset이면 곡선과 나란하다. 버튼 높이 절반(캡슐)을 넘지 않는다
    @Test func concentricRadiusFollowsScreenCorner() {
        let small = WatchScreen.metrics(for: CGSize(width: 162, height: 197))   // 화면 R 29
        let ultra = WatchScreen.metrics(for: CGSize(width: 211, height: 257))   // 화면 R 58
        #expect(small.concentricRadius(inset: 10, height: 80) == 19)
        #expect(ultra.concentricRadius(inset: 13, height: 100) == 45)
        #expect(ultra.concentricRadius(inset: 13, height: 60) == 30)
        #expect(small.concentricRadius(inset: 40, height: 80) == 0)
    }

    /// 아래 모서리 곁 버튼: 동심 반지름이 버튼 높이 안에 들어가면 좌우 들임은 그대로 (팀 버튼과 같은 결과)
    @Test func bottomCornerKeepsSideWhenConcentricFits() {
        let small = WatchScreen.metrics(for: CGSize(width: 162, height: 197))   // 화면 R 29
        let corner = small.bottomCorner(bottom: 10, side: 6, height: 44)
        #expect(corner.radius == 19)
        #expect(corner.side == 6)
        // 좌우 들임이 아래 여백보다 커도 동심
        let wide = small.bottomCorner(bottom: 6, side: 10, height: 44)
        #expect(wide.radius == 19)
        #expect(wide.side == 10)
    }

    /// 버튼이 낮아 반지름이 캡슐(높이 절반)에 막히면, 끝 둥근 곳이 화면 곡선에 닿지 않게 좌우를 더 들인다.
    /// 46mm(R 51), 아래 13·좌우 8·높이 44: 끝 원 중심이 화면 모서리 원 중심에서 R - 8 - 22 = 21 안에 있어야 한다
    @Test func bottomCornerMovesCapsuleInward() {
        let large = WatchScreen.metrics(for: CGSize(width: 208, height: 248))   // 화면 R 51
        let corner = large.bottomCorner(bottom: 13, side: 8, height: 44)
        #expect(corner.radius == 22)
        // dy = 51 - 13 - 22 = 16, dx = √(21² - 16²) ≈ 13.60 → 51 - 22 - 13.60 ≈ 15.40
        #expect(abs(corner.side - 15.40) < 0.01)
    }

    /// 모든 화면·높이에서 버튼 아래 모서리가 화면 곡선과 좌우 들임 이상 떨어져 있다 (곡선에 잘리지 않는다)
    @Test func bottomCornerStaysInsideScreenCurve() {
        for screen in WatchScreen.table {
            for height in stride(from: CGFloat(30), through: 120, by: 7) {
                let bottom: CGFloat = 10 * screen.size.width / 162, side: CGFloat = 6 * screen.size.width / 162
                let corner = screen.bottomCorner(bottom: bottom, side: side, height: height)
                #expect(corner.radius <= height / 2 + 0.001)
                #expect(corner.side >= side - 0.001)
                // 버튼 왼쪽 아래 모서리 원 중심과 화면 왼쪽 아래 모서리 원 중심 (화면 아래 끝 y = 0, 위로 +)
                let r = screen.cornerRadius
                let dx: CGFloat = max(0, r - (corner.side + corner.radius))
                let dy: CGFloat = max(0, r - (bottom + corner.radius))
                let gap = r - (dx * dx + dy * dy).squareRoot() - corner.radius
                #expect(gap >= side - 0.001, "\(screen.size) h \(height): 곡선까지 \(gap)")
            }
        }
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

import CoreGraphics

/// 워치 화면별 실측값 (B1 스펙 §2). watchOS는 화면 모서리 반지름도, 시스템 시각 위치도 API로 주지 않고,
/// GeometryReader의 위쪽 안전 영역 값도 0으로 들어와(2026-10-15 확인) 시뮬레이터 스크린샷에서 잰 값을 둔다
/// (`apple/scripts/watch_metrics.swift`).
/// - 화면 모서리는 연속 곡선이라, 대각선 지점이 같은 원의 반지름을 쓴다.
/// - 시스템 시각은 글자(왼쪽의 폰 연결 끊김 아이콘 포함)가 차지하는 범위다.
/// 41·45mm와 Ultra 1·2는 시뮬레이터가 없어 42·46mm·Ultra 3 값을 폭 비율로 줄였다.
public struct WatchScreen: Equatable, Sendable {
    public var size: CGSize
    public var cornerRadius: CGFloat
    public var clockTop: CGFloat
    public var clockBottom: CGFloat
    public var clockLeft: CGFloat

    public static let table: [WatchScreen] = [
        WatchScreen(size: CGSize(width: 162, height: 197), cornerRadius: 29, clockTop: 10.5, clockBottom: 20.5, clockLeft: 110),     // 40mm (SE)
        WatchScreen(size: CGSize(width: 176, height: 215), cornerRadius: 42, clockTop: 16.5, clockBottom: 26, clockLeft: 123.5),     // 41mm ≈ 42mm × 176/187
        WatchScreen(size: CGSize(width: 187, height: 223), cornerRadius: 44.5, clockTop: 17.5, clockBottom: 27.5, clockLeft: 131.5), // 42mm (Series 10·11)
        WatchScreen(size: CGSize(width: 184, height: 224), cornerRadius: 34, clockTop: 13.5, clockBottom: 24.5, clockLeft: 128),     // 44mm (SE)
        WatchScreen(size: CGSize(width: 198, height: 242), cornerRadius: 48.5, clockTop: 19, clockBottom: 29.5, clockLeft: 140.5),   // 45mm ≈ 46mm × 198/208
        WatchScreen(size: CGSize(width: 208, height: 248), cornerRadius: 51, clockTop: 20, clockBottom: 31, clockLeft: 147.5),       // 46mm
        WatchScreen(size: CGSize(width: 205, height: 251), cornerRadius: 56.5, clockTop: 22, clockBottom: 32.5, clockLeft: 145),     // Ultra 1·2 ≈ Ultra 3 × 205/211
        WatchScreen(size: CGSize(width: 211, height: 257), cornerRadius: 58, clockTop: 22.5, clockBottom: 33.5, clockLeft: 149),     // Ultra 3
    ]

    /// 화면 끝에서 inset만큼 들어온 버튼의 모서리 반지름. 화면 모서리와 동심이라 곡선과 나란하고 잘리지 않는다.
    /// 버튼 높이 절반을 넘으면 캡슐이 된다.
    public func concentricRadius(inset: CGFloat, height: CGFloat) -> CGFloat {
        min(max(0, cornerRadius - inset), height / 2)
    }

    /// 화면 아래 모서리 곁 버튼(바닥 줄)의 모서리 반지름과 좌우 들임.
    /// - bottom: 화면 바닥에서 버튼 아래까지, side: 바라는 좌우 들임, height: 버튼 높이
    /// 반지름은 아래 여백·좌우 들임 중 큰 쪽만큼 화면 모서리와 동심이다. 버튼이 낮아 캡슐(높이 절반)에 막히면
    /// 끝 둥근 곳이 곡선에 닿으므로, 곡선과 side 이상 떨어지도록 좌우를 더 들인다 (46mm [지난 경기] 44pt 등).
    public func bottomCorner(bottom: CGFloat, side: CGFloat, height: CGFloat) -> BottomCorner {
        let radius = concentricRadius(inset: max(bottom, side), height: height)
        let gap = min(bottom, side)
        // 버튼 모서리 원 중심이 화면 모서리 원 중심에서 reach 안에 있으면 곡선과 gap 이상 떨어진다
        let reach = max(0, cornerRadius - gap - radius)
        let dy = max(0, cornerRadius - bottom - radius)
        let dx = max(0, reach * reach - dy * dy).squareRoot()
        return BottomCorner(radius: radius, side: max(side, cornerRadius - radius - dx))
    }

    /// 표에서 가로·세로가 가장 가까운 화면
    public static func metrics(for size: CGSize) -> WatchScreen {
        table.min { distance($0.size, size) < distance($1.size, size) } ?? table[0]
    }

    private static func distance(_ a: CGSize, _ b: CGSize) -> CGFloat {
        hypot(a.width - b.width, a.height - b.height)
    }
}

/// 바닥 줄 버튼의 모서리 반지름과 좌우 들임 (`WatchScreen.bottomCorner`)
public struct BottomCorner: Equatable, Sendable {
    public var radius: CGFloat
    public var side: CGFloat

    public init(radius: CGFloat, side: CGFloat) {
        self.radius = radius
        self.side = side
    }
}

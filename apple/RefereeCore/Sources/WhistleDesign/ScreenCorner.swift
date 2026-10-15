import CoreGraphics

/// 화면 모서리 반지름 (B1 스펙 §2). watchOS는 API로 주지 않아 시뮬레이터 스크린샷에서 잰 값을 둔다
/// (`apple/scripts/watch_metrics.swift`, 2026-10-15). 화면 모서리는 연속 곡선이라, 대각선 지점이 같은 원의 반지름이다.
/// 41·45mm와 Ultra 1·2는 시뮬레이터가 없어 42·46mm·Ultra 3 값을 폭 비율로 줄였다.
public enum ScreenCorner {
    public static let table: [(size: CGSize, radius: CGFloat)] = [
        (CGSize(width: 162, height: 197), 29),     // 40mm (SE)
        (CGSize(width: 176, height: 215), 42),     // 41mm = 42mm 값 × 176/187
        (CGSize(width: 187, height: 223), 44.5),   // 42mm (Series 10·11)
        (CGSize(width: 184, height: 224), 34),     // 44mm (SE)
        (CGSize(width: 198, height: 242), 48.5),   // 45mm = 46mm 값 × 198/208
        (CGSize(width: 208, height: 248), 51),     // 46mm
        (CGSize(width: 205, height: 251), 56.5),   // 49mm Ultra 1·2 = Ultra 3 값 × 205/211
        (CGSize(width: 211, height: 257), 58),     // 49mm Ultra 3
    ]

    /// 표에서 가로·세로가 가장 가까운 크기의 반지름
    public static func radius(for size: CGSize) -> CGFloat {
        table.min { distance($0.size, size) < distance($1.size, size) }?.radius ?? 0
    }

    private static func distance(_ a: CGSize, _ b: CGSize) -> CGFloat {
        hypot(a.width - b.width, a.height - b.height)
    }
}

import SwiftUI
import RefereeSession

/// 그리기 전에 반원 색을 정하는 순수 함수
public enum TeamMarkColors {
    /// 왼쪽 반은 주색, 오른쪽 반은 보조 색 (없으면 주색). onDark면 각 반에 markOnDark를 따로 적용한다.
    public static func halves(primary: TeamColor, secondary: TeamColor?, onDark: Bool) -> (left: RGB, right: RGB) {
        func rgb(_ c: TeamColor) -> RGB {
            onDark ? c.markOnDark : RGB(red: c.red, green: c.green, blue: c.blue)
        }
        let left = rgb(primary)
        return (left, secondary.map(rgb) ?? left)
    }
}

/// 팀 색 원. 보조 색이 있으면 왼쪽 주색·오른쪽 보조 색의 세로 반반 (구분선 없음).
public struct TeamMark: View {
    let primary: TeamColor
    let secondary: TeamColor?
    let size: CGFloat
    let onDark: Bool
    let border: Color?

    public init(primary: TeamColor, secondary: TeamColor?, size: CGFloat, onDark: Bool = false, border: Color? = nil) {
        self.primary = primary
        self.secondary = secondary
        self.size = size
        self.onDark = onDark
        self.border = border
    }

    public var body: some View {
        let halves = TeamMarkColors.halves(primary: primary, secondary: secondary, onDark: onDark)
        ZStack {
            Circle().fill(halves.left.color)
            if secondary != nil {
                // 오른쪽 반: 같은 크기의 원을 가로 절반 폭 사각형으로 잘라 정확히 세로 이등분
                Circle().fill(halves.right.color)
                    .mask(alignment: .trailing) {
                        Rectangle().frame(width: size / 2, height: size)
                    }
            }
        }
        .overlay { if let border { Circle().stroke(border, lineWidth: 1) } }
        .frame(width: size, height: size)
    }
}

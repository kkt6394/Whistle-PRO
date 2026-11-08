import Foundation

/// 화면에 보이는 소수: 기기 언어·지역의 소수점으로 (독일어 12,3 · 영어 12.3). 묶음 기호(1,000)는 넣지 않는다.
/// 예전엔 `String(format: "%.1f")`라 독일어·프랑스어 등 열 개 언어에서도 점으로 찍혔다 (2026-11-09 독일어 점검).
/// 리플레이 영상(영어 고정)과 시험용 고정 문자열은 이 함수를 쓰지 않는다. 안드로이드 `NumberText`와 같은 규칙
public enum NumberText {
    public static func decimal(_ value: Double, digits: Int, locale: Locale = .current) -> String {
        let formatter = NumberFormatter()
        formatter.locale = locale
        formatter.numberStyle = .decimal
        formatter.usesGroupingSeparator = false
        formatter.minimumFractionDigits = digits
        formatter.maximumFractionDigits = digits
        // String(format:)과 같은 반올림 (0.25 → 0.3)
        formatter.roundingMode = .halfUp
        return formatter.string(from: NSNumber(value: value)) ?? String(format: "%.\(digits)f", value)
    }
}

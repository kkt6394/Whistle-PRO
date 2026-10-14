/// 기간 코드(`PeriodRef.label`: 1H·2H·Q1~Q4·ET1·ET2)의 구분. 화면에서는 앱이 현지화한 이름으로 바꾼다
/// (한국어: 전반·후반·1쿼터·연장 전반). 코드는 기록·보고서 데이터에 그대로 남는다.
public enum PeriodName: Equatable, Sendable {
    case half(Int), quarter(Int), extra(Int)

    public init?(label: String) {
        if label.hasPrefix("ET"), let n = Int(label.dropFirst(2)) {
            self = .extra(n)
        } else if label.hasPrefix("Q"), let n = Int(label.dropFirst()) {
            self = .quarter(n)
        } else if label.hasSuffix("H"), let n = Int(label.dropLast()) {
            self = .half(n)
        } else {
            return nil
        }
    }
}

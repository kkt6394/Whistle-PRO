/// 스토어 스크린샷용 데모 이름 두 벌 (워치·폰 공통). 실제 선수·팀과 무관한 가상 이름.
public struct DemoNames: Sendable {
    public let homeName: String, awayName: String
    /// 8명, 번호 1·4·5·7·9·10·14·17, 앞 6명 선발, 14·17은 후보
    public let homeRoster: [Player]
    /// 2명, 번호 4·11
    public let awayRoster: [Player]
    public let presetName: String
    public let competition: String, venue: String, referee: String

    private static func home(_ names: [String]) -> [Player] {
        let numbers = [1, 4, 5, 7, 9, 10, 14, 17]
        return zip(numbers, names).enumerated().map { index, pair in
            Player(number: pair.0, name: pair.1, starter: index < 6 ? true : nil)
        }
    }

    public static let ko = DemoNames(
        homeName: "FC 서울숲", awayName: "한강 유나이티드",
        homeRoster: home(["김민준", "이서준", "박도윤", "최하준", "정시우", "한예준", "조은우", "윤도현"]),
        awayRoster: [Player(number: 4, name: "정우진"), Player(number: 11, name: "강지호")],
        presetName: "조기축구 25분×4",
        competition: "서울 아마추어 리그", venue: "서울숲 운동장", referee: "김민수")

    public static let en = DemoNames(
        homeName: "Riverside FC", awayName: "Harbor United",
        homeRoster: home(["Carter", "Nguyen", "Okafor", "Silva", "Park", "Müller", "Johansson", "Dubois"]),
        awayRoster: [Player(number: 4, name: "Rossi"), Player(number: 11, name: "Tanaka")],
        presetName: "Sunday League 25×4",
        competition: "Riverside Amateur League", venue: "Riverside Park", referee: "A. Kim")

    /// "-demoNames" 다음 값이 "en"이면 en, "ko"면 ko, 인자가 없으면 nil (스토어 모드 아님)
    public static func fromArguments(_ arguments: [String]) -> DemoNames? {
        guard let index = arguments.firstIndex(of: "-demoNames"),
              index + 1 < arguments.count else { return nil }
        switch arguments[index + 1] {
        case "en": return .en
        case "ko": return .ko
        default: return nil
        }
    }
}

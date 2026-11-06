/// 스토어 스크린샷용 데모 이름 두 벌 (워치·폰 공통). 실제 선수·팀과 무관한 가상 이름.
public struct DemoNames: Sendable {
    public let homeName: String, awayName: String
    /// 16명, 1~11 선발, 12·14·15·17·20 후보 (데모 이벤트가 쓰는 1·4·5·7·9·10·14·17 포함)
    public let homeRoster: [Player]
    /// 16명, 1~11 선발, 13·16·18·19·21 후보 (데모 이벤트가 쓰는 4·11 포함)
    public let awayRoster: [Player]
    public let presetName: String
    public let competition: String, venue: String, referee: String

    /// 선발 11명(번호 1~11) + 후보(번호는 benchNumbers). 실제 경기처럼 꽉 찬 명단 — 홍보 영상·스크린샷용
    private static func team(_ starters: [String], bench: [String], benchNumbers: [Int]) -> [Player] {
        zip(1...11, starters).map { Player(number: $0.0, name: $0.1, starter: true) }
            + zip(benchNumbers, bench).map { Player(number: $0.0, name: $0.1) }
    }
    private static func home(_ starters: [String], bench: [String]) -> [Player] {
        team(starters, bench: bench, benchNumbers: [12, 14, 15, 17, 20])
    }
    private static func away(_ starters: [String], bench: [String]) -> [Player] {
        team(starters, bench: bench, benchNumbers: [13, 16, 18, 19, 21])
    }

    public static let ko = DemoNames(
        homeName: "FC 서울숲", awayName: "한강 유나이티드",
        homeRoster: home(["김민준", "서지훈", "임건우", "이서준", "박도윤", "오현우", "최하준", "신유준", "정시우", "한예준", "권태윤"],
                         bench: ["황민재", "조은우", "안준서", "윤도현", "송재원"]),
        awayRoster: away(["배성민", "류지환", "홍승현", "정우진", "문태호", "양동현", "구본혁", "남찬영", "노준혁", "하승우", "강지호"],
                         bench: ["차민성", "장도윤", "엄태준", "전호진", "고성민"]),
        presetName: "조기축구 25분×4",
        competition: "서울 아마추어 리그", venue: "서울숲 운동장", referee: "김민수")

    public static let en = DemoNames(
        homeName: "Riverside FC", awayName: "Harbor United",
        homeRoster: home(["Carter", "Walsh", "Mensah", "Nguyen", "Okafor", "Hughes", "Silva", "Reyes", "Park", "Müller", "Diallo"],
                         bench: ["Brennan", "Johansson", "Kowalski", "Dubois", "Ortiz"]),
        awayRoster: away(["Fischer", "Lindqvist", "Moreau", "Rossi", "Adeyemi", "Costa", "O'Brien", "Haddad", "Novak", "Petrov", "Tanaka"],
                         bench: ["Larsen", "Bianchi", "Murphy", "Santos", "Weber"]),
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

import Foundation

/// 명단의 선수 한 명
public struct Player: Codable, Equatable, Hashable, Sendable {
    public var number: Int
    public var name: String

    public init(number: Int, name: String) {
        self.number = number
        self.name = name
    }
}

public enum RosterIssue: Equatable, Sendable {
    /// 0~99 밖의 번호
    case numberOutOfRange(Int)
    case duplicateNumber(Int)
    /// 이름이 비었거나 공백뿐 (번호로 구분)
    case emptyName(Int)
    case nameTooLong(Int)
    case tooManyPlayers
}

public enum Roster {
    /// 워치 숫자패드가 두 자리까지라 같은 범위
    public static let numberRange = 0...99
    public static let maximumNameLength = 20
    public static let maximumPlayers = 40

    public struct ParseResult: Equatable, Sendable {
        public var players: [Player]
        /// 번호나 이름을 읽지 못해 건너뛴 줄 수 (빈 줄은 세지 않는다)
        public var skippedLines: Int
    }

    /// 한 줄에 한 명: "10 홍길동", "#7, Kim", "9\tLee". 같은 번호가 다시 나오면 뒤의 것을 쓴다. 번호순으로 돌려준다.
    public static func parse(_ text: String) -> ParseResult {
        var byNumber: [Int: String] = [:]
        var skipped = 0
        for rawLine in text.split(omittingEmptySubsequences: false, whereSeparator: \.isNewline) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            if line.isEmpty { continue }
            var rest = Substring(line)
            if rest.first == "#" { rest = rest.dropFirst() }
            let digits = rest.prefix { $0.isASCII && $0.isNumber }
            let name = rest.dropFirst(digits.count)
                .drop { $0.isWhitespace || $0 == "," || $0 == "." || $0 == "-" || $0 == ":" }
                .trimmingCharacters(in: .whitespaces)
            guard !digits.isEmpty, digits.count <= 2, let number = Int(digits), !name.isEmpty else {
                skipped += 1
                continue
            }
            byNumber[number] = String(name.prefix(maximumNameLength))
        }
        let players = byNumber.map { Player(number: $0.key, name: $0.value) }.sorted { $0.number < $1.number }
        return ParseResult(players: players, skippedLines: skipped)
    }

    /// 붙여넣기 결과 안내용: 새 번호 수와 이름이 바뀐 번호 수 (같은 이름은 세지 않는다)
    public static func pasteCounts(existing: [Player], pasted: [Player]) -> (added: Int, renamed: Int) {
        let names = Dictionary(existing.map { ($0.number, $0.name) }, uniquingKeysWith: { first, _ in first })
        var added = 0, renamed = 0
        for player in pasted {
            if let name = names[player.number] {
                if name != player.name { renamed += 1 }
            } else {
                added += 1
            }
        }
        return (added, renamed)
    }

    /// 저장 전에 확인할 문제 (없으면 빈 목록)
    public static func validate(_ players: [Player]) -> [RosterIssue] {
        var issues: [RosterIssue] = []
        if players.count > maximumPlayers { issues.append(.tooManyPlayers) }
        var seen = Set<Int>()
        for player in players {
            if !numberRange.contains(player.number) { issues.append(.numberOutOfRange(player.number)) }
            if !seen.insert(player.number).inserted, !issues.contains(.duplicateNumber(player.number)) {
                issues.append(.duplicateNumber(player.number))
            }
            let name = player.name.trimmingCharacters(in: .whitespacesAndNewlines)
            if name.isEmpty {
                issues.append(.emptyName(player.number))
            } else if name.count > maximumNameLength {
                issues.append(.nameTooLong(player.number))
            }
        }
        return issues
    }

    /// 이름 앞뒤 공백을 지우고 번호순으로
    public static func normalized(_ players: [Player]) -> [Player] {
        players
            .map { Player(number: $0.number, name: $0.name.trimmingCharacters(in: .whitespacesAndNewlines)) }
            .sorted { $0.number < $1.number }
    }
}

import Foundation

/// 저장된 구장. 네 모서리만 담는다(다듬은 경기장은 읽을 때 다시 계산).
struct StoredPitch: Codable, Equatable {
    var id: UUID
    var corners: [GeoPoint]
    var createdAt: Double
}

/// 경기와 구장의 연결. pitch가 nil이면 "이 경기에서 지움"이다(자동 판정도 하지 않는다).
struct PitchLink: Codable, Equatable {
    var pitch: UUID?
}

struct PitchFile: Codable, Equatable {
    var pitches: [StoredPitch]
    /// 경기 id → 연결. 노트처럼 JSON에서는 "키, 값, 키, 값" 배열이다
    var links: [UUID: PitchLink]
}

private let matchRatio = 0.5
private let matchMargin = 15.0
private let samePitchDistance = 30.0
private let metersPerDegree = 111_320.0

private func meters(_ a: GeoPoint, _ b: GeoPoint) -> Double {
    let north = (b.latitude - a.latitude) * metersPerDegree
    let east = (b.longitude - a.longitude) * metersPerDegree * cos((a.latitude + b.latitude) / 2 * .pi / 180)
    return (north * north + east * east).squareRoot()
}

private extension PitchFile {
    /// 점의 matchRatio 이상이 경기장(+matchMargin) 안이면 그 비율, 아니면 nil
    static func ratioInside(_ pitch: Pitch, _ points: [GeoPoint]) -> Double? {
        if points.isEmpty { return nil }
        let ratio = Double(points.filter { pitch.contains($0, margin: matchMargin) }.count) / Double(points.count)
        return ratio >= matchRatio ? ratio : nil
    }

    /// 비율이 가장 높은 구장. 같으면 먼저 저장된 것
    func bestMatch(_ points: [GeoPoint]) -> (stored: StoredPitch, pitch: Pitch)? {
        var best: (stored: StoredPitch, pitch: Pitch, ratio: Double)?
        for stored in pitches {
            guard let pitch = Pitch.fromCorners(stored.corners), let ratio = Self.ratioInside(pitch, points) else { continue }
            if best == nil || ratio > best!.ratio { best = (stored, pitch, ratio) }
        }
        return best.map { ($0.stored, $0.pitch) }
    }
}

extension MatchLibrary {
    static let pitchesFileName = "pitches.json"

    private var pitchesURL: URL { directory.appendingPathComponent(Self.pitchesFileName) }

    /// pitches.json 전체. 파일이 없거나 깨졌으면 nil
    func readPitches() -> PitchFile? {
        guard let data = try? Data(contentsOf: pitchesURL) else { return nil }
        return try? JSONDecoder().decode(PitchFile.self, from: data)
    }

    func writePitches(_ file: PitchFile) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try JSONEncoder().encode(file).write(to: pitchesURL, options: .atomic)
    }

    /// 이 경기의 경기장. 연결된 구장 → 지운 표시면 nil → 이동 경로 점(points)이 겹치는 저장 구장 순서.
    /// 파일에 쓰지 않는다.
    public func pitch(matchID: UUID, points: [GeoPoint]) -> Pitch? {
        guard let file = readPitches() else { return nil }
        if let link = file.links[matchID] {
            guard let id = link.pitch else { return nil }
            if let stored = file.pitches.first(where: { $0.id == id }), let pitch = Pitch.fromCorners(stored.corners) {
                return pitch
            }
        }
        return file.bestMatch(points)?.pitch
    }

    /// 다시 맞춘 경기장이 기존 구장을 고친 것인지. 중심이 30 m 이내이고 서로의 중심이 상대 경기장 안이어야 한다
    /// (옆 구장처럼 많이 어긋나면 새 구장)
    static func isSamePitch(_ existing: Pitch, _ refit: Pitch) -> Bool {
        meters(existing.center, refit.center) <= samePitchDistance
            && existing.contains(refit.center, margin: 0)
            && refit.contains(existing.center, margin: 0)
    }

    /// 찍은 모서리를 다듬어 이 경기에 연결한다. 다듬어지지 않으면 nil이고 저장하지 않는다.
    /// 기존 구장(이 경기에 연결된 것, 없으면 새 경기장과 겹쳐 자동 판정되는 것)과 같은 구장이면(isSamePitch)
    /// 그 구장을 고치고(같은 구장을 쓰는 다른 경기도 따라 바뀐다), 아니면 새 구장으로 저장해 이 경기만 연결한다.
    /// 파일이 깨졌으면 새로 시작한다.
    @discardableResult
    public func savePitch(matchID: UUID, corners: [GeoPoint], now: Double) throws -> Pitch? {
        guard let pitch = Pitch.fromCorners(corners) else { return nil }
        let file = readPitches() ?? PitchFile(pitches: [], links: [:])
        let linked = file.links[matchID]?.pitch.flatMap { id in file.pitches.first { $0.id == id } }
        let existing = linked ?? file.bestMatch(pitch.corners)?.stored
        var pitches = file.pitches
        let id: UUID
        if let existing, let existingPitch = Pitch.fromCorners(existing.corners),
           Self.isSamePitch(existingPitch, pitch),
           let index = pitches.firstIndex(of: existing) {
            id = existing.id
            pitches[index].corners = pitch.corners
        } else {
            id = UUID()
            pitches.append(StoredPitch(id: id, corners: pitch.corners, createdAt: now))
        }
        var links = file.links
        links[matchID] = PitchLink(pitch: id)
        try writePitches(PitchFile(pitches: pitches, links: links))
        return pitch
    }

    /// 이 경기에서 구장을 지운다. 구장 자체는 남고, 이 경기는 자동 판정도 하지 않는다
    public func clearPitch(matchID: UUID) throws {
        let file = readPitches() ?? PitchFile(pitches: [], links: [:])
        var links = file.links
        links[matchID] = PitchLink(pitch: nil)
        try writePitches(PitchFile(pitches: file.pitches, links: links))
    }
}

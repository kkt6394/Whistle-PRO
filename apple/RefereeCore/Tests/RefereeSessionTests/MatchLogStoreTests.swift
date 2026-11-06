import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct MatchLogStoreTests {
    private func record(createdAt: Double = 0) -> MatchRecord {
        MatchRecord(id: UUID(), createdAt: createdAt, setup: MatchSetup(),
                    commands: [Command(id: "c0", timestamp: 1, kind: .startPeriod)])
    }

    @Test func setupDefaultsAreFifaStandard() {
        let setup = MatchSetup()
        #expect(setup.rules == .fifaStandard)
        #expect(setup.warningBeforeEnd == 300)
        #expect(setup.breakWarningBeforeEnd == 300)
        #expect(setup.clockDisplay == .elapsed)
    }

    @Test func noActiveMatchInEmptyDirectory() throws {
        #expect(try FileMatchLogStore(directory: tempDirectory()).loadActive() == nil)
    }

    @Test func activeRecordRoundTrips() throws {
        let store = FileMatchLogStore(directory: tempDirectory())
        let r = record()
        try store.saveActive(r)
        #expect(try store.loadActive() == r)
    }

    @Test func finishActiveMovesRecordToArchive() throws {
        let store = FileMatchLogStore(directory: tempDirectory())
        let older = record(createdAt: 10)
        let newer = record(createdAt: 20)
        try store.saveActive(newer)
        try store.finishActive(newer)
        try store.finishActive(older)
        #expect(try store.loadActive() == nil)
        #expect(try store.archived() == [older, newer])
    }

    @Test func corruptActiveIsQuarantined() throws {
        let dir = tempDirectory()
        let store = FileMatchLogStore(directory: dir)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try Data("{ not json".utf8).write(to: dir.appendingPathComponent("active.json"))

        #expect(try store.loadActive() == nil)
        let names = try FileManager.default.contentsOfDirectory(atPath: dir.path)
        #expect(!names.contains("active.json"))
        #expect(names.contains { $0.hasPrefix("active-corrupt-") })
    }

    // 워치 "지난 경기": 끝난 경기는 폰 전송과 별개로 최근 10경기까지 남는다

    @Test func finishedMatchesAppearInHistoryNewestFirst() throws {
        let store = FileMatchLogStore(directory: tempDirectory())
        let older = record(createdAt: 10)
        let newer = record(createdAt: 20)
        try store.finishActive(older)
        try store.finishActive(newer)
        #expect(try store.history() == [newer, older])
    }

    @Test func historySurvivesPhoneAcknowledgement() throws {
        let store = FileMatchLogStore(directory: tempDirectory())
        let r = record()
        try store.finishActive(r)
        try store.deleteArchived(r.id)
        #expect(try store.archived() == [])
        #expect(try store.history() == [r])
    }

    @Test func historyKeepsOnlyTheMostRecentMatches() throws {
        let store = FileMatchLogStore(directory: tempDirectory())
        let records = (0..<(FileMatchLogStore.historyLimit + 2)).map { record(createdAt: Double($0)) }
        for r in records { try store.finishActive(r) }
        let history = try store.history()
        #expect(history.count == FileMatchLogStore.historyLimit)
        #expect(history.first == records.last)
        #expect(!history.contains(records[0]) && !history.contains(records[1]))
    }

    /// 지난 경기 리뷰: 워치 목록에서 지울 수 있다. 폰으로 보낼 보관함은 그대로 둔다
    @Test func deleteHistoryRemovesOnlyThatMatch() throws {
        let store = FileMatchLogStore(directory: tempDirectory())
        let keep = record(createdAt: 10)
        let gone = record(createdAt: 20)
        try store.finishActive(keep)
        try store.finishActive(gone)
        try store.deleteHistory(gone.id)
        #expect(try store.history() == [keep])
        #expect(try store.archived() == [keep, gone])
        // 없는 경기를 지워도 오류가 아니다
        try store.deleteHistory(UUID())
    }

    @Test func emptyHistoryWithoutFinishedMatches() throws {
        #expect(try FileMatchLogStore(directory: tempDirectory()).history() == [])
    }

    /// 무료 경기는 이벤트가 없고, 지난 경기 조회는 유료 기능이라 남기지 않는다
    @Test func freeMatchesAreNotKeptInHistory() throws {
        let store = FileMatchLogStore(directory: tempDirectory())
        var free = record()
        free.unlocked = false
        try store.finishActive(free)
        #expect(try store.archived() == [free])
        #expect(try store.history() == [])
    }

    /// 시작 시각이 같아도 방금 끝낸 경기는 정리되지 않는다
    @Test func justFinishedMatchSurvivesPruningOnTies() throws {
        let store = FileMatchLogStore(directory: tempDirectory())
        let records = (0..<(FileMatchLogStore.historyLimit + 3)).map { _ in record(createdAt: 5) }
        for r in records {
            try store.finishActive(r)
            #expect(try store.history().contains(r))
        }
        #expect(try store.history().count == FileMatchLogStore.historyLimit)
    }

    @Test func unreadableHistoryFilesAreRemoved() throws {
        let dir = tempDirectory()
        let store = FileMatchLogStore(directory: dir)
        let history = dir.appendingPathComponent("history", isDirectory: true)
        try FileManager.default.createDirectory(at: history, withIntermediateDirectories: true)
        try Data("{ not json".utf8).write(to: history.appendingPathComponent("broken.json"))
        try store.finishActive(record())
        #expect(try store.history().count == 1)
        #expect(!FileManager.default.fileExists(atPath: history.appendingPathComponent("broken.json").path))
    }

    @Test func historyFailureDoesNotBlockArchiving() throws {
        let dir = tempDirectory()
        let store = FileMatchLogStore(directory: dir)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        // history 자리에 일반 파일이 있어 폴더를 만들 수 없다
        try Data().write(to: dir.appendingPathComponent("history"))
        let r = record()
        try store.finishActive(r)
        #expect(try store.archived() == [r])
    }

    /// 받았는지 물을 경기 ID는 기록을 디코딩하지 않고 finished/ 파일 이름에서 뽑는다 (운동 기록·이름이 다른 파일은 빼고)
    @Test func archivedIDsComeFromFileNames() throws {
        let dir = tempDirectory()
        let store = FileMatchLogStore(directory: dir)
        let a = record(createdAt: 10), b = record(createdAt: 20)
        try store.finishActive(a)
        try store.saveActiveWorkout(WorkoutLog(matchID: b.id))
        try store.saveActive(b)
        try store.finishActive(b)
        let finished = dir.appendingPathComponent("finished", isDirectory: true)
        try Data("x".utf8).write(to: finished.appendingPathComponent("notes.json"))
        #expect(Set(try store.archivedIDs()) == [a.id, b.id])
        try store.deleteArchived(a.id)
        #expect(try store.archivedIDs() == [b.id])
    }

    @Test func noArchivedIDsInEmptyDirectory() throws {
        #expect(try FileMatchLogStore(directory: tempDirectory()).archivedIDs() == [])
    }

    /// [킥오프 취소]: 진행 중 경기·운동 파일을 지우고 보관함·지난 경기에는 아무것도 남기지 않는다 (2026-11-06, 안드로이드와 같은 사례)
    @Test func discardActiveRemovesActiveAndWorkoutFiles() throws {
        let dir = tempDirectory()
        let store = FileMatchLogStore(directory: dir)
        let r = record()
        try store.saveActive(r)
        try store.saveActiveWorkout(WorkoutLog(matchID: r.id))
        try store.discardActive()
        #expect(try store.loadActive() == nil)
        #expect(try store.loadActiveWorkout() == nil)
        #expect(!FileManager.default.fileExists(atPath: dir.appendingPathComponent("active.json").path))
        #expect(!FileManager.default.fileExists(atPath: dir.appendingPathComponent("workout-active.json").path))
        #expect(try store.archived().isEmpty)
        #expect(try store.archivedIDs().isEmpty)
        #expect(try store.history().isEmpty)
        // 지울 것이 없어도 실패하지 않는다
        try store.discardActive()
    }

    /// 늦게 끝난 운동 저장이 취소한 경기의 운동 파일을 다시 쓰면 그 경기 것만 지운다 (새 경기의 운동은 남긴다)
    @Test func discardActiveWorkoutOnlyForThatMatch() throws {
        let store = FileMatchLogStore(directory: tempDirectory())
        let cancelled = UUID()
        let next = UUID()
        try store.saveActiveWorkout(WorkoutLog(matchID: next))
        try store.discardActiveWorkout(cancelled)
        #expect(try store.loadActiveWorkout()?.matchID == next)
        try store.saveActiveWorkout(WorkoutLog(matchID: cancelled))
        try store.discardActiveWorkout(cancelled)
        #expect(try store.loadActiveWorkout() == nil)
    }
}

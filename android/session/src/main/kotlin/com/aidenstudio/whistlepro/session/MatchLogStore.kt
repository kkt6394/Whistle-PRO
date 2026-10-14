package com.aidenstudio.whistlepro.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

interface MatchLogStore {
    /** 진행 중인 경기. 없으면 null. */
    fun loadActive(): MatchRecord?
    fun saveActive(record: MatchRecord)
    /** 경기(와 같은 경기의 운동 기록)를 보관함으로 옮기고 진행 중 기록을 지운다. */
    fun finishActive(record: MatchRecord)
    /** 보관된 경기 (오래된 순) */
    fun archived(): List<MatchRecord>

    fun saveActiveWorkout(log: WorkoutLog)
    fun loadActiveWorkout(): WorkoutLog?
    fun archivedWorkout(matchID: UUID): WorkoutLog?
    /** 폰이 받았다고 확인한 경기를 워치 보관함에서 지운다. */
    fun deleteArchived(matchID: UUID)
}

/**
 * 파일 저장소. directory/active.json·workout-active.json 에 진행 중 기록,
 * directory/finished/<id>.json·<id>-workout.json 에 끝난 기록을 둔다. (Swift FileMatchLogStore와 같은 배치)
 */
class FileMatchLogStore(val directory: File) : MatchLogStore {
    private val activeFile get() = File(directory, "active.json")
    private val activeWorkoutFile get() = File(directory, "workout-active.json")
    private val archiveDirectory get() = File(directory, "finished")

    private fun archivedFile(id: UUID) = File(archiveDirectory, "${id.swiftString}.json")
    private fun archivedWorkoutFile(id: UUID) = File(archiveDirectory, "${id.swiftString}-workout.json")

    /** 읽을 수 없는 파일은 지우지 않고 active-corrupt-<uuid>.json 으로 옮긴 뒤 null을 돌려준다. */
    override fun loadActive(): MatchRecord? {
        if (!activeFile.exists()) return null
        val text = activeFile.readText()
        return try {
            MatchRecord.fromJson(parse(text))
        } catch (e: Exception) {
            val quarantine = File(directory, "active-corrupt-${UUID.randomUUID().swiftString}.json")
            try {
                Files.move(activeFile.toPath(), quarantine.toPath())
            } catch (moveError: Exception) {
                // 이동이 안 되면 복사로라도 보관한 뒤 원본을 치운다 (다음 경기가 덮어쓰기 전에)
                Files.copy(activeFile.toPath(), quarantine.toPath())
                activeFile.delete()
            }
            null
        }
    }

    override fun saveActive(record: MatchRecord) {
        directory.mkdirs()
        writeAtomically(activeFile, record.toJson())
    }

    override fun finishActive(record: MatchRecord) {
        archiveDirectory.mkdirs()
        writeAtomically(archivedFile(record.id), record.toJson())
        val log = runCatching { loadActiveWorkout() }.getOrNull()
        if (log != null && log.matchID == record.id) writeAtomically(archivedWorkoutFile(record.id), log.toJson())
        for (active in listOf(activeFile, activeWorkoutFile)) {
            if (active.exists()) Files.delete(active.toPath())
        }
    }

    override fun archived(): List<MatchRecord> {
        val files = archiveDirectory.listFiles() ?: return emptyList()
        return files
            .filter { it.extension == "json" && !it.name.endsWith("-workout.json") }
            .mapNotNull { runCatching { MatchRecord.fromJson(parse(it.readText())) }.getOrNull() }
            .sortedBy { it.createdAt }
    }

    override fun saveActiveWorkout(log: WorkoutLog) {
        directory.mkdirs()
        writeAtomically(activeWorkoutFile, log.toJson())
    }

    /** 읽을 수 없으면 null (운동 기록은 경기 기록보다 덜 중요해서 격리하지 않는다) */
    override fun loadActiveWorkout(): WorkoutLog? {
        if (!activeWorkoutFile.exists()) return null
        return runCatching { WorkoutLog.fromJson(parse(activeWorkoutFile.readText())) }.getOrNull()
    }

    override fun archivedWorkout(matchID: UUID): WorkoutLog? {
        val file = archivedWorkoutFile(matchID)
        if (!file.exists()) return null
        return WorkoutLog.fromJson(parse(file.readText()))
    }

    override fun deleteArchived(matchID: UUID) {
        for (file in listOf(archivedFile(matchID), archivedWorkoutFile(matchID))) {
            if (file.exists()) Files.delete(file.toPath())
        }
    }

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    /** 임시 파일에 쓰고 원자적으로 바꾼다 (Swift .atomic과 같음) */
    private fun writeAtomically(file: File, json: JsonObject) {
        val temp = File(file.parentFile, ".${file.name}.${UUID.randomUUID()}.tmp")
        temp.writeText(json.toString())
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }
}

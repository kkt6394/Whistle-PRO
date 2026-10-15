package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.array
import com.aidenstudio.whistlepro.core.double
import com.aidenstudio.whistlepro.core.objects
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

data class WorkoutSample(
    /** Unix epoch 초 */
    val t: Double,
    val value: Double,
) {
    fun toJson() = buildJsonObject {
        put("t", JsonPrimitive(t))
        put("value", JsonPrimitive(value))
    }

    companion object {
        fun fromJson(json: JsonObject) = WorkoutSample(json.double("t"), json.double("value"))
    }
}

data class RoutePoint(
    val t: Double,
    val latitude: Double,
    val longitude: Double,
    /** m/s (음수면 측정 불가) */
    val speed: Double,
    /** 수평 정확도 m (음수면 무효) */
    val accuracy: Double,
) {
    fun toJson() = buildJsonObject {
        put("t", JsonPrimitive(t))
        put("latitude", JsonPrimitive(latitude))
        put("longitude", JsonPrimitive(longitude))
        put("speed", JsonPrimitive(speed))
        put("accuracy", JsonPrimitive(accuracy))
    }

    companion object {
        fun fromJson(json: JsonObject) = RoutePoint(
            json.double("t"), json.double("latitude"), json.double("longitude"), json.double("speed"), json.double("accuracy"),
        )
    }
}

/** 한 경기 동안 수집한 운동 데이터. 거리·칼로리는 운동 시작부터의 누적값이다. */
data class WorkoutLog(
    val matchID: UUID,
    /** bpm */
    val heartRate: List<WorkoutSample> = emptyList(),
    /** 누적 m */
    val distance: List<WorkoutSample> = emptyList(),
    /** 누적 kcal */
    val energy: List<WorkoutSample> = emptyList(),
    val route: List<RoutePoint> = emptyList(),
    /** 운동 세션을 새로 시작한 시각들 (경기 첫 시작 제외). Swift WorkoutLog.restarts와 같다.
     *  null은 키가 없던 예전 기록 — 값이 줄면 재시작으로 본다 */
    val restarts: List<Double>? = emptyList(),
) {
    fun toJson() = buildJsonObject {
        put("matchID", JsonPrimitive(matchID.swiftString))
        put("heartRate", JsonArray(heartRate.map { it.toJson() }))
        put("distance", JsonArray(distance.map { it.toJson() }))
        put("energy", JsonArray(energy.map { it.toJson() }))
        put("route", JsonArray(route.map { it.toJson() }))
        // 새 기록은 빈 배열도 쓴다 (Swift와 같다). 예전 기록(null)은 키 없이
        restarts?.let { put("restarts", JsonArray(it.map(::JsonPrimitive))) }
    }

    companion object {
        fun fromJson(json: JsonObject) = WorkoutLog(
            matchID = json.uuid("matchID"),
            heartRate = json.array("heartRate").objects("heartRate").map(WorkoutSample::fromJson),
            distance = json.array("distance").objects("distance").map(WorkoutSample::fromJson),
            energy = json.array("energy").objects("energy").map(WorkoutSample::fromJson),
            route = json.array("route").objects("route").map(RoutePoint::fromJson),
            restarts = (json["restarts"] as? JsonArray)?.map { it.jsonPrimitive.double },
        )
    }
}

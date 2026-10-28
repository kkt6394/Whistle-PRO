package com.aidenstudio.whistlepro.session

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 지도 위 한 점. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** 경기장 좌표(m). x는 왼쪽 터치라인에서 오른쪽으로(0…width), y는 위쪽 골라인(A)에서 아래로(0…length). */
data class PitchPoint(val x: Double, val y: Double)

/**
 * 직사각형 경기장. bearing은 골라인 A→B 방향의 방위각(도, 북=0, 시계 방향, 0..<360).
 * 경기장 크기라 중심 기준 평면 근사를 쓴다(WorkoutAnalysis.distance와 같다).
 */
data class Pitch(val center: GeoPoint, val length: Double, val width: Double, val bearing: Double) {
    private val metersPerDegreeLongitude get() = METERS_PER_DEGREE * cos(center.latitude * PI / 180)
    private val directionNorth get() = cos(bearing * PI / 180)
    private val directionEast get() = sin(bearing * PI / 180)

    /** A왼쪽, A오른쪽, B오른쪽, B왼쪽 순서. */
    val corners: List<GeoPoint>
        get() = listOf(
            geo(PitchPoint(0.0, 0.0)),
            geo(PitchPoint(width, 0.0)),
            geo(PitchPoint(width, length)),
            geo(PitchPoint(0.0, length)),
        )

    fun local(p: GeoPoint): PitchPoint {
        val north = (p.latitude - center.latitude) * METERS_PER_DEGREE
        val east = (p.longitude - center.longitude) * metersPerDegreeLongitude
        // 오른쪽 = 진행 방향을 시계 방향으로 90도 돌린 쪽
        val along = north * directionNorth + east * directionEast
        val across = -north * directionEast + east * directionNorth
        return PitchPoint(across + width / 2, along + length / 2)
    }

    fun geo(p: PitchPoint): GeoPoint {
        val across = p.x - width / 2
        val along = p.y - length / 2
        val north = along * directionNorth - across * directionEast
        val east = along * directionEast + across * directionNorth
        return GeoPoint(
            center.latitude + north / METERS_PER_DEGREE,
            center.longitude + east / metersPerDegreeLongitude,
        )
    }

    /** 경기장에서 margin(m) 안쪽까지 포함해 안에 있는지. */
    fun contains(p: GeoPoint, margin: Double): Boolean {
        val l = local(p)
        return l.x >= -margin && l.x <= width + margin && l.y >= -margin && l.y <= length + margin
    }

    companion object {
        private const val METERS_PER_DEGREE = 111_320.0
        private const val MINIMUM_AREA = 100.0

        /**
         * 찍은 네 모서리를 직사각형으로 다듬는다. 순서는 상관없다.
         * 네 개가 아니거나 넓이가 100㎡ 미만이면 null. 첫 번째 점 쪽 골라인이 A.
         */
        fun fromCorners(corners: List<GeoPoint>): Pitch? {
            if (corners.size != 4) return null
            val centroid = GeoPoint(corners.map { it.latitude }.average(), corners.map { it.longitude }.average())
            val metersPerDegreeLongitude = METERS_PER_DEGREE * cos(centroid.latitude * PI / 180)
            // 무게중심 기준 (북, 동) m
            val pts = corners.map {
                doubleArrayOf(
                    (it.latitude - centroid.latitude) * METERS_PER_DEGREE,
                    (it.longitude - centroid.longitude) * metersPerDegreeLongitude,
                )
            }
            val first = pts[0]
            // 각도순 정렬로 꼬인 순서를 푼다
            val ring = pts.sortedBy { atan2(it[1], it[0]) }
            fun side(i: Int) = doubleArrayOf(ring[(i + 1) % 4][0] - ring[i][0], ring[(i + 1) % 4][1] - ring[i][1])
            fun size(v: DoubleArray) = sqrt(v[0] * v[0] + v[1] * v[1])
            val pairA = (size(side(0)) + size(side(2))) / 2
            val pairB = (size(side(1)) + size(side(3))) / 2
            // 긴 쪽이 터치라인. 마주 보는 두 변은 반대 방향이라 한쪽을 뒤집어 평균한다
            val touch = if (pairA >= pairB) 0 else 1
            val length = max(pairA, pairB)
            val width = min(pairA, pairB)
            // 넓이는 꼭짓점 공식으로. 한 줄로 늘어선 점은 0이 된다
            val area = abs((0 until 4).sumOf { ring[it][0] * ring[(it + 1) % 4][1] - ring[(it + 1) % 4][0] * ring[it][1] }) / 2
            if (area < MINIMUM_AREA) return null
            val s1 = side(touch)
            val s2 = side(touch + 2)
            var north = (s1[0] - s2[0]) / 2
            var east = (s1[1] - s2[1]) / 2
            val norm = sqrt(north * north + east * east)
            if (norm == 0.0) return null
            north /= norm
            east /= norm
            // 첫 점이 중심보다 진행 방향 앞쪽이면 A가 반대편이므로 방향을 뒤집는다
            if (first[0] * north + first[1] * east > 0) {
                north = -north
                east = -east
            }
            val bearing = (atan2(east, north) * 180 / PI + 360) % 360
            return Pitch(centroid, length, width, bearing)
        }
    }
}

/** 라인 치수(m). 정규 치수를 작은 경기장에서는 줄인다. */
data class PitchMarkings(val scale: Double) {
    val centerCircleRadius get() = 9.15 * scale
    val penaltyAreaWidth get() = 40.32 * scale
    val penaltyAreaDepth get() = 16.5 * scale
    val goalAreaWidth get() = 18.32 * scale
    val goalAreaDepth get() = 5.5 * scale
    val penaltyMarkDistance get() = 11.0 * scale

    companion object {
        fun of(length: Double, width: Double): PitchMarkings {
            val scale = min(1.0, min((width - 4) / 40.32, (length / 2 - 1) / (16.5 + 9.15)))
            return PitchMarkings(max(0.2, scale))
        }
    }
}

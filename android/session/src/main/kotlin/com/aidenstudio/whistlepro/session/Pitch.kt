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
    /**
     * 위아래 뒤집기: 같은 자리·크기에 A·B 골라인만 바꾼 경기장 (방향 +180°).
     * 그림 경기장과 리플레이는 골라인 기준으로 위아래를 두니, 사용자가 보기에 거꾸로면 이걸로 돌린다
     */
    fun flipped(): Pitch = copy(bearing = (bearing + 180) % 360)

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
    val goalWidth get() = 7.32 * scale
    val goalDepth get() = 2.0 * scale

    companion object {
        fun of(length: Double, width: Double): PitchMarkings {
            val scale = min(1.0, min((width - 4) / 40.32, (length / 2 - 1) / (16.5 + 9.15)))
            return PitchMarkings(max(0.2, scale))
        }

        /**
         * 지도에 그릴 라인 (경기장 좌표, 테두리 제외): 하프라인, 센터서클, 그리고 양 끝(y 0, length)마다
         * 페널티 에어리어·골 에어리어·골대(골라인 바깥). 골대 방향이 보이게 하려는 것이다
         */
        fun lines(length: Double, width: Double): List<List<PitchPoint>> {
            val marks = of(length, width)
            val middle = width / 2
            val half = listOf(PitchPoint(0.0, length / 2), PitchPoint(width, length / 2))
            val circle = (0..36).map { i ->
                val angle = (i % 36) / 36.0 * 2 * PI
                PitchPoint(middle + marks.centerCircleRadius * cos(angle), length / 2 + marks.centerCircleRadius * sin(angle))
            }

            /** 골라인 위 가운데에서 depth만큼 (inward 방향으로) 나간 열린 사각형 */
            fun box(goalLine: Double, inward: Double, boxWidth: Double, depth: Double): List<PitchPoint> {
                val left = middle - boxWidth / 2
                val right = middle + boxWidth / 2
                val inner = goalLine + inward * depth
                return listOf(PitchPoint(left, goalLine), PitchPoint(left, inner), PitchPoint(right, inner), PitchPoint(right, goalLine))
            }
            val ends = listOf(0.0 to 1.0, length to -1.0).flatMap { (goalLine, inward) ->
                listOf(
                    box(goalLine, inward, marks.penaltyAreaWidth, marks.penaltyAreaDepth),
                    box(goalLine, inward, marks.goalAreaWidth, marks.goalAreaDepth),
                    box(goalLine, -inward, marks.goalWidth, marks.goalDepth),
                )
            }
            return listOf(half, circle) + ends
        }
    }
}

/** 심판의 이동 경로(기간 중·정확도 20 m 이하 지점)로 경기장을 추정한다. */
object PitchEstimate {
    private const val METERS_PER_DEGREE = 111_320.0
    private const val MINIMUM_POINTS = 10
    private const val MINIMUM_WIDTH = 30.0

    fun of(points: List<GeoPoint>): Pitch? {
        if (points.size < MINIMUM_POINTS) return null
        val centerLat = median(points.map { it.latitude })
        val centerLon = median(points.map { it.longitude })
        val metersPerDegreeLongitude = METERS_PER_DEGREE * cos(centerLat * PI / 180)
        // 중앙값 중심 기준 (북, 동) m
        val local = points.map {
            doubleArrayOf((it.latitude - centerLat) * METERS_PER_DEGREE, (it.longitude - centerLon) * metersPerDegreeLongitude)
        }
        // 중심에서 95번째 백분위 거리의 1.2배를 넘는 점은 튀는 점으로 보고 버린다(그대로 자르면 긴 띠의 끝이 깎인다)
        val distances = local.map { sqrt(it[0] * it[0] + it[1] * it[1]) }
        val limit = distances.sorted()[((distances.size - 1) * 0.95).toInt()] * 1.2
        val kept = local.filterIndexed { i, _ -> distances[i] <= limit }

        // 0°…179°를 1°씩 돌려 가며 넓이가 가장 작은 축 정렬 사각형을 찾는다(같으면 작은 각도)
        var bestAngle = 0
        var bestArea = Double.MAX_VALUE
        var bestRange = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        for (angle in 0 until 180) {
            val n = cos(angle * PI / 180)
            val e = sin(angle * PI / 180)
            var minAlong = Double.MAX_VALUE
            var maxAlong = -Double.MAX_VALUE
            var minAcross = Double.MAX_VALUE
            var maxAcross = -Double.MAX_VALUE
            for (p in kept) {
                val along = p[0] * n + p[1] * e
                val across = -p[0] * e + p[1] * n
                minAlong = min(minAlong, along)
                maxAlong = max(maxAlong, along)
                minAcross = min(minAcross, across)
                maxAcross = max(maxAcross, across)
            }
            val area = (maxAlong - minAlong) * (maxAcross - minAcross)
            if (area < bestArea) {
                bestArea = area
                bestAngle = angle
                bestRange = doubleArrayOf(minAlong, maxAlong, minAcross, maxAcross)
            }
        }
        val alongSize = bestRange[1] - bestRange[0]
        val acrossSize = bestRange[3] - bestRange[2]
        // 긴 쪽이 length. 가로가 더 길면 방향을 90° 돌린다(A는 늘 0..<180 쪽)
        val alongIsLength = alongSize >= acrossSize
        val bearing = if (alongIsLength) bestAngle.toDouble() else ((bestAngle + 90) % 180).toDouble()
        val length = max(alongSize, acrossSize)
        val width = max(MINIMUM_WIDTH, min(alongSize, acrossSize))

        // 범위의 한가운데를 중심으로 삼아 위도·경도로 되돌린다
        val midAlong = (bestRange[0] + bestRange[1]) / 2
        val midAcross = (bestRange[2] + bestRange[3]) / 2
        val n = cos(bestAngle * PI / 180)
        val e = sin(bestAngle * PI / 180)
        val north = midAlong * n - midAcross * e
        val east = midAlong * e + midAcross * n
        val center = GeoPoint(centerLat + north / METERS_PER_DEGREE, centerLon + east / metersPerDegreeLongitude)
        return Pitch(center, length, width, bearing)
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}

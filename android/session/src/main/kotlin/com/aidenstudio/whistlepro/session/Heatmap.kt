package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.wholeSeconds
import kotlin.math.cos
import kotlin.math.max

/** 히트맵 사각형 한 칸 (위경도 범위) */
data class HeatCell(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double,
    val count: Int,
    /** 가장 많이 머문 칸이 1 */
    val intensity: Double,
)

object Heatmap {
    private const val METERS_PER_DEGREE = 111_320.0

    /** 기간이 진행된 동안의 지점만 고른다: 하프타임·경기 전후에 걸은 길은 칠하지 않는다 (2026-10-28 리뷰) */
    fun duringPeriods(route: List<RoutePoint>, state: MatchState, now: Double): List<RoutePoint> {
        val ranges = state.periods.map { it.startedAt to (it.endedAt ?: now) }
        return route.filter { p -> ranges.any { p.t >= it.first && p.t <= it.second } }
    }

    /** GPS 지점을 cellMeters 격자로 묶는다. 정확도가 나쁜(20m 초과·음수) 지점은 버린다. */
    fun cells(route: List<RoutePoint>, cellMeters: Double = 4.0): List<HeatCell> {
        val points = WorkoutAnalysis.usableRoute(route)
        val reference = points.firstOrNull() ?: return emptyList()
        val latitudeStep = cellMeters / METERS_PER_DEGREE
        val longitudeStep = cellMeters / (METERS_PER_DEGREE * cos(reference.latitude * Math.PI / 180))

        val counts = LinkedHashMap<Pair<Long, Long>, Int>()
        for (p in points) {
            // 손상된 좌표로 트랩하지 않게 제한해서 변환한다
            val key = wholeSeconds(p.latitude / latitudeStep) to wholeSeconds(p.longitude / longitudeStep)
            counts[key] = (counts[key] ?: 0) + 1
        }
        val maximum = (counts.values.maxOrNull() ?: 1).toDouble()
        return counts.map { (key, count) ->
            val (row, column) = key
            HeatCell(
                minLatitude = row * latitudeStep,
                minLongitude = column * longitudeStep,
                maxLatitude = (row + 1) * latitudeStep,
                maxLongitude = (column + 1) * longitudeStep,
                count = count,
                intensity = count / maximum,
            )
        }
    }

    fun bounds(cells: List<HeatCell>, pinCoordinates: List<Pair<Double, Double>> = emptyList()): HeatmapBounds? {
        if (cells.isEmpty()) return null
        val latitudes = cells.flatMap { listOf(it.minLatitude, it.maxLatitude) } + pinCoordinates.map { it.first }
        val longitudes = cells.flatMap { listOf(it.minLongitude, it.maxLongitude) } + pinCoordinates.map { it.second }
        val minLat = latitudes.min(); val maxLat = latitudes.max()
        val minLon = longitudes.min(); val maxLon = longitudes.max()
        val centerLatitude = (minLat + maxLat) / 2
        return HeatmapBounds(
            centerLatitude = centerLatitude,
            centerLongitude = (minLon + maxLon) / 2,
            northSouthMeters = (maxLat - minLat) * METERS_PER_DEGREE,
            eastWestMeters = (maxLon - minLon) * METERS_PER_DEGREE * cos(centerLatitude * Math.PI / 180),
        )
    }
}

data class ChartPoint(
    /** 첫 킥오프부터 지난 분 */
    val minute: Double,
    val value: Double,
    /** 몇 번째 기간의 점인지 (그래프가 기간마다 따로 선을 그려 휴식 구간을 잇지 않게) */
    val period: Int = 0,
) {
    companion object {
        /**
         * 상세 심박 선 그래프용: 같은 기간 안에서 `minutes` 칸마다 평균 한 점으로 줄인다.
         * 5초마다 찍힌 점을 그대로 이으면 선이 빽빽한 막대처럼 보였다. 기간이 바뀌면 칸을 새로 시작한다 (휴식 구간을 잇지 않게)
         */
        fun bucketed(points: List<ChartPoint>, minutes: Double): List<ChartPoint> {
            val result = mutableListOf<ChartPoint>()
            val bucket = mutableListOf<ChartPoint>()
            fun flush() {
                if (bucket.isEmpty()) return
                val n = bucket.size.toDouble()
                result += ChartPoint(bucket.sumOf { it.minute } / n, bucket.sumOf { it.value } / n, bucket[0].period)
                bucket.clear()
            }
            for (point in points) {
                val first = bucket.firstOrNull()
                if (first != null &&
                    (first.period != point.period || Math.floor(point.minute / minutes) != Math.floor(first.minute / minutes))
                ) flush()
                bucket += point
            }
            flush()
            return result
        }
    }
}

/** 기간 진행 중의 심박수만 그래프 점으로 (휴식 제외) */
fun WorkoutAnalysis.heartRateTimeline(log: WorkoutLog, state: MatchState, now: Double): List<ChartPoint> {
    val start = state.periods.firstOrNull()?.startedAt ?: return emptyList()
    val ranges = state.periods.map { it.startedAt to (it.endedAt ?: now) }
    return log.heartRate.mapNotNull { sample ->
        val period = ranges.indexOfFirst { sample.t >= it.first && sample.t <= it.second }
        if (period < 0) null else ChartPoint((sample.t - start) / 60, sample.value, period)
    }
}

/** 히트맵 전체(와 이벤트 위치)가 들어오는 지도 범위. 상세 화면 지도와 SNS 카드 스냅숏이 함께 쓴다. */
data class HeatmapBounds(
    val centerLatitude: Double,
    val centerLongitude: Double,
    val northSouthMeters: Double,
    val eastWestMeters: Double,
) {
    /** 넓은 쪽 길이의 2.5배 높이에서 보면 여백을 두고 다 들어온다. 최소 150m */
    val viewingDistance: Double get() = max(max(northSouthMeters, eastWestMeters) * 2.5, 150.0)

    /** 한국 안이다: SNS 카드·보고서 지도 사진은 일반 지도로, 폰 위성 보기는 확대를 18까지 (Google 한국 위성 사진 한계) */
    val inKorea: Boolean
        get() {
            if (centerLatitude !in 33.0..38.7 || centerLongitude !in 124.5..131.9) return false
            // 상자의 남동쪽 모서리는 일본 (후쿠오카·시모노세키·쓰시마·이키). 부산·거제는 북위 35도 위이거나 동경 128.9도 서쪽 (M7a 리뷰)
            return !(centerLatitude < 35.0 && centerLongitude > 128.9)
        }
}

/**
 * 부드러운 히트맵 격자 (아이폰 `HeatDensity`): 칸마다 머문 양을 둥근 종 모양(가우시안)으로 번지게 더한 값.
 * 네모 칸을 그대로 칠하면 계단처럼 보여서(2026-10-28 사용자 지적) 지도 그림은 이 격자로 그린다.
 * [values]는 줄 단위(첫 줄이 북쪽 끝), 가장 진한 곳이 1
 */
class HeatDensity(
    val width: Int,
    val height: Int,
    val values: DoubleArray,
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
) {
    fun value(x: Int, y: Int): Double = values[y * width + x]
}

/**
 * 칸 중심마다 머문 양(count)을 반경 [radiusMeters](표준편차) 가우시안으로 번지게 더한다 (아이폰 `Heatmap.density`와 같은 계산).
 * 가장자리가 잘리지 않게 반경의 3배만큼 둘레를 넓히고, 한 변이 [maximumSide] 픽셀을 넘지 않게 해상도를 낮춘다.
 * 머문 양은 몇 곳(하프타임 자리 등)만 지나치게 진해지지 않게 제곱근으로 누른다
 */
fun Heatmap.density(
    cells: List<HeatCell>,
    radiusMeters: Double = 5.0,
    metersPerPixel: Double = 0.5,
    maximumSide: Int = 512,
): HeatDensity? {
    if (cells.isEmpty() || radiusMeters <= 0 || metersPerPixel <= 0 || maximumSide <= 1) return null
    val metersPerDegree = 111_320.0
    val centerLatitude = (cells.minOf { it.minLatitude } + cells.maxOf { it.maxLatitude }) / 2
    val longitudeScale = metersPerDegree * max(cos(centerLatitude * Math.PI / 180), 0.01)
    val pad = radiusMeters * 3
    val minLat = cells.minOf { it.minLatitude } - pad / metersPerDegree
    val maxLat = cells.maxOf { it.maxLatitude } + pad / metersPerDegree
    val minLon = cells.minOf { it.minLongitude } - pad / longitudeScale
    val maxLon = cells.maxOf { it.maxLongitude } + pad / longitudeScale
    val northSouth = (maxLat - minLat) * metersPerDegree
    val eastWest = (maxLon - minLon) * longitudeScale
    val step = max(metersPerPixel, max(northSouth, eastWest) / maximumSide)
    val width = kotlin.math.ceil(eastWest / step).toInt().coerceIn(1, maximumSide)
    val height = kotlin.math.ceil(northSouth / step).toInt().coerceIn(1, maximumSide)

    val values = DoubleArray(width * height)
    val reach = kotlin.math.ceil(pad / step).toInt()
    val twoSigmaSquared = 2 * radiusMeters * radiusMeters
    for (cell in cells) {
        val weight = kotlin.math.sqrt(cell.count.toDouble())
        // 픽셀 좌표(가로는 서→동, 세로는 북→남)
        val cx = ((cell.minLongitude + cell.maxLongitude) / 2 - minLon) * longitudeScale / step
        val cy = (maxLat - (cell.minLatitude + cell.maxLatitude) / 2) * metersPerDegree / step
        val x0 = max(0, cx.toInt() - reach); val x1 = kotlin.math.min(width - 1, cx.toInt() + reach)
        val y0 = max(0, cy.toInt() - reach); val y1 = kotlin.math.min(height - 1, cy.toInt() + reach)
        if (x0 > x1 || y0 > y1) continue
        for (y in y0..y1) {
            val dy = (y + 0.5 - cy) * step
            for (x in x0..x1) {
                val dx = (x + 0.5 - cx) * step
                values[y * width + x] += weight * kotlin.math.exp(-(dx * dx + dy * dy) / twoSigmaSquared)
            }
        }
    }
    val peak = values.maxOrNull() ?: return null
    if (peak <= 0) return null
    for (i in values.indices) values[i] /= peak
    return HeatDensity(width, height, values, minLat, maxLat, minLon, maxLon)
}

/**
 * 경기장 좌표 히트 격자: 칸 중심을 [Pitch.local]로 경기장 m 좌표에 옮겨 가우시안으로 번지게 더한다.
 * 격자는 가로 width+2×MARGIN, 세로 length+2×MARGIN(m)이고 첫 줄이 골라인 A 쪽이다.
 * 반환 [HeatDensity]의 위경도 필드에는 경기장 m 범위(`minLatitude`=-MARGIN … `maxLatitude`=length+MARGIN, 경도는 width)를 넣는다.
 * 번지는 반경(5 m)·제곱근 누름·정규화는 `Heatmap.density`와 같다. 터치라인·골라인 밖 MARGIN을 넘은 칸은 버린다
 */
object PitchHeat {
    const val MARGIN = 8.0

    fun density(
        cells: List<HeatCell>,
        pitch: Pitch,
        radiusMeters: Double = 5.0,
        metersPerPixel: Double = 0.5,
        maximumSide: Int = 256,
    ): HeatDensity? {
        if (radiusMeters <= 0 || metersPerPixel <= 0 || maximumSide <= 1) return null
        val spots = cells.mapNotNull { cell ->
            val p = pitch.local(GeoPoint((cell.minLatitude + cell.maxLatitude) / 2, (cell.minLongitude + cell.maxLongitude) / 2))
            if (p.x < -MARGIN || p.x > pitch.width + MARGIN || p.y < -MARGIN || p.y > pitch.length + MARGIN) null
            else Triple(p.x, p.y, kotlin.math.sqrt(cell.count.toDouble()))
        }
        if (spots.isEmpty()) return null
        val eastWest = pitch.width + 2 * MARGIN
        val northSouth = pitch.length + 2 * MARGIN
        val step = max(metersPerPixel, max(northSouth, eastWest) / maximumSide)
        val width = kotlin.math.ceil(eastWest / step).toInt().coerceIn(1, maximumSide)
        val height = kotlin.math.ceil(northSouth / step).toInt().coerceIn(1, maximumSide)

        val values = DoubleArray(width * height)
        val reach = kotlin.math.ceil(radiusMeters * 3 / step).toInt()
        val twoSigmaSquared = 2 * radiusMeters * radiusMeters
        for ((x, y, weight) in spots) {
            val cx = (x + MARGIN) / step
            val cy = (y + MARGIN) / step
            val x0 = max(0, cx.toInt() - reach); val x1 = kotlin.math.min(width - 1, cx.toInt() + reach)
            val y0 = max(0, cy.toInt() - reach); val y1 = kotlin.math.min(height - 1, cy.toInt() + reach)
            if (x0 > x1 || y0 > y1) continue
            for (py in y0..y1) {
                val dy = (py + 0.5 - cy) * step
                for (px in x0..x1) {
                    val dx = (px + 0.5 - cx) * step
                    values[py * width + px] += weight * kotlin.math.exp(-(dx * dx + dy * dy) / twoSigmaSquared)
                }
            }
        }
        val peak = values.maxOrNull() ?: return null
        if (peak <= 0) return null
        for (i in values.indices) values[i] /= peak
        return HeatDensity(width, height, values, -MARGIN, pitch.length + MARGIN, -MARGIN, pitch.width + MARGIN)
    }
}

package com.aidenstudio.whistlepro.session.design

import kotlin.math.max
import kotlin.math.min

/** 화면 세로 배분의 한 요소 (B2 스펙 §2·§3). height는 이 요소의 최소(=기준) 높이다. */
data class FillItem(val height: Double, val role: Role = Role.Fixed) {
    sealed interface Role {
        data object Fixed : Role
        /** 남는 높이를 받는 주인공. 여럿이면 똑같이 나눈다 */
        data object Hero : Role
        /** 주인공이 상한에 닿은 뒤 남는 높이를 받는다 */
        data object Second : Role
        /** 모자라면 order가 작은 것부터 숨긴다 */
        data class Hideable(val order: Int) : Role
    }
}

data class FillResult(
    /** 요소별 높이 (숨긴 요소는 0) */
    val heights: List<Double>,
    val hidden: List<Boolean>,
    /** 주인공·두 번째 요소가 다 받고도 남으면 간격이 커진다 */
    val spacing: Double,
    /** 숨길 줄을 숨겨서라도 모두 들어가는지. false면 화면이 핵심 버튼만 고정하고 나머지를 스크롤한다 */
    val fits: Boolean,
)

/** 남는 높이 배분: 숨길 줄 → 주인공(상한) → 두 번째 → 간격 (B2 스펙 §2) */
object FillLayout {
    /** 화면이 높이를 나눌 때 남겨 두는 여유: 배분한 높이를 그대로 쓰면 반올림으로 넘쳐 스크롤이 생긴다 */
    const val SLACK = 4.0

    fun layout(available: Double, spacing: Double, items: List<FillItem>, heroCap: Double? = 60.0): FillResult {
        val hidden = MutableList(items.size) { false }
        // 높이 0인 고정 항목은 화면이 그리지 않는 조건부 항목이라 간격도 세지 않는다
        fun shown(index: Int) = !hidden[index] && !(items[index].height == 0.0 && items[index].role == FillItem.Role.Fixed)
        fun total(): Double {
            val visible = items.indices.filter(::shown)
            return visible.sumOf { items[it].height } + spacing * max(0, visible.size - 1)
        }
        val hideOrder = items.indices
            .mapNotNull { index -> (items[index].role as? FillItem.Role.Hideable)?.let { it.order to index } }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.second }
        for (index in hideOrder) if (total() > available) hidden[index] = true

        val heights = items.indices.map { if (hidden[it]) 0.0 else items[it].height }.toMutableList()
        val shortfall = available - total()
        val fits = shortfall >= -0.001
        var spare = max(0.0, shortfall)

        // 주인공끼리 똑같이 나누고, 상한에 닿은 주인공의 남은 몫은 아직 덜 찬 주인공에게 다시 나눈다
        var growing = items.indices.filter { !hidden[it] && items[it].role == FillItem.Role.Hero }
        while (growing.isNotEmpty() && spare > 0.001) {
            val each = spare / growing.size
            for (index in growing) {
                val grow = heroCap?.let { min(each, max(0.0, it - heights[index])) } ?: each
                heights[index] += grow
                spare -= grow
            }
            growing = growing.filter { index -> heroCap?.let { heights[index] < it - 0.001 } ?: true }
        }
        val seconds = items.indices.filter { !hidden[it] && items[it].role == FillItem.Role.Second }
        if (seconds.isNotEmpty() && spare > 0) {
            for (index in seconds) heights[index] += spare / seconds.size
            spare = 0.0
        }
        val visibleCount = items.indices.count(::shown)
        val finalSpacing = if (visibleCount > 1) spacing + spare / (visibleCount - 1) else spacing
        return FillResult(heights, hidden, finalSpacing, fits)
    }
}

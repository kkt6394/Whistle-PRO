package com.aidenstudio.whistlepro.session

/** 경기 상세 탭 (디자인 개편 §5.2): 내용이 있는 탭만 */
enum class DetailTab {
    RECORD, WORKOUT, MAP;

    companion object {
        fun available(hasWorkout: Boolean, hasMap: Boolean): List<DetailTab> = buildList {
            add(RECORD)
            if (hasWorkout) add(WORKOUT)
            if (hasMap) add(MAP)
        }
    }
}

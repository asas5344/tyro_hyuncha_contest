package com.team.hyuncha.data

enum class CarType(val label: String) {
    SEDAN("세단"),
    SUV("SUV"),
}

/**
 * 차종 제원. 단위는 모두 m, 도.
 * 시뮬레이터는 뒷바퀴 축 중심을 차의 기준점으로 씀 (자전거 모델 기준).
 */
data class CarSpec(
    val name: String,
    val type: CarType,
    val lengthM: Double,          // 전장
    val widthM: Double,           // 전폭
    val wheelBaseM: Double,       // 휠베이스 (앞뒤 바퀴 축 사이 거리)
    val rearOverhangM: Double,    // 뒷바퀴 축 ~ 뒤 범퍼 거리
    val steeringRatio: Double,    // 조향비: 핸들을 이만큼 돌리면 바퀴가 1도 꺾임
    val maxWheelAngleDeg: Double, // 바퀴 최대 조향각
) {
    /** 핸들을 끝까지 돌렸을 때 각도 (한쪽 방향) */
    val maxSteeringWheelDeg: Double get() = maxWheelAngleDeg * steeringRatio
}

/**
 * 현대차 차종 프리셋.
 * 전장·전폭·휠베이스는 공식 제원표 값.
 * 뒤 오버행·조향비·최대 조향각은 공개 제원(최소 회전반경)에서 거꾸로 계산한 추정치라
 * 실제 값을 찾으면 교체할 것.
 */
object CarPresets {
    val AVANTE = CarSpec(
        name = "아반떼", type = CarType.SEDAN,
        lengthM = 4.710, widthM = 1.825, wheelBaseM = 2.720,
        rearOverhangM = 0.95, steeringRatio = 15.0, maxWheelAngleDeg = 31.0,
    )
    val GRANDEUR = CarSpec(
        name = "그랜저", type = CarType.SEDAN,
        lengthM = 5.035, widthM = 1.880, wheelBaseM = 2.895,
        rearOverhangM = 1.10, steeringRatio = 15.0, maxWheelAngleDeg = 29.0,
    )
    val TUCSON = CarSpec(
        name = "투싼", type = CarType.SUV,
        lengthM = 4.630, widthM = 1.865, wheelBaseM = 2.755,
        rearOverhangM = 0.95, steeringRatio = 15.0, maxWheelAngleDeg = 30.0,
    )
    val SANTA_FE = CarSpec(
        name = "싼타페", type = CarType.SUV,
        lengthM = 4.830, widthM = 1.900, wheelBaseM = 2.815,
        rearOverhangM = 1.05, steeringRatio = 15.0, maxWheelAngleDeg = 29.0,
    )

    val all = listOf(AVANTE, GRANDEUR, TUCSON, SANTA_FE)
}

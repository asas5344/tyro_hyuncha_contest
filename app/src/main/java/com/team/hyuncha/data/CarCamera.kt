package com.team.hyuncha.data

/**
 * 내 차에 달린 주차 카메라 종류. 후진할 때 차량 화면에 무엇이 뜨는지가 달라짐.
 * - REAR_ONLY: 후방 카메라만 → 후진하면 뒤쪽 카메라 화면 (좌우가 거울처럼 뒤집혀 보임)
 * - SURROUND: 서라운드 뷰 → 후진하면 내 차를 위에서 내려다본 화면
 */
enum class CarCamera(val label: String) {
    REAR_ONLY("후방 카메라만"),
    SURROUND("탑뷰 (서라운드 뷰)"),
}

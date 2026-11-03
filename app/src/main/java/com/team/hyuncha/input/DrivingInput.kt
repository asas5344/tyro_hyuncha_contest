package com.team.hyuncha.input

/**
 * 시뮬레이터가 읽는 운전 입력.
 * 시뮬레이터는 이 인터페이스만 알기 때문에 입력이 터치·실제 차량·게이밍 휠 중
 * 어디서 오든 시뮬레이터 코드는 바뀌지 않음.
 */
interface DrivingInput {
    val steeringDeg: Double     // 핸들 각도(도). 양수 = 오른쪽으로 돌림
    var direction: Direction    // 전진 / 후진 (P단 고정이라 입력 종류와 상관없이 화면 버튼으로 선택)
    val brakePressed: Boolean   // 브레이크 밟음 여부. 떼면 크리프 속도로 천천히 진행
}

enum class Direction(val label: String) {
    FORWARD("전진"),
    REVERSE("후진"),
}

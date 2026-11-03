package com.team.hyuncha.input

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 화면 슬라이더·버튼·키보드로 조작하는 입력 (차량 없이 개발·시연할 때 사용).
 * Compose 상태로 들고 있어서 값이 바뀌면 화면도 같이 갱신됨.
 */
class TouchInput : DrivingInput {
    override var steeringDeg by mutableDoubleStateOf(0.0)
    override var direction by mutableStateOf(Direction.FORWARD)
    override var brakePressed by mutableStateOf(true)  // 시작은 안전하게 정지

    fun reset() {
        steeringDeg = 0.0
        direction = Direction.FORWARD
        brakePressed = true
    }
}

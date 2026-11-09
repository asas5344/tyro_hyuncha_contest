package com.team.hyuncha.safety

import ai.pleos.playground.vehicle.constant.control.VehicleGear

/**
 * 안전 잠금: 실제 차량 입력으로 연습할 때는 P단에서만 진행.
 * P단에서는 변속기가 잠겨 차가 굴러가지 않으므로, 속도·주차 브레이크를 따로 안 봐도
 * 핵심 안전 조건이 충족됨. 조건이 깨지면 시뮬레이터를 바로 멈춤.
 */
enum class LockState(val message: String?) {
    OK(null),
    CHECKING("기어 상태를 확인하고 있어요"),
    NOT_PARK("P단에서만 연습할 수 있어요.\n기어를 P로 바꿔 주세요."),
}

object SafetyLock {
    fun check(gear: VehicleGear?): LockState = when (gear) {
        null -> LockState.CHECKING
        VehicleGear.GEAR_PARK -> LockState.OK
        else -> LockState.NOT_PARK
    }
}

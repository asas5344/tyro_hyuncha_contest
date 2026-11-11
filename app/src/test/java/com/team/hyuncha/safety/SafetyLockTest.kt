package com.team.hyuncha.safety

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 안전 잠금: P단일 때만 연습이 진행돼야 함. 이 규칙이 실수로 바뀌면 여기서 바로 잡힘 */
class SafetyLockTest {

    @Test
    fun P단이면_진행() {
        assertEquals(LockState.OK, SafetyLock.check(VehicleGear.GEAR_PARK))
    }

    @Test
    fun 기어를_아직_못_받았으면_확인_중으로_멈춤() {
        assertEquals(LockState.CHECKING, SafetyLock.check(null))
    }

    /** 가장 중요: SDK가 줄 수 있는 기어 값 중 P가 아닌 것은 하나도 빠짐없이 잠겨야 함 (새 값이 추가돼도 자동으로 검사됨) */
    @Test
    fun P단이_아닌_모든_기어는_잠금() {
        VehicleGear.entries.filter { it != VehicleGear.GEAR_PARK }.forEach { gear ->
            assertEquals("$gear", LockState.NOT_PARK, SafetyLock.check(gear))
        }
    }

    /** 진행할 때는 안내가 없고, 멈출 때는 이유를 알려주는 안내가 반드시 있어야 함 */
    @Test
    fun 멈출_때는_항상_안내_문구가_있다() {
        assertNull(LockState.OK.message)
        LockState.entries.filter { it != LockState.OK }.forEach { assertNotNull("$it", it.message) }
    }
}

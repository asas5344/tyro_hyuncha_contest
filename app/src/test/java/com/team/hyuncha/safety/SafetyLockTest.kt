package com.team.hyuncha.safety

import android.car.VehicleGear
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

    /** 표준 주행 기어와 알 수 없는 값에서는 연습을 잠근다. */
    @Test
    fun P단이_아닌_모든_기어는_잠금() {
        val nonPark = listOf(
            VehicleGear.GEAR_UNKNOWN, VehicleGear.GEAR_NEUTRAL, VehicleGear.GEAR_REVERSE,
            VehicleGear.GEAR_DRIVE, VehicleGear.GEAR_FIRST, VehicleGear.GEAR_SECOND,
            VehicleGear.GEAR_THIRD, VehicleGear.GEAR_FOURTH, VehicleGear.GEAR_FIFTH,
            VehicleGear.GEAR_SIXTH, VehicleGear.GEAR_SEVENTH, VehicleGear.GEAR_EIGHTH,
            VehicleGear.GEAR_NINTH, Int.MAX_VALUE,
        )
        nonPark.forEach { gear ->
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

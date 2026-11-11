package com.team.hyuncha.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 실제 차량 모드의 브레이크 규칙: 확실히 "뗌"일 때만 가상 차가 움직여야 함 */
class BrakeGateTest {

    @Test
    fun 브레이크를_한_번도_안_밟았으면_페달이_무엇이든_정지() {
        listOf(null, true, false).forEach { pedal ->
            assertTrue("pedal=$pedal", BrakeGate.holding(armed = false, pedal = pedal))
        }
    }

    @Test
    fun 준비된_뒤_페달을_밟고_있으면_정지() {
        assertTrue(BrakeGate.holding(armed = true, pedal = true))
    }

    @Test
    fun 준비된_뒤_페달_상태를_모르면_정지() {
        assertTrue(BrakeGate.holding(armed = true, pedal = null))
    }

    /** 움직이는 경우는 이것 하나뿐 */
    @Test
    fun 준비된_뒤_페달을_뗀_것이_확인되면_진행() {
        assertFalse(BrakeGate.holding(armed = true, pedal = false))
    }

    @Test
    fun 브레이크를_밟아야만_준비된다() {
        assertFalse(BrakeGate.armedAfter(armed = false, pedal = null))
        assertFalse(BrakeGate.armedAfter(armed = false, pedal = false))
        assertTrue(BrakeGate.armedAfter(armed = false, pedal = true))
    }

    @Test
    fun 한_번_준비되면_페달을_떼거나_값이_끊겨도_유지된다() {
        assertTrue(BrakeGate.armedAfter(armed = true, pedal = false))
        assertTrue(BrakeGate.armedAfter(armed = true, pedal = null))
    }

    /** 처음 켰을 때의 흐름: 값 없음 → 뗌(P단이라 안 밟고 있음) → 밟음 → 뗌 */
    @Test
    fun 화면에_들어오자마자_차가_굴러가지_않는다() {
        var armed = false
        val moved = listOf(null, false, true, false).map { pedal ->
            armed = BrakeGate.armedAfter(armed, pedal)
            !BrakeGate.holding(armed, pedal)
        }
        // 브레이크를 밟았다 뗀 마지막 순간에만 움직임
        assertTrue(moved == listOf(false, false, false, true))
    }
}

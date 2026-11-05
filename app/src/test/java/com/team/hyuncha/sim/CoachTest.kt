package com.team.hyuncha.sim

import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.DrivingInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class UserInput(
    override var steeringDeg: Double = 0.0,
    override var direction: Direction = Direction.FORWARD,
    override var brakePressed: Boolean = true,
) : DrivingInput

class CoachTest {

    private val dt = 1.0 / 60

    @Test
    fun 직각주차_모든_차종_모든_목표_칸에서_모범_주행을_찾는다() {
        val full = (0 until ParkingMaps.SLOT_COUNT).toList()
        for (target in ParkingMaps.TARGET_RANGE) for (car in CarPresets.all) {
            val plan = Coach.solve(car, ParkingMaps.perpendicular(target, full))
            assertNotNull("${car.name} ${target + 1}번 칸", plan)
            assertEquals(SimStatus.PARKED, plan!!.run.status)
            assertEquals(1, plan.run.directionChanges)   // 전진 → 후진 한 번
        }
    }

    @Test
    fun 평행주차_모든_차종_모든_목표_칸에서_모범_주행을_찾는다() {
        val full = (0 until ParkingMaps.PARALLEL_SLOT_COUNT).toList()
        for (target in ParkingMaps.PARALLEL_TARGET_RANGE) for (car in CarPresets.all) {
            val plan = Coach.solve(car, ParkingMaps.parallel(target, full))
            assertNotNull("${car.name} B${target + 1}", plan)
            assertEquals(SimStatus.PARKED, plan!!.run.status)
        }
    }

    @Test
    fun 모범_주행_계산은_1초_안에_끝난다() {
        val start = System.nanoTime()
        Coach.solve(CarPresets.GRANDEUR, ParkingMaps.parallel())
        Coach.solve(CarPresets.GRANDEUR, ParkingMaps.perpendicular())
        val sec = (System.nanoTime() - start) / 1e9
        println("모범 주행 계산 시간: %.2f초".format(sec))
        assertTrue("너무 오래 걸림: $sec 초", sec < 1.0)
    }

    @Test
    fun 모범_주행에는_단계_자막이_순서대로_있다() {
        val plan = Coach.solve(CarPresets.AVANTE, ParkingMaps.perpendicular())!!
        assertTrue(plan.steps.size >= 3)
        assertTrue(plan.steps.zipWithNext().all { (a, b) -> a.timeSec <= b.timeSec })
        assertTrue(plan.stepAt(0.0)!!.text.startsWith("1."))
    }

    @Test
    fun 모범을_그대로_따라하면_잘했다고_한다() {
        val plan = Coach.solve(CarPresets.AVANTE, ParkingMaps.perpendicular())!!
        assertEquals(listOf("모범 주행과 거의 같아요. 잘했어요!"), Coach.feedback(plan.run, plan))
    }

    @Test
    fun 후진을_너무_일찍_시작하면_더_가서_멈추라고_한다() {
        val map = ParkingMaps.perpendicular()
        val plan = Coach.solve(CarPresets.AVANTE, map)!!
        val mine = drive(map, stopX = plan.reverseStartX - 2.0, reverseSteer = CarPresets.AVANTE.maxSteeringWheelDeg)
        assertTrue(Coach.feedback(mine, plan).joinToString().contains("덜 지나서"))
    }

    @Test
    fun 후진할_때_핸들을_반대로_돌리면_알려준다() {
        val map = ParkingMaps.perpendicular()
        val plan = Coach.solve(CarPresets.AVANTE, map)!!
        val mine = drive(map, stopX = plan.reverseStartX, reverseSteer = -CarPresets.AVANTE.maxSteeringWheelDeg)
        assertTrue(Coach.feedback(mine, plan).joinToString().contains("반대"))
    }

    @Test
    fun 후진할_때_핸들을_덜_돌리면_알려준다() {
        val map = ParkingMaps.perpendicular()
        val plan = Coach.solve(CarPresets.AVANTE, map)!!
        val mine = drive(map, stopX = plan.reverseStartX, reverseSteer = CarPresets.AVANTE.maxSteeringWheelDeg * 0.4)
        assertTrue(Coach.feedback(mine, plan).joinToString().contains("덜 돌렸어요"))
    }

    /** 사용자 흉내: 앞으로 가서 stopX에서 멈추고, 핸들을 reverseSteer로 두고 부딪히거나 10초 지날 때까지 후진 */
    private fun drive(map: ParkingMap, stopX: Double, reverseSteer: Double): ParkingSimulator {
        val sim = ParkingSimulator(CarPresets.AVANTE, map)
        val input = UserInput(brakePressed = false)
        repeat(3000) { if (sim.pose.x < stopX) sim.step(input, dt) }
        input.brakePressed = true
        repeat(60) { sim.step(input, dt) }
        input.direction = Direction.REVERSE
        input.steeringDeg = reverseSteer
        input.brakePressed = false
        repeat(600) { sim.step(input, dt) }
        return sim
    }
}

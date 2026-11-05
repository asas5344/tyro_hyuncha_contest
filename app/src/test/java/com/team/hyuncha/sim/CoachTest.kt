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
        assertEquals(listOf("깔끔하게 주차했어요. 잘했어요!"), Coach.feedback(plan.run, plan))
    }

    @Test
    fun 후진을_너무_일찍_시작하면_더_가서_멈추라고_한다() {
        val map = ParkingMaps.perpendicular()
        val plan = Coach.solve(CarPresets.AVANTE, map)!!
        val mine = drive(map, stopX = plan.actionStartX - 2.0, reverseSteer = CarPresets.AVANTE.maxSteeringWheelDeg)
        assertEquals(SimStatus.COLLIDED, mine.status)
        val text = Coach.feedback(mine, plan).joinToString()
        // 브레이크를 밟는 동안 조금 밀리므로 정확히 2.0m는 아님
        assertTrue(text, text.contains("일찍 시작했어요") && text.contains("더 앞으로 가서"))
    }

    @Test
    fun 후진할_때_핸들을_반대로_돌리면_알려준다() {
        val map = ParkingMaps.perpendicular()
        val plan = Coach.solve(CarPresets.AVANTE, map)!!
        val mine = drive(map, stopX = plan.actionStartX, reverseSteer = -CarPresets.AVANTE.maxSteeringWheelDeg)
        assertEquals(SimStatus.COLLIDED, mine.status)
        val text = Coach.feedback(mine, plan).joinToString()
        assertTrue(text, text.contains("왼쪽으로 돌렸어요"))
    }

    @Test
    fun 후진할_때_핸들을_덜_돌리면_알려준다() {
        val map = ParkingMaps.perpendicular()
        val plan = Coach.solve(CarPresets.AVANTE, map)!!
        val mine = drive(map, stopX = plan.actionStartX, reverseSteer = CarPresets.AVANTE.maxSteeringWheelDeg * 0.4)
        assertEquals(SimStatus.COLLIDED, mine.status)
        val text = Coach.feedback(mine, plan).joinToString()
        assertTrue(text, text.contains("덜 돌렸어요"))
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
        repeat(60 * 30) { sim.step(input, dt) }
        return sim
    }

    // ───── 다른 방법으로 움직였을 때 틀린 조언을 하지 않는지 ─────

    @Test
    fun 앞으로_넣어서_성공하면_후진_얘기를_하지_않는다() {
        val map = ParkingMaps.perpendicular()
        val car = CarPresets.AVANTE
        val plan = Coach.solve(car, map)!!
        val sim = ParkingSimulator(car, map)
        // 목표 칸 위 통로에서 칸을 보고(아래 방향) 서 있다가 앞으로 넣음
        sim.reset(CarPose(map.slot.center.x, 1.74, Math.PI / 2))
        val input = UserInput(brakePressed = false)
        repeat(3000) {
            if (sim.bodyRect().center.y + sim.speed * sim.speed / (2 * ParkingSimulator.BRAKE_DECEL) < map.slot.center.y) sim.step(input, dt)
        }
        input.brakePressed = true
        repeat(120) { sim.step(input, dt) }
        assertEquals(SimStatus.PARKED, sim.status)
        val text = Coach.feedback(sim, plan).joinToString()
        assertTrue(text, !text.contains("후진"))
        assertTrue(text, text.contains("잘했어요"))
    }

    @Test
    fun 처음부터_후진하다_닿으면_숫자로_비교하지_않는다() {
        val map = ParkingMaps.perpendicular()
        val plan = Coach.solve(CarPresets.AVANTE, map)!!
        val sim = ParkingSimulator(CarPresets.AVANTE, map)
        val input = UserInput(direction = Direction.REVERSE, brakePressed = false)
        repeat(60 * 30) { sim.step(input, dt) }
        assertEquals(SimStatus.COLLIDED, sim.status)
        val text = Coach.feedback(sim, plan).joinToString()
        assertTrue(text, text.contains("다른 순서"))
        assertTrue(text, !text.contains("m 더 가서") && !text.contains("핸들을"))
    }

    @Test
    fun 앞으로_가다_닿으면_그렇게_알려준다() {
        val map = ParkingMaps.perpendicular()
        val plan = Coach.solve(CarPresets.AVANTE, map)!!
        val sim = ParkingSimulator(CarPresets.AVANTE, map)
        val input = UserInput(brakePressed = false)
        repeat(60 * 60) { sim.step(input, dt) }
        assertEquals(SimStatus.COLLIDED, sim.status)
        val text = Coach.feedback(sim, plan).joinToString()
        assertTrue(text, text.contains("앞으로 가다가 닿았어요"))
    }

    @Test
    fun 여러_번_맞춰서_성공해도_방법을_탓하지_않는다() {
        // 모범 주행 기록 뒤에 앞으로 조금 갔다가 다시 후진해서 성공한 것처럼: 성공이면 전환이 많다는 말만, 순서 비판은 없음
        val plan = Coach.solve(CarPresets.AVANTE, ParkingMaps.perpendicular())!!
        val text = Coach.feedback(plan.run, plan).joinToString()
        assertTrue(text, !text.contains("일찍") && !text.contains("늦게") && !text.contains("덜 돌렸어요"))
    }
}

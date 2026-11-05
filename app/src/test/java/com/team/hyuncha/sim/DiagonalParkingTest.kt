package com.team.hyuncha.sim

import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.DrivingInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private class DiagInput(
    override var steeringDeg: Double = 0.0,
    override var direction: Direction = Direction.FORWARD,
    override var brakePressed: Boolean = true,
) : DrivingInput

class DiagonalParkingTest {

    private val dt = 1.0 / 60

    @Test
    fun 사선_칸은_통로_경계_아래에_있고_서로_겹치지_않는다() {
        val map = ParkingMaps.diagonal()
        val aisle = map.start.y * 2
        map.bays.forEach { bay -> assertTrue(bay.corners().all { it.y >= aisle - 1e-6 }) }
        map.bays.zipWithNext().forEach { (a, b) ->
            // 옆 칸끼리는 테두리만 닿고 안쪽이 겹치지 않음 (살짝 줄여서 확인)
            assertTrue(!a.copy(halfWidth = a.halfWidth - 0.01).intersects(b.copy(halfWidth = b.halfWidth - 0.01)))
        }
    }

    @Test
    fun 사선_칸에_정렬해서_멈추면_성공한다() {
        val map = ParkingMaps.diagonal()
        val car = CarPresets.GRANDEUR
        val sim = ParkingSimulator(car, map)
        val toCenter = car.lengthM / 2 - car.rearOverhangM
        val back = Vec2.unit(map.slot.angle) * toCenter
        sim.reset(CarPose(map.slot.center.x - back.x, map.slot.center.y - back.y, map.slot.angle))
        sim.step(DiagInput(), dt)
        assertEquals(SimStatus.PARKED, sim.status)
    }

    @Test
    fun 모든_차종이_모든_목표_칸에_사선주차할_수_있다() {
        val full = (0 until ParkingMaps.DIAGONAL_SLOT_COUNT).toList()
        for (target in ParkingMaps.DIAGONAL_TARGET_RANGE) for (car in CarPresets.all) {
            val plan = Coach.solve(car, ParkingMaps.diagonal(target, full))
            assertNotNull("${car.name} C${target + 1}", plan)
            assertEquals(SimStatus.PARKED, plan!!.run.status)
            assertEquals(0, plan.run.directionChanges)   // 앞으로만
        }
    }

    @Test
    fun 랜덤_사선주차는_목표_칸이_비어_있고_양옆은_차있다() {
        repeat(50) { seed ->
            val map = ParkingMaps.randomDiagonal(kotlin.random.Random(seed))
            assertEquals(ParkingMode.DIAGONAL, map.mode)
            val t = map.bays.indexOfFirst { it.center == map.slot.center }
            assertTrue(t in ParkingMaps.DIAGONAL_TARGET_RANGE)
            assertTrue(map.parkedCars.none { abs(it.center.x - map.slot.center.x) < 0.01 })
            listOf(t - 1, t + 1).forEach { n -> assertTrue(map.parkedCars.any { abs(it.center.x - map.bays[n].center.x) < 0.01 }) }
        }
    }

    @Test
    fun 핸들을_일찍_꺾어_닿으면_더_가서_꺾으라고_한다() {
        val map = ParkingMaps.diagonal()
        val car = CarPresets.AVANTE
        val plan = Coach.solve(car, map)!!
        val sim = forwardAndTurn(map, turnX = plan.actionStartX - 3.0, steer = car.maxSteeringWheelDeg)
        assertEquals(SimStatus.COLLIDED, sim.status)
        val text = Coach.feedback(sim, plan).joinToString()
        assertTrue(text, text.contains("일찍 꺾었어요"))
    }

    @Test
    fun 꺾지_않고_곧게_가다_닿으면_그렇게_알려준다() {
        val map = ParkingMaps.diagonal()
        val plan = Coach.solve(CarPresets.AVANTE, map)!!
        val sim = forwardAndTurn(map, turnX = 1000.0, steer = 0.0)
        assertEquals(SimStatus.COLLIDED, sim.status)
        val text = Coach.feedback(sim, plan).joinToString()
        assertTrue(text, text.contains("꺾지 않고"))
    }

    @Test
    fun 모범을_그대로_따라하면_잘했다고_한다() {
        val plan = Coach.solve(CarPresets.SANTA_FE, ParkingMaps.diagonal())!!
        assertEquals(listOf("깔끔하게 주차했어요. 잘했어요!"), Coach.feedback(plan.run, plan))
    }

    private fun forwardAndTurn(map: ParkingMap, turnX: Double, steer: Double): ParkingSimulator {
        val sim = ParkingSimulator(CarPresets.AVANTE, map)
        val input = DiagInput(brakePressed = false)
        repeat(60 * 60) {
            if (sim.pose.x >= turnX) input.steeringDeg = steer
            sim.step(input, dt)
        }
        return sim
    }
}

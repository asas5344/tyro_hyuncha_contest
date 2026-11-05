package com.team.hyuncha.sim

import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.DrivingInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

private class ScriptInput(
    override var steeringDeg: Double = 0.0,
    override var direction: Direction = Direction.FORWARD,
    override var brakePressed: Boolean = true,
) : DrivingInput

class ParallelParkingTest {

    private val dt = 1.0 / 60

    @Test
    fun 평행주차_칸에_정렬해서_멈추면_성공한다() {
        val map = ParkingMaps.parallel()
        val car = CarPresets.GRANDEUR
        val sim = ParkingSimulator(car, map)
        // 차체 중심이 칸 중심에 오도록 뒷바퀴 축 위치 계산 (오른쪽을 보고 서 있음)
        val toCenter = car.lengthM / 2 - car.rearOverhangM
        sim.reset(CarPose(map.slot.center.x - toCenter, map.slot.center.y, 0.0))
        sim.step(ScriptInput(), dt)
        assertEquals(SimStatus.PARKED, sim.status)
    }

    @Test
    fun 랜덤_평행주차는_목표_칸이_비어_있고_앞뒤_칸은_차있다() {
        repeat(50) { seed ->
            val map = ParkingMaps.randomParallel(kotlin.random.Random(seed))
            assertEquals(ParkingMode.PARALLEL, map.mode)
            val targetIndex = map.bays.indexOfFirst { it.center == map.slot.center }
            assertTrue(targetIndex in ParkingMaps.PARALLEL_TARGET_RANGE)
            assertTrue(map.parkedCars.none { abs(it.center.x - map.slot.center.x) < 0.01 })
            listOf(targetIndex - 1, targetIndex + 1).forEach { n ->
                assertTrue(map.parkedCars.any { abs(it.center.x - map.bays[n].center.x) < 0.01 })
            }
        }
    }

    /** 가장 어려운 경우(나머지 칸이 전부 찬 상태)에서 모든 차종이 모든 목표 칸에 평행주차할 수 있어야 함 */
    @Test
    fun 모든_차종이_모든_목표_칸에_평행주차할_수_있다() {
        val full = (0 until ParkingMaps.PARALLEL_SLOT_COUNT).toList()
        for (target in ParkingMaps.PARALLEL_TARGET_RANGE) {
            for (car in CarPresets.all) {
                val solved = solveParallel(car) { ParkingMaps.parallel(target, full) }
                assertTrue("${car.name}: ${target + 1}번 칸 평행주차 경로를 찾지 못함", solved)
            }
        }
    }

    /** 사람이 하는 순서대로 여러 조합을 시도: 앞차 옆에 섬 → 오른쪽 끝까지 꺾고 후진 → 곧게 후진 → 왼쪽 끝까지 꺾고 후진 */
    private fun solveParallel(car: CarSpec, makeMap: () -> ParkingMap): Boolean {
        val probe = makeMap()
        val slotRight = probe.slot.center.x + probe.slot.halfLength
        for (laneY in listOf(4.4, 4.7, 5.0)) {                 // 앞차와의 옆 간격
            for (k in 0..16) {
                val stopX = slotRight - 1.0 + k * 0.25            // 후진 시작 위치 (뒷바퀴 축)
                for (angleDeg in listOf(35.0, 40.0, 45.0, 50.0, 55.0)) {
                    for (straight in listOf(0.0, 0.3, 0.6, 0.9, 1.2, 1.5)) {
                        if (tryParallel(ParkingSimulator(car, makeMap()), laneY, stopX, Math.toRadians(angleDeg), straight)) return true
                    }
                }
            }
        }
        return false
    }

    private fun tryParallel(sim: ParkingSimulator, laneY: Double, stopX: Double, angle: Double, straight: Double): Boolean {
        sim.reset(CarPose(sim.map.start.x, laneY, 0.0))
        val input = ScriptInput(brakePressed = false)
        val maxSteer = sim.car.maxSteeringWheelDeg
        val brakeDist = { sim.speed * sim.speed / (2 * ParkingSimulator.BRAKE_DECEL) }
        val turnRadius = sim.car.wheelBaseM / kotlin.math.tan(Math.toRadians(sim.car.maxWheelAngleDeg))

        fun run(until: () -> Boolean): Boolean {
            repeat(4000) {
                if (sim.status == SimStatus.COLLIDED) return false
                if (sim.status == SimStatus.PARKED || until()) return true   // 멈추는 중에 주차 성공 판정이 나도 성공
                sim.step(input, dt)
            }
            return false
        }
        fun stop(): Boolean { input.brakePressed = true; return run { abs(sim.speed) < 1e-3 } }
        fun go(dir: Direction, steer: Double) { input.direction = dir; input.steeringDeg = steer; input.brakePressed = false }

        // 1) 앞으로 가서 후진 시작 위치에 섬
        go(Direction.FORWARD, 0.0)
        if (!run { sim.pose.x + brakeDist() >= stopX }) return false
        if (!stop()) return false
        // 2) 오른쪽 끝까지 꺾고 후진: 차 뒤가 연석 쪽으로 들어가며 비스듬해짐
        go(Direction.REVERSE, maxSteer)
        if (!run { sim.pose.heading <= -angle }) return false
        // 3) 핸들 풀고 곧게 후진
        go(Direction.REVERSE, 0.0)
        val sx = sim.pose.x; val sy = sim.pose.y
        if (!run { hypot(sim.pose.x - sx, sim.pose.y - sy) >= straight }) return false
        // 4) 왼쪽 끝까지 꺾고 후진: 차를 연석과 나란히 (멈추는 동안 더 도는 만큼 미리 브레이크)
        go(Direction.REVERSE, -maxSteer)
        if (!run { sim.pose.heading + brakeDist() / turnRadius >= 0.0 }) return false
        if (!stop()) return false
        if (sim.status == SimStatus.PARKED) return true
        // 5) 칸 안에서 앞뒤 위치만 맞춤 (핸들 가운데로 전진)
        go(Direction.FORWARD, 0.0)
        val target = sim.map.slot.center.x
        if (!run { sim.bodyRect().center.x + brakeDist() >= target }) return false
        if (!stop()) return false
        repeat(10) { sim.step(input, dt) }
        return sim.status == SimStatus.PARKED
    }
}

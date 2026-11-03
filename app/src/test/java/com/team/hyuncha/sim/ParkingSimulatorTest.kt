package com.team.hyuncha.sim

import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.DrivingInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/** 테스트용 입력: 값을 직접 넣어서 조작 */
private class FakeInput(
    override var steeringDeg: Double = 0.0,
    override var direction: Direction = Direction.FORWARD,
    override var brakePressed: Boolean = true,
) : DrivingInput

private const val DT = 1.0 / 60

class ParkingSimulatorTest {

    private fun newSim() = ParkingSimulator(CarPresets.GRANDEUR, ParkingMaps.perpendicular())

    @Test
    fun 브레이크를_밟고_있으면_움직이지_않는다() {
        val sim = newSim()
        val input = FakeInput(brakePressed = true)
        repeat(120) { sim.step(input, DT) }
        assertEquals(sim.map.start, sim.pose)
    }

    @Test
    fun 핸들_가운데로_전진하면_직진한다() {
        val sim = newSim()
        val input = FakeInput(brakePressed = false)
        repeat(120) { sim.step(input, DT) }
        assertTrue(sim.pose.x > sim.map.start.x + 1.0)
        assertEquals(sim.map.start.y, sim.pose.y, 1e-9)
        assertEquals(0.0, sim.pose.heading, 1e-9)
    }

    @Test
    fun 핸들을_고정하면_회전반경_원을_따라간다() {
        val sim = newSim()
        sim.reset(CarPose(15.0, 3.0, 0.0))
        val car = sim.car
        val input = FakeInput(steeringDeg = 300.0, brakePressed = false)
        // 회전 중심: 뒷바퀴 축에서 오른쪽(y 아래 방향)으로 R = 휠베이스 / tan(바퀴각)
        val radius = car.wheelBaseM / tan(Math.toRadians(300.0 / car.steeringRatio))
        val cx = 15.0 - radius * sin(0.0)
        val cy = 3.0 + radius * cos(0.0)
        // 충돌 판정을 피하려고 벽 없는 큰 맵에서 돌림
        val open = ParkingSimulator(car, sim.map.copy(walls = emptyList(), parkedCars = emptyList()))
        open.reset(CarPose(15.0, 3.0, 0.0))
        repeat(300) { open.step(input, DT) }
        assertEquals(radius, hypot(open.pose.x - cx, open.pose.y - cy), radius * 0.01)
        assertTrue("오른쪽으로 돌았으므로 heading 증가", open.pose.heading > 0)
    }

    @Test
    fun 후진하면서_핸들을_오른쪽으로_돌리면_차_앞이_왼쪽으로_돈다() {
        val open = ParkingSimulator(CarPresets.GRANDEUR, newSim().map.copy(walls = emptyList(), parkedCars = emptyList()))
        open.reset(CarPose(15.0, 3.0, 0.0))
        val input = FakeInput(steeringDeg = 300.0, direction = Direction.REVERSE, brakePressed = false)
        repeat(120) { open.step(input, DT) }
        assertTrue(open.pose.heading < 0)
        assertTrue(open.pose.x < 15.0)
    }

    @Test
    fun 벽에_부딪히면_충돌로_멈춘다() {
        val sim = newSim()
        val input = FakeInput(brakePressed = false)
        repeat(60 * 60) { sim.step(input, DT) }
        assertEquals(SimStatus.COLLIDED, sim.status)
        val before = sim.pose
        sim.step(input, DT)
        assertEquals(before, sim.pose)
    }

    @Test
    fun 칸_안에_정렬해서_멈추면_성공한다() {
        val sim = newSim()
        val slot = sim.map.slot
        // 차체 중심이 칸 중심에 오도록 뒷바퀴 축 위치 계산 (위를 보고 후진 주차된 상태)
        val heading = -PI / 2
        val toCenter = sim.car.lengthM / 2 - sim.car.rearOverhangM
        sim.reset(CarPose(slot.center.x, slot.center.y + toCenter, heading))
        sim.step(FakeInput(brakePressed = true), DT)
        assertEquals(SimStatus.PARKED, sim.status)
        assertEquals(0.0, sim.result!!.angleErrorDeg, 1e-6)
    }

    @Test
    fun 칸_안이어도_비뚤면_성공이_아니다() {
        val sim = newSim()
        val slot = sim.map.slot
        val heading = -PI / 2 + Math.toRadians(10.0)
        val toCenter = sim.car.lengthM / 2 - sim.car.rearOverhangM
        sim.reset(CarPose(slot.center.x - toCenter * cos(heading), slot.center.y - toCenter * sin(heading), heading))
        sim.step(FakeInput(brakePressed = true), DT)
        assertFalse(sim.status == SimStatus.PARKED)
    }

    /** 모든 차종이 이 맵에서 실제로 주차 가능한지: 전진 → 오른쪽으로 꺾으며 후진 → 직진 후진 */
    @Test
    fun 모든_차종이_직각주차를_성공할_수_있다() {
        for (car in CarPresets.all) {
            // 통로에서 차를 어디에 붙이는지(y)도 운전자가 고르는 부분이라 같이 탐색
            val solved = listOf(3.0, 2.6, 2.2, 1.8).any { startY ->
                (0..60).any { i ->
                    val stopX = 14.0 + i * 0.1
                    listOf(1.0, 0.9, 0.8).any { frac ->
                        tryBackIn(ParkingSimulator(car, ParkingMaps.perpendicular()), startY, stopX, frac)
                    }
                }
            }
            assertTrue("${car.name} 주차 경로를 찾지 못함", solved)
        }
    }

    private fun tryBackIn(sim: ParkingSimulator, startY: Double, stopX: Double, steerFrac: Double): Boolean {
        sim.reset(CarPose(sim.map.start.x, startY, 0.0))
        val input = FakeInput(brakePressed = false)
        fun run(maxSteps: Int, until: () -> Boolean): Boolean {
            repeat(maxSteps) {
                if (until() || sim.status != SimStatus.DRIVING) return sim.status == SimStatus.DRIVING
                sim.step(input, DT)
            }
            return false
        }
        fun stop() { input.brakePressed = true; run(300) { abs(sim.speed) < 1e-3 } }

        // 1) 직진해서 칸을 지나침
        if (!run(3000) { sim.pose.x >= stopX }) return false
        stop()
        if (sim.status != SimStatus.DRIVING) return false
        // 2) 핸들 오른쪽 + 후진: 차 앞이 위를 볼 때까지
        input.direction = Direction.REVERSE
        input.steeringDeg = sim.car.maxSteeringWheelDeg * steerFrac
        input.brakePressed = false
        if (!run(3000) { sim.pose.heading <= -PI / 2 + 0.01 }) return false
        // 3) 핸들 풀고 곧게 후진: 제동 거리(v²/2a)만큼 미리 브레이크를 밟아 칸 중심에서 멈춤
        input.steeringDeg = 0.0
        val brakingDistance = { sim.speed * sim.speed / (2 * ParkingSimulator.BRAKE_DECEL) }
        if (!run(3000) { sim.bodyRect().center.y + brakingDistance() >= sim.map.slot.center.y }) return false
        input.brakePressed = true
        repeat(300) { sim.step(input, DT) }
        return sim.status == SimStatus.PARKED
    }
}

package com.team.hyuncha.sim

import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.DrivingInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

private class ReplayInput(
    override var steeringDeg: Double = 0.0,
    override var direction: Direction = Direction.FORWARD,
    override var brakePressed: Boolean = true,
) : DrivingInput

class ReplayTest {

    private val dt = 1.0 / 60
    private fun newSim() = ParkingSimulator(CarPresets.GRANDEUR, ParkingMaps.perpendicular())

    @Test
    fun 곧게_후진해서_벽에_닿으면_뒤_범퍼가_벽에_닿았다고_알려준다() {
        val sim = newSim()
        val input = ReplayInput(direction = Direction.REVERSE, brakePressed = false)
        repeat(60 * 30) { sim.step(input, dt) }
        assertEquals(SimStatus.COLLIDED, sim.status)
        val hit = assertNotNullAndGet(sim.collision)
        assertEquals(CarPart.REAR, hit.part)
        assertEquals(ObstacleKind.WALL, hit.obstacle)
        assertEquals("뒤 범퍼가 벽에 닿았어요", hit.message)
    }

    @Test
    fun 주차된_차로_곧게_전진하면_앞_범퍼가_주차된_차에_닿았다고_알려준다() {
        val sim = newSim()
        val parked = sim.map.parkedCars.first()
        // 주차된 차 바로 위 통로에서 아래(주차된 차 쪽)를 보고 출발 (뒤 범퍼가 위쪽 벽에서 40cm 떨어지게)
        sim.reset(CarPose(parked.center.x, sim.car.rearOverhangM + 0.4, PI / 2))
        val input = ReplayInput(direction = Direction.FORWARD, brakePressed = false)
        repeat(60 * 30) { sim.step(input, dt) }
        val hit = assertNotNullAndGet(sim.collision)
        assertEquals(CarPart.FRONT, hit.part)
        assertEquals(ObstacleKind.PARKED_CAR, hit.obstacle)
    }

    @Test
    fun 비스듬히_닿으면_모서리로_알려준다() {
        // 차 앞-오른쪽 모서리만 장애물 안으로 들어간 상황
        val body = OrientedRect(Vec2(0.0, 0.0), 2.4, 0.9, 0.0)
        val box = OrientedRect(Vec2(2.6, 1.1), 0.5, 0.5, 0.0)
        assertEquals(CarPart.FRONT_RIGHT, Contact.find(body, box).first)
    }

    @Test
    fun 장애물_모서리가_옆구리를_찌르면_옆면으로_알려준다() {
        val body = OrientedRect(Vec2(0.0, 0.0), 2.4, 0.9, 0.0)
        val box = OrientedRect(Vec2(0.0, -1.5), 0.6, 0.6, Math.PI / 4)   // 마름모 꼭짓점이 왼쪽 옆면을 찌름
        assertEquals(CarPart.LEFT_SIDE, Contact.find(body, box).first)
    }

    @Test
    fun 전진_후진을_바꾸면_사건으로_기록된다() {
        val sim = newSim()
        val input = ReplayInput(brakePressed = false)
        repeat(90) { sim.step(input, dt) }             // 전진
        input.direction = Direction.REVERSE
        repeat(180) { sim.step(input, dt) }            // 감속 후 후진
        val changes = sim.events.filterIsInstance<SimEvent.DirectionChanged>()
        assertEquals(1, changes.size)
        assertEquals(Direction.REVERSE, changes.first().to)
        assertEquals(1, changes.first().count)
    }

    @Test
    fun 멈춰_있는_동안은_기록하지_않고_재생_시각으로_위치를_찾는다() {
        val sim = newSim()
        val input = ReplayInput(brakePressed = true)
        repeat(120) { sim.step(input, dt) }            // 2초 정지
        assertEquals(1, sim.history.size)
        input.brakePressed = false
        repeat(120) { sim.step(input, dt) }            // 2초 전진
        assertTrue(sim.history.size > 100)
        // 처음 2초 안의 아무 시각이든 출발 위치
        assertEquals(sim.map.start, sim.frameAt(1.5).pose)
        // 마지막 시각 이후는 마지막 위치
        assertEquals(sim.pose, sim.frameAt(100.0).pose)
        // 시간이 갈수록 앞으로 감
        assertTrue(sim.frameAt(3.0).pose.x < sim.frameAt(3.9).pose.x)
    }

    @Test
    fun 다시_하면_기록이_지워진다() {
        val sim = newSim()
        val input = ReplayInput(direction = Direction.REVERSE, brakePressed = false)
        repeat(60 * 30) { sim.step(input, dt) }
        sim.reset()
        assertEquals(1, sim.history.size)
        assertTrue(sim.events.isEmpty())
    }

    @Test
    fun 계기판_재생은_기록된_실제_속도를_사용하고_충돌_정지도_기록된다() {
        val sim=newSim()
        val input=ReplayInput(brakePressed=false)
        repeat(100) { sim.step(input,dt) }
        val moving=sim.history.last()
        assertEquals(sim.speed,moving.speedMps,0.0)
        input.brakePressed=true
        repeat(100) { sim.step(input,dt) }
        assertEquals(0.0,sim.history.last().speedMps,0.0)
        assertEquals(moving.speedMps,sim.frameAt(moving.timeSec).speedMps,0.0)
        sim.reset()
        input.direction=Direction.REVERSE;input.brakePressed=false
        repeat(2000) { sim.step(input,dt) }
        assertEquals(SimStatus.COLLIDED,sim.status)
        assertEquals(0.0,sim.history.last().speedMps,0.0)
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}

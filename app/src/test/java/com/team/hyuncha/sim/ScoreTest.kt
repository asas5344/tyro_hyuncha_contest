package com.team.hyuncha.sim

import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.DrivingInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

private class StillInput(
    override val steeringDeg: Double = 0.0,
    override var direction: Direction = Direction.FORWARD,
    override val brakePressed: Boolean = true,
) : DrivingInput

class ScoreTest {

    private val dt = 1.0 / 60

    /** 차체 중심을 칸 기준 좌표(앞뒤 along, 좌우 across)에 두고 멈춰서 주차 판정 */
    private fun parkAt(map: ParkingMap, along: Double = 0.0, across: Double = 0.0, headingOffset: Double = 0.0): ParkingSimulator {
        val sim = ParkingSimulator(CarPresets.GRANDEUR, map)
        val slot = map.slot
        val axisL = Vec2.unit(slot.angle)
        val axisW = Vec2(-kotlin.math.sin(slot.angle), kotlin.math.cos(slot.angle))
        val center = slot.center + axisL * along + axisW * across
        // T자 칸은 위를 보고 후진으로 넣은 상태, 평행 칸은 오른쪽을 본 상태
        val heading = (if (map.mode == ParkingMode.PARALLEL) 0.0 else -PI / 2) + headingOffset
        val toCenter = sim.car.lengthM / 2 - sim.car.rearOverhangM
        sim.reset(CarPose(center.x - toCenter * kotlin.math.cos(heading), center.y - toCenter * kotlin.math.sin(heading), heading))
        sim.step(StillInput(), dt)
        assertEquals(SimStatus.PARKED, sim.status)
        return sim
    }

    private fun ParkingScore.points(name: String) = items.first { it.name == name }.points

    @Test
    fun 주차_전에는_점수가_없다() {
        val sim = ParkingSimulator(CarPresets.GRANDEUR, ParkingMaps.perpendicular())
        assertNull(Scoring.score(sim, null))
    }

    @Test
    fun 칸_가운데에_반듯하게_넣으면_100점_S등급() {
        val score = Scoring.score(parkAt(ParkingMaps.perpendicular()), null)!!
        assertEquals(100, score.total)
        assertEquals("S", score.grade)
        assertEquals(100, score.items.sumOf { it.max })
    }

    @Test
    fun 비뚤수록_각도_점수가_깎인다() {
        val straight = Scoring.score(parkAt(ParkingMaps.perpendicular()), null)!!.points("각도 정렬")
        val tilted = Scoring.score(parkAt(ParkingMaps.perpendicular(), headingOffset = Math.toRadians(4.0)), null)!!.points("각도 정렬")
        assertEquals(25, straight)
        assertTrue("4°면 감점: $tilted", tilted in 6..20)
    }

    @Test
    fun 평행주차는_연석_쪽으로_붙으면_감점_없고_도로_쪽으로_나오면_감점() {
        val map = ParkingMaps.parallel()
        val margin = map.slot.halfWidth - CarPresets.GRANDEUR.widthM / 2
        val toCurb = Scoring.score(parkAt(map, across = margin * 0.9), null)!!.points("좌우 위치")
        val toRoad = Scoring.score(parkAt(map, across = -margin * 0.9), null)!!.points("좌우 위치")
        assertEquals(15, toCurb)
        assertTrue("도로 쪽으로 나오면 감점: $toRoad", toRoad < 10)
    }

    @Test
    fun 등급_기준() {
        assertEquals("S", Scoring.grade(95))
        assertEquals("A", Scoring.grade(94))
        assertEquals("A", Scoring.grade(85))
        assertEquals("B", Scoring.grade(70))
        assertEquals("C", Scoring.grade(55))
        assertEquals("D", Scoring.grade(54))
    }

    @Test
    fun 사각형_사이_거리() {
        val a = OrientedRect(Vec2(0.0, 0.0), 1.0, 1.0, 0.0)
        assertEquals(1.0, Scoring.distance(a, OrientedRect(Vec2(3.0, 0.0), 1.0, 1.0, 0.0)), 1e-9)
        assertEquals(0.0, Scoring.distance(a, OrientedRect(Vec2(1.5, 0.0), 1.0, 1.0, 0.0)), 1e-9)
    }

    /** 모범 주행은 어느 모드·차종이든 A 이상이어야 기준이 너무 깐깐하지 않은 것 */
    @Test
    fun 모범_주행은_A등급_이상() {
        for (mode in ParkingMode.entries) for (car in CarPresets.all) {
            val plan = Coach.solve(car, ParkingMaps.random(mode, kotlin.random.Random(1))) ?: continue
            val score = Scoring.score(plan.run, plan.run)!!
            assertTrue("$mode ${car.name}: ${score.total}점", score.total >= 85)
        }
    }

    @Test
    fun 멈춰서_확인한_시간은_주행_시간에서_빠진다() {
        val sim = ParkingSimulator(CarPresets.GRANDEUR, ParkingMaps.perpendicular())
        val input = object : DrivingInput {
            override val steeringDeg = 0.0
            override var direction = Direction.FORWARD
            override var brakePressed = true
        }
        repeat(120) { sim.step(input, dt) }         // 2초 멈춤
        input.brakePressed = false
        repeat(60) { sim.step(input, dt) }          // 1초 움직임
        input.brakePressed = true
        repeat(300) { sim.step(input, dt) }         // 5초 멈춤 (멈추는 데 걸린 시간은 움직인 시간)
        assertEquals(8.0, sim.elapsedSec, 1e-6)
        assertTrue("움직인 시간 ${sim.movingSec}", sim.movingSec in 1.0..1.6)
    }

    @Test
    fun 모범_주행과_같으면_주행_시간_만점() {
        val plan = Coach.solve(CarPresets.GRANDEUR, ParkingMaps.perpendicular())!!
        assertEquals(15, Scoring.score(plan.run, plan.run)!!.points("주행 시간"))
    }
}

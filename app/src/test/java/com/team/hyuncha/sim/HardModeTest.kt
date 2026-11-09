package com.team.hyuncha.sim

import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.DrivingInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

private class HardInput(
    override var steeringDeg: Double = 0.0,
    override var direction: Direction = Direction.FORWARD,
    override var brakePressed: Boolean = true,
) : DrivingInput

class HardModeTest {

    private val dt = 1.0 / 60

    @Test fun 하드_모드는_항상_40초_8km_h_클락션_3번_좁은_칸() {
        repeat(200) { seed ->
            val map = ParkingMaps.randomHard(Random(seed))
            val r = map.rules
            assertTrue(r.isHard)
            assertEquals(ParkingMode.PERPENDICULAR, map.mode)
            assertEquals(40.0, r.timeLimitSec!!, 1e-9)
            assertEquals(8.0, r.creepSpeedMps * 3.6, 1e-9)
            assertEquals(ParkingMaps.HARD_SLOT_W, map.slot.halfWidth * 2, 1e-9)
            assertEquals(3, r.hornTimesSec.size)
            assertEquals("첫 클락션은 4초 고정", 4.0, r.hornTimesSec.first(), 1e-9)
            assertTrue(r.hornTimesSec.all { it in 0.0..40.0 })
            assertTrue("6초 이상 간격", r.hornTimesSec.zipWithNext().all { (a, b) -> b - a >= 6.0 })
            assertNotNull(r.situation)
        }
    }

    @Test fun 랜덤_상황은_세_가지가_모두_나온다() {
        val seen = (0 until 200).map { ParkingMaps.randomHard(Random(it)).rules.situation }.toSet()
        assertEquals(HardSituation.entries.toSet(), seen)
    }

    @Test fun 하드_모드_통로는_일반_모드보다_좁다() {
        val normal = ParkingMaps.perpendicular()
        val normalAisle = normal.slot.center.y - normal.slot.halfLength
        for (situation in HardSituation.entries) {
            val hard = ParkingMaps.hard(4, situation, -1)
            val hardAisle = hard.slot.center.y - hard.slot.halfLength
            assertEquals(0.3, normalAisle - hardAisle, 1e-9)
        }
    }

    @Test fun 상황을_골라서_만들_수_있다() {
        HardSituation.entries.forEach { s -> assertEquals(s, ParkingMaps.randomHard(Random(1), s).rules.situation) }
    }

    @Test fun 상황별_주차장_모양() {
        val enc = ParkingMaps.hard(3, HardSituation.ENCROACH, 1)
        val normal = ParkingMaps.hard(3, HardSituation.DOUBLE_PARKED, 1)
        val neighborX = enc.slot.center.x + ParkingMaps.HARD_SLOT_W
        val moved = enc.parkedCars.first { abs(it.center.x - (neighborX - ParkingMaps.ENCROACH_SHIFT_M)) < 0.01 }
        assertTrue("옆 차가 비뚤어짐", abs(moved.angle - Math.PI / 2) > 0.01)
        // 옆 차 모서리가 목표 칸 안으로 들어와 있음
        assertTrue(moved.corners().any { enc.slot.contains(it) })

        val dbl = ParkingMaps.hard(3, HardSituation.DOUBLE_PARKED, 1, doubleParkOffset = -2.0)
        assertEquals(normal.parkedCars.size, dbl.parkedCars.size)
        assertTrue("통로에 가로로 선 차", dbl.parkedCars.any { abs(it.angle) < 1e-9 && it.center.y < dbl.slot.center.y - dbl.slot.halfLength })
    }

    @Test fun 기둥은_서로_떨어진_두_칸_사이에서_통로를_침범하지_않는다() {
        val gaps = mutableSetOf<Int>()
        repeat(100) { seed ->
            val map = ParkingMaps.randomHard(Random(seed), HardSituation.PILLAR)
            val pillar = map.pillars.single()
            val left = map.bays.last { it.center.x < pillar.center.x }
            val right = map.bays.first { it.center.x > pillar.center.x }
            val leftEdge = left.center.x + left.halfWidth
            val rightEdge = right.center.x - right.halfWidth
            val slotTop = map.slot.center.y - map.slot.halfLength
            assertTrue(map.slot == left || map.slot == right)
            assertTrue(pillar.corners().all { it.x in leftEdge..rightEdge && it.y >= slotTop })
            assertTrue(listOf(leftEdge, rightEdge).all { x -> map.lines.any { abs(it.first.x - x) < 1e-9 } })
            gaps += map.bays.indexOf(right)
        }
        assertTrue("기둥이 여러 칸 사이에 배치됨", gaps.size > 1)
    }

    @Test fun 옆_차_침범을_고르면_매번_한_대가_목표_칸을_침범한다() {
        repeat(200) { seed ->
            val map = ParkingMaps.randomHard(Random(seed), HardSituation.ENCROACH)
            val intruders = map.parkedCars.count { car -> car.corners().any(map.slot::contains) }
            assertEquals("seed=$seed", 1, intruders)
        }
    }

    /** 가장 중요: 나올 수 있는 모든 조합을 모든 차종이 표준 방법으로 제한시간 안에 넣을 수 있어야 함 */
    @Test fun 나올_수_있는_모든_조합을_모든_차종이_주차할_수_있다() {
        val failures = mutableListOf<String>()
        for (target in ParkingMaps.HARD_TARGET_RANGE) for (car in CarPresets.all) {
            val combos = buildList {
                ParkingMaps.pillarSides(target).forEach { add(HardSituation.PILLAR to it) }
                if (target in ParkingMaps.ENCROACH_TARGET_RANGE) {
                    ParkingMaps.ENCROACH_SIDES.forEach { add(HardSituation.ENCROACH to it) }
                }
            }
            for ((s, side) in combos) {
                val plan = Coach.solve(car, ParkingMaps.hard(target, s, side))
                if (plan == null || plan.run.clockSec > 40) failures += "$s side=$side t$target ${car.name}"
            }
            for (off in ParkingMaps.DOUBLE_PARK_OFFSETS) {
                val plan = Coach.solve(car, ParkingMaps.hard(target, HardSituation.DOUBLE_PARKED, 1, doubleParkOffset = off))
                if (plan == null || plan.run.clockSec > 40) failures += "이중주차 off=$off t$target ${car.name}"
            }
        }
        assertTrue("주차 불가 조합: $failures", failures.isEmpty())
    }

    @Test fun 제한시간은_처음_움직인_순간부터_세고_넘으면_시간_초과() {
        val sim = ParkingSimulator(CarPresets.AVANTE, ParkingMaps.hard(3, HardSituation.DOUBLE_PARKED, 1).copy(
            walls = emptyList(), parkedCars = emptyList()))
        val input = HardInput(brakePressed = true)
        repeat(60 * 10) { sim.step(input, dt) }                 // 10초 동안 브레이크만 밟고 대기
        assertNull("아직 안 움직였으면 시간이 안 흐름", sim.startedAtSec)
        assertEquals(40.0, sim.timeLeftSec!!, 1e-9)
        input.brakePressed = false
        repeat(60 * 3) { sim.step(input, dt) }
        assertEquals(8.0, sim.speed * 3.6, 0.05)                 // 3초면 8km/h 도달
        input.brakePressed = true
        repeat(60 * 38) { sim.step(input, dt) }
        assertEquals(SimStatus.TIMEOUT, sim.status)
        assertTrue(sim.clockSec >= 40.0)
    }

    @Test fun 기둥에_부딪히면_기둥이라고_알려준다() {
        val map = ParkingMaps.hard(3, HardSituation.PILLAR, -1)
        val p = map.pillars[0]
        val sim = ParkingSimulator(CarPresets.AVANTE, map)
        // 기둥 바로 위에서 아래(기둥 쪽)를 보고 전진
        sim.reset(CarPose(p.center.x, p.center.y - 4.0, Math.PI / 2))
        val input = HardInput(brakePressed = false)
        repeat(60 * 10) { sim.step(input, dt) }
        assertEquals(SimStatus.COLLIDED, sim.status)
        assertEquals(ObstacleKind.PILLAR, sim.collision!!.obstacle)
        assertEquals("앞 범퍼가 기둥에 닿았어요", sim.collision!!.message)
    }
}

package com.team.hyuncha.sim

import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.DrivingInput
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.tan

/** 모범 주행의 한 단계 (재생할 때 자막으로 보여줌) */
data class CoachStep(val timeSec: Double, val text: String)

/**
 * 모범 주행: 지금 배치·차종으로 표준 순서를 그대로 따라 성공한 주행.
 * @param run 모범 주행을 실제로 돌린 시뮬레이터 (history·events를 그대로 재생에 씀)
 * @param reverseStartX 후진을 시작한 위치 (뒷바퀴 축 x)
 * @param reverseWheelDeg 첫 후진 때 바퀴 각도 (오른쪽 양수)
 * @param leanDeg 평행주차에서 비스듬히 들어간 최대 각도 (직각주차는 90)
 */
class CoachPlan(
    val run: ParkingSimulator,
    val steps: List<CoachStep>,
    val reverseStartX: Double,
    val reverseWheelDeg: Double,
    val leanDeg: Double,
) {
    fun stepAt(timeSec: Double): CoachStep? = steps.lastOrNull { it.timeSec <= timeSec }
}

/**
 * 모범 주행을 찾고, 내 주행과 비교해 피드백을 만드는 코치.
 * 사람이 배우는 표준 순서를 여러 값으로 시도해 보고, 성공한 것 중 가장 깔끔한 주행을 고름.
 * "가장 완벽한" = 표준 방법 중 전환이 적고 칸 가운데에 반듯하게 들어간 것 (수학적 최적 경로는 아님).
 */
object Coach {
    private const val DT = 1.0 / 60

    fun solve(car: CarSpec, map: ParkingMap): CoachPlan? = when (map.mode) {
        ParkingMode.PERPENDICULAR -> best(perpendicularCandidates(car, map))
        ParkingMode.PARALLEL -> best(parallelCandidates(car, map))
    }

    /**
     * 성공한 주행 중 가장 깔끔한 것: 전환 횟수 → 칸 중앙 거리·각도 오차 순.
     * 모든 조합을 다 돌리면 차량 화면에서 몇 초 걸릴 수 있어서, 성공이 3개 모이면 그중에서 고름.
     * 후보 순서를 사람이 흔히 쓰는 값(핸들 끝까지, 45°)부터 시도하도록 짜 두었음.
     */
    private fun best(candidates: Sequence<CoachPlan?>): CoachPlan? =
        candidates.filterNotNull().take(3).toList().minByOrNull { p ->
            val r = p.run.result!!
            r.directionChanges * 100.0 + r.centerOffsetM * 10 + r.angleErrorDeg
        }

    // ───────── 직각주차: 앞으로 가서 칸을 지나 멈춤 → 핸들 오른쪽 끝 후진 → 나란해지면 곧게 후진 ─────────

    private fun perpendicularCandidates(car: CarSpec, map: ParkingMap) = sequence {
        for (frac in listOf(1.0, 0.85)) {
            for (k in 0..80) {
                yield(tryPerpendicular(car, map, stopX = map.slot.center.x - 1.5 + k * 0.1, steerFrac = frac))
            }
        }
    }

    private fun tryPerpendicular(car: CarSpec, map: ParkingMap, stopX: Double, steerFrac: Double): CoachPlan? {
        val s = Script(ParkingSimulator(car, map))
        val steps = mutableListOf(CoachStep(0.0, "1. 앞으로 천천히 가서, 목표 칸을 조금 지나 멈춰요"))
        s.go(Direction.FORWARD, 0.0)
        if (!s.runUntil { s.sim.pose.x + s.brakeDistance() >= stopX }) return null
        if (!s.stop()) return null
        val reverseStartX = s.sim.pose.x
        val steer = car.maxSteeringWheelDeg * steerFrac
        steps += CoachStep(s.sim.elapsedSec, "2. 핸들을 오른쪽${if (steerFrac >= 1.0) " 끝까지" else "으로 크게"} 돌리고 천천히 후진해요")
        s.go(Direction.REVERSE, steer)
        if (!s.runUntil { s.sim.pose.heading <= -PI / 2 + 0.005 }) return null
        steps += CoachStep(s.sim.elapsedSec, "3. 차가 칸과 나란해지면 핸들을 풀고 곧게 후진해요")
        s.go(Direction.REVERSE, 0.0)
        if (!s.runUntil { s.sim.bodyRect().center.y + s.brakeDistance() >= map.slot.center.y }) return null
        steps += CoachStep(s.sim.elapsedSec, "4. 칸 가운데에 오면 브레이크!")
        if (!s.stop() || s.sim.status != SimStatus.PARKED) return null
        return CoachPlan(s.sim, steps, reverseStartX, steer / car.steeringRatio, leanDeg = 90.0)
    }

    // ───────── 평행주차: 앞차 옆에 섬 → 오른쪽 끝 후진 → (곧게) → 왼쪽 끝 후진 → 앞뒤 맞춤 ─────────

    private fun parallelCandidates(car: CarSpec, map: ParkingMap) = sequence {
        val slotRight = map.slot.center.x + map.slot.halfLength
        for (angle in listOf(45.0, 40.0, 50.0, 35.0, 55.0)) {
            for (straight in listOf(0.0, 0.4, 0.8, 1.2)) {
                for (k in 0..16) {
                    yield(tryParallel(car, map, stopX = slotRight - 1.0 + k * 0.25, leanDeg = angle, straight = straight))
                }
            }
        }
    }

    private fun tryParallel(car: CarSpec, map: ParkingMap, stopX: Double, leanDeg: Double, straight: Double): CoachPlan? {
        val s = Script(ParkingSimulator(car, map))
        val max = car.maxSteeringWheelDeg
        val radius = car.wheelBaseM / tan(Math.toRadians(car.maxWheelAngleDeg))
        val steps = mutableListOf(CoachStep(0.0, "1. 앞으로 가서 목표 칸 앞차 옆에 나란히 멈춰요"))
        s.go(Direction.FORWARD, 0.0)
        if (!s.runUntil { s.sim.pose.x + s.brakeDistance() >= stopX }) return null
        if (!s.stop()) return null
        val reverseStartX = s.sim.pose.x
        steps += CoachStep(s.sim.elapsedSec, "2. 핸들을 오른쪽 끝까지 돌리고 후진해요")
        s.go(Direction.REVERSE, max)
        if (!s.runUntil { s.sim.pose.heading <= -Math.toRadians(leanDeg) }) return null
        if (straight > 0) {
            steps += CoachStep(s.sim.elapsedSec, "3. 차가 약 ${leanDeg.toInt()}° 비스듬해지면 핸들을 풀고 조금 곧게 후진해요")
            s.go(Direction.REVERSE, 0.0)
            val sx = s.sim.pose.x
            val sy = s.sim.pose.y
            if (!s.runUntil { hypot(s.sim.pose.x - sx, s.sim.pose.y - sy) >= straight }) return null
        }
        steps += CoachStep(s.sim.elapsedSec, "${steps.size + 1}. 핸들을 왼쪽 끝까지 돌리고 후진해서 차를 길과 나란히 맞춰요")
        s.go(Direction.REVERSE, -max)
        if (!s.runUntil { s.sim.pose.heading + s.brakeDistance() / radius >= 0.0 }) return null
        if (!s.stop()) return null
        if (s.sim.status != SimStatus.PARKED) {
            steps += CoachStep(s.sim.elapsedSec, "${steps.size + 1}. 핸들을 풀고 앞으로 조금 가서 칸 가운데에 맞춰요")
            s.go(Direction.FORWARD, 0.0)
            if (!s.runUntil { s.sim.bodyRect().center.x + s.brakeDistance() >= map.slot.center.x }) return null
            if (!s.stop()) return null
            s.hold(10)
        }
        if (s.sim.status != SimStatus.PARKED) return null
        return CoachPlan(s.sim, steps, reverseStartX, car.maxWheelAngleDeg, leanDeg)
    }

    /** 사람 대신 핸들·브레이크·방향을 조작하는 대본 */
    private class Script(val sim: ParkingSimulator) {
        private val input = object : DrivingInput {
            override var steeringDeg = 0.0
            override var direction = Direction.FORWARD
            override var brakePressed = true
        }

        fun brakeDistance() = sim.speed * sim.speed / (2 * ParkingSimulator.BRAKE_DECEL)

        fun go(dir: Direction, steeringDeg: Double) {
            input.direction = dir
            input.steeringDeg = steeringDeg
            input.brakePressed = false
        }

        /** 조건이 될 때까지 진행. 부딪히면 실패, 그 사이 주차 성공 판정이 나면 성공으로 봄 */
        fun runUntil(maxSteps: Int = 4000, until: () -> Boolean): Boolean {
            repeat(maxSteps) {
                if (sim.status == SimStatus.COLLIDED) return false
                if (sim.status == SimStatus.PARKED || until()) return true
                sim.step(input, DT)
            }
            return false
        }

        fun stop(): Boolean {
            input.brakePressed = true
            return runUntil(400) { abs(sim.speed) < 1e-3 }
        }

        fun hold(frames: Int) = repeat(frames) { sim.step(input, DT) }
    }

    // ───────── 피드백: 내 주행을 모범 주행의 핵심 값과 비교 ─────────

    /**
     * 내 주행에서 문제였던 점을 최대 3줄로. 표준 순서(전진 → 후진) 기준으로 비교하므로,
     * 완전히 다른 방법으로 움직였다면 일반적인 안내만 나옴.
     */
    fun feedback(mine: ParkingSimulator, plan: CoachPlan?): List<String> {
        val lines = mutableListOf<String>()
        val firstReverse = mine.events.filterIsInstance<SimEvent.DirectionChanged>().firstOrNull { it.to == Direction.REVERSE }

        if (plan == null) {
            if (mine.status == SimStatus.COLLIDED) lines += "다시 보기로 어디서 닿았는지 확인해 보세요."
            return lines
        }
        if (firstReverse == null) {
            lines += "후진을 하지 않았어요. 모범 주행처럼 먼저 앞으로 가서 멈춘 뒤 후진해요."
            return lines
        }

        // 1) 후진을 시작한 위치
        val startX = mine.frameAt(firstReverse.timeSec).pose.x
        val dx = startX - plan.reverseStartX
        val where = if (mine.map.mode == ParkingMode.PARALLEL) "앞차 옆" else "칸"
        if (dx < -0.6) lines += "${where}을 덜 지나서 후진을 시작했어요. 모범보다 ${m(-dx)} 더 가서 멈춰요."
        else if (dx > 0.6) lines += "${where}을 너무 지나서 후진을 시작했어요. 모범보다 ${m(dx)} 덜 가서 멈춰요."

        // 2) 첫 후진 때 핸들
        val nextChange = mine.events.filterIsInstance<SimEvent.DirectionChanged>().firstOrNull { it.timeSec > firstReverse.timeSec }
        val reverseFrames = mine.history.filter {
            it.timeSec >= firstReverse.timeSec && (nextChange == null || it.timeSec < nextChange.timeSec)
        }
        val maxRight = reverseFrames.maxOfOrNull { it.wheelAngleDeg } ?: 0.0
        val maxLeft = reverseFrames.minOfOrNull { it.wheelAngleDeg } ?: 0.0
        when {
            maxRight < 5 && maxLeft < -5 ->
                lines += "후진할 때 핸들을 반대(왼쪽)로 돌렸어요. 칸이 오른쪽에 있으면 핸들도 오른쪽으로 돌려요."
            maxRight < plan.reverseWheelDeg * 0.7 ->
                lines += "후진할 때 핸들을 덜 돌렸어요. 바퀴 ${deg(maxRight)} → 모범 ${deg(plan.reverseWheelDeg)} (오른쪽 끝까지)."
        }

        // 3) 평행주차: 비스듬히 들어간 각도
        if (mine.map.mode == ParkingMode.PARALLEL) {
            val lean = -(reverseFrames.minOfOrNull { it.pose.heading } ?: 0.0)
            val leanDeg = Math.toDegrees(lean)
            if (leanDeg < plan.leanDeg - 10) lines += "충분히 비스듬해지기 전에 핸들을 반대로 꺾었어요. ${deg(leanDeg)} → 모범 ${deg(plan.leanDeg)}."
            else if (leanDeg > plan.leanDeg + 15) lines += "너무 비스듬하게 들어갔어요. ${deg(leanDeg)} → 모범 ${deg(plan.leanDeg)}."
        }

        // 4) 성공했을 때는 마무리 품질
        if (mine.status == SimStatus.PARKED) {
            val r = mine.result!!
            val extra = r.directionChanges - plan.run.directionChanges
            if (extra > 0) lines += "모범보다 전진·후진을 ${extra}번 더 바꿨어요. 위 순서대로 하면 한 번에 들어가요."
            if (r.angleErrorDeg > 3) lines += "칸과 ${deg(r.angleErrorDeg)} 비뚤게 섰어요. 핸들을 푸는 때를 조금 조정해 보세요."
            if (lines.isEmpty()) lines += "모범 주행과 거의 같아요. 잘했어요!"
        }

        if (lines.isEmpty() && mine.status == SimStatus.COLLIDED) {
            lines += "순서는 모범과 비슷했어요. 모범 주행을 보며 핸들을 풀고 꺾는 때를 맞춰 보세요."
        }
        return lines.take(3)
    }

    private fun m(v: Double) = String.format(Locale.US, "%.1fm", v)
    private fun deg(v: Double) = "${v.toInt()}°"
}

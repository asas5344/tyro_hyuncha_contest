package com.team.hyuncha.sim

import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.DrivingInput
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.tan

/** 모범 주행의 한 단계 (재생할 때 자막으로 보여줌) */
data class CoachStep(val timeSec: Double, val text: String)

/**
 * 모범 주행: 지금 배치·차종으로 표준 순서를 그대로 따라 성공한 주행.
 * @param run 모범 주행을 실제로 돌린 시뮬레이터 (history·events를 그대로 재생에 씀)
 * @param actionStartX 핵심 동작을 시작한 위치 (뒷바퀴 축 x). 직각·평행 = 후진 시작, 사선 = 핸들을 꺾기 시작
 * @param actionWheelDeg 핵심 동작 때 바퀴 각도 (오른쪽 양수). 직각·평행 = 첫 후진, 사선 = 들어갈 때
 * @param leanDeg 평행주차에서 비스듬히 들어간 최대 각도 (직각주차는 90)
 */
class CoachPlan(
    val run: ParkingSimulator,
    val steps: List<CoachStep>,
    val actionStartX: Double,
    val actionWheelDeg: Double,
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

    /** 0..last를 가운데부터 바깥으로 (가운데 값이 성공할 가능성이 커서 먼저 시도) */
    private fun centerOut(last: Int): List<Int> = (0..last).sortedBy { kotlin.math.abs(it - last / 2) }

    fun solve(car: CarSpec, map: ParkingMap): CoachPlan? = when (map.mode) {
        ParkingMode.PERPENDICULAR -> best(perpendicularCandidates(car, map))
        ParkingMode.PARALLEL -> best(parallelCandidates(car, map))
        ParkingMode.DIAGONAL -> best(diagonalCandidates(car, map))
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
            for (k in centerOut(80)) {
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
        val actionStartX = s.sim.pose.x
        val steer = car.maxSteeringWheelDeg * steerFrac
        steps += CoachStep(s.sim.elapsedSec, "2. 핸들을 오른쪽${if (steerFrac >= 1.0) " 끝까지" else "으로 크게"} 돌리고 천천히 후진해요")
        s.go(Direction.REVERSE, steer)
        if (!s.runUntil { s.sim.pose.heading <= -PI / 2 + 0.005 }) return null
        steps += CoachStep(s.sim.elapsedSec, "3. 차가 칸과 나란해지면 핸들을 풀고 곧게 후진해요")
        s.go(Direction.REVERSE, 0.0)
        if (!s.runUntil { s.sim.bodyRect().center.y + s.brakeDistance() >= map.slot.center.y }) return null
        steps += CoachStep(s.sim.elapsedSec, "4. 칸 가운데에 오면 브레이크!")
        if (!s.stop() || s.sim.status != SimStatus.PARKED) return null
        return CoachPlan(s.sim, steps, actionStartX, steer / car.steeringRatio, leanDeg = 90.0)
    }

    // ───────── 평행주차: 앞차 옆에 섬 → 오른쪽 끝 후진 → (곧게) → 왼쪽 끝 후진 → 앞뒤 맞춤 ─────────

    private fun parallelCandidates(car: CarSpec, map: ParkingMap) = sequence {
        val slotRight = map.slot.center.x + map.slot.halfLength
        for (angle in listOf(45.0, 40.0, 50.0, 35.0, 55.0)) {
            for (straight in listOf(0.0, 0.4, 0.8, 1.2)) {
                for (k in centerOut(16)) {
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
        val actionStartX = s.sim.pose.x
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
        return CoachPlan(s.sim, steps, actionStartX, car.maxWheelAngleDeg, leanDeg)
    }

    // ───────── 사선주차: 통로를 따라가다 칸 앞에서 핸들 오른쪽 → 칸과 나란해지면 풀고 곧게 → 가운데에서 브레이크 ─────────

    private fun diagonalCandidates(car: CarSpec, map: ParkingMap) = sequence {
        // 칸 입구(통로 경계와 만나는 곳)보다 앞에서 꺾기 시작
        val entranceX = map.slot.center.x - map.slot.halfLength * kotlin.math.cos(map.slot.angle)
        for (frac in listOf(1.0, 0.85, 0.7)) {
            for (k in centerOut(90)) {
                yield(tryDiagonal(car, map, turnX = entranceX - 7.0 + k * 0.1, steerFrac = frac))
            }
        }
    }

    private fun tryDiagonal(car: CarSpec, map: ParkingMap, turnX: Double, steerFrac: Double): CoachPlan? {
        val s = Script(ParkingSimulator(car, map))
        val slotAxis = Vec2.unit(map.slot.angle)
        val steps = mutableListOf(CoachStep(0.0, "1. 통로를 따라 천천히 앞으로 가요"))
        s.go(Direction.FORWARD, 0.0)
        if (!s.runUntil { s.sim.pose.x >= turnX }) return null
        val turnStartX = s.sim.pose.x
        val steer = car.maxSteeringWheelDeg * steerFrac
        steps += CoachStep(s.sim.elapsedSec, "2. 목표 칸에 가까워지면 핸들을 오른쪽${if (steerFrac >= 1.0) " 끝까지" else "으로 크게"} 돌려요")
        s.go(Direction.FORWARD, steer)
        if (!s.runUntil { s.sim.pose.heading >= map.slot.angle - 0.005 }) return null
        steps += CoachStep(s.sim.elapsedSec, "3. 차가 칸과 나란해지면 핸들을 풀고 곧게 들어가요")
        s.go(Direction.FORWARD, 0.0)
        if (!s.runUntil { ((s.sim.bodyRect().center - map.slot.center) dot slotAxis) + s.brakeDistance() >= 0.0 }) return null
        steps += CoachStep(s.sim.elapsedSec, "4. 칸 가운데에 오면 브레이크!")
        if (!s.stop() || s.sim.status != SimStatus.PARKED) return null
        return CoachPlan(s.sim, steps, turnStartX, steer / car.steeringRatio, leanDeg = Math.toDegrees(map.slot.angle))
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

    /** 내가 움직인 구간 하나: 같은 방향으로 계속 움직인 동안 */
    private class Segment(val direction: Direction, val frames: List<SimFrame>) {
        val start get() = frames.first()
    }

    /**
     * 기록을 실제로 움직인 방향(앞·뒤)별 구간으로 나눔.
     * 방향 버튼이 아니라 실제 이동으로 판단하므로, 처음부터 후진한 경우도 잡힘.
     */
    private fun segments(history: List<SimFrame>): List<Segment> {
        val result = mutableListOf<Segment>()
        var current = mutableListOf<SimFrame>()
        var dir: Direction? = null
        for ((a, b) in history.zipWithNext()) {
            val dx = b.pose.x - a.pose.x
            val dy = b.pose.y - a.pose.y
            if (hypot(dx, dy) < 1e-4) continue                       // 멈춰 있음
            val along = dx * kotlin.math.cos(a.pose.heading) + dy * kotlin.math.sin(a.pose.heading)
            val d = if (along >= 0) Direction.FORWARD else Direction.REVERSE
            if (d != dir) {
                if (current.isNotEmpty() && dir != null) result += Segment(dir, current)
                current = mutableListOf(a)
                dir = d
            }
            current += b
        }
        if (current.isNotEmpty() && dir != null) result += Segment(dir, current)
        return result
    }

    private fun travelled(seg: Segment): Double =
        seg.frames.zipWithNext().sumOf { (a, b) -> hypot(b.pose.x - a.pose.x, b.pose.y - a.pose.y) }

    /**
     * 내 주행에 대한 피드백 (최대 3줄).
     *
     * 틀린 조언을 하지 않는 게 가장 중요하므로:
     * - 성공했으면 방법을 따지지 않음 (앞으로 넣든, 여러 번 맞추든 성공은 성공). 마무리 품질만 짧게
     * - 실패했을 때만 모범과 비교하고, 그것도 내 주행이 표준 순서(앞으로 간 뒤 출발 방향 그대로 후진)를
     *   따랐을 때만 숫자로 비교. 아니면 일반적인 안내만
     */
    fun feedback(mine: ParkingSimulator, plan: CoachPlan?): List<String> = when (mine.status) {
        SimStatus.PARKED -> successFeedback(mine, plan)
        SimStatus.COLLIDED -> collisionFeedback(mine, plan)
        SimStatus.TIMEOUT -> timeoutFeedback(mine, plan)
        SimStatus.DRIVING -> emptyList()
    }

    /** 제한시간 초과: 모범 주행은 몇 초에 끝났는지, 전환을 많이 했는지 */
    private fun timeoutFeedback(mine: ParkingSimulator, plan: CoachPlan?): List<String> {
        val lines = mutableListOf("제한 시간 ${mine.map.rules.timeLimitSec?.toInt() ?: 0}초 안에 주차하지 못했어요.")
        plan?.run?.let { model -> lines += "모범 주행은 움직이기 시작해서 ${model.clockSec.roundToInt()}초 만에 끝났어요. 모범 주행을 보고 순서를 익혀 보세요." }
        if (mine.directionChanges >= 3) lines += "전진·후진을 ${mine.directionChanges}번 바꿨어요. 멈추는 위치를 정확히 맞추면 한 번에 들어갈 수 있어요."
        return lines.take(3)
    }

    private fun successFeedback(mine: ParkingSimulator, plan: CoachPlan?): List<String> {
        val r = mine.result ?: return emptyList()
        val lines = mutableListOf<String>()
        if (r.angleErrorDeg > 3) lines += "칸과 ${deg(r.angleErrorDeg)} 정도 비뚤게 섰어요. 마지막에 핸들을 푸는 때를 조금 조정해 보세요."
        if (r.centerOffsetM > 0.4) lines += "칸 가운데에서 ${m(r.centerOffsetM)} 벗어났어요."
        val modelChanges = plan?.run?.directionChanges
        if (modelChanges != null && r.directionChanges >= modelChanges + 2) {
            lines += "전진·후진을 ${r.directionChanges}번 바꿨어요. 모범 주행 순서로 하면 더 적게 바꾸고도 넣을 수 있어요."
        }
        if (lines.isEmpty()) lines += "깔끔하게 주차했어요. 잘했어요!"
        return lines.take(3)
    }

    private fun collisionFeedback(mine: ParkingSimulator, plan: CoachPlan?): List<String> {
        if (mine.map.mode == ParkingMode.DIAGONAL) return diagonalCollisionFeedback(mine, plan)
        val segs = segments(mine.history).filter { travelled(it) > 0.15 }   // 아주 조금 움찔한 건 무시
        val hitDirection = segs.lastOrNull()?.direction
        val general = "모범 주행을 보고 같은 순서로 따라 해 보세요."

        if (plan == null || segs.isEmpty()) return listOf(general)

        // 앞으로 가다가 닿음 (처음 전진이든 나중에 다시 전진이든)
        if (hitDirection == Direction.FORWARD) {
            return if (segs.size == 1) listOf(
                "앞으로 가다가 닿았어요. 앞쪽 센서가 노랗게 바뀌면 브레이크를 밟아 멈춰요.",
                "모범 주행처럼 칸을 조금 지나 멈춘 뒤, 후진으로 넣으면 더 쉬워요.",
            ) else listOf("앞으로 다시 맞추다가 닿았어요. 앞쪽 센서를 보며 천천히 움직여요.", general)
        }

        // 여기부터는 후진하다 닿음. 표준 순서(전진 1번 → 후진)이고, 후진을 시작할 때 차가 출발 방향 그대로였을 때만 숫자로 비교
        val first = segs[0]
        val firstReverse = segs.getOrNull(1)
        val startHeading = mine.map.start.heading
        val standardLike = first.direction == Direction.FORWARD && firstReverse?.direction == Direction.REVERSE &&
            segs.size == 2 && abs(angleDiff(firstReverse.start.pose.heading, startHeading)) < Math.toRadians(15.0)
        if (!standardLike || firstReverse == null) {
            return listOf("모범 주행과 다른 순서로 움직여서 자세히 비교하기 어려워요.", general)
        }

        val lines = mutableListOf<String>()

        // 1) 후진을 시작한 위치 (출발 방향을 따라 잰 거리)
        val dx = firstReverse.start.pose.x - plan.actionStartX
        if (dx < -0.6) lines += "후진을 일찍 시작했어요. 모범 주행은 ${m(-dx)} 더 앞으로 가서 멈춘 뒤 후진해요."
        else if (dx > 0.6) lines += "후진을 늦게 시작했어요. 모범 주행은 ${m(dx)} 덜 가서 멈춘 뒤 후진해요."

        // 2) 후진하며 핸들: 오른쪽이 양수. 실제로 1m 이상 후진한 경우만
        if (travelled(firstReverse) > 1.0) {
            val maxRight = firstReverse.frames.maxOf { it.wheelAngleDeg }
            val maxLeft = firstReverse.frames.minOf { it.wheelAngleDeg }
            val firstTurn = if (mine.map.mode == ParkingMode.PARALLEL) "처음 후진할 때" else "후진할 때"
            when {
                maxRight < 5 && maxLeft < -5 ->
                    lines += "${firstTurn} 핸들을 왼쪽으로 돌렸어요. 목표 칸이 오른쪽에 있어서 모범 주행은 오른쪽으로 돌려요."
                maxRight < plan.actionWheelDeg * 0.7 ->
                    lines += "${firstTurn} 핸들을 덜 돌렸어요. 바퀴 ${deg(maxRight)}, 모범 주행은 ${deg(plan.actionWheelDeg)}예요."
            }
        }

        // 3) 평행주차: 오른쪽으로 꺾어 후진하며 비스듬해진 정도
        if (mine.map.mode == ParkingMode.PARALLEL && travelled(firstReverse) > 1.0) {
            val leanDeg = Math.toDegrees(-firstReverse.frames.minOf { angleDiff(it.pose.heading, startHeading) })
            if (leanDeg > 5) {
                if (leanDeg < plan.leanDeg - 10) lines += "충분히 비스듬해지기 전에 닿았거나 반대로 꺾었어요. ${deg(leanDeg)}, 모범 주행은 약 ${deg(plan.leanDeg)}예요."
                else if (leanDeg > plan.leanDeg + 15) lines += "너무 비스듬하게 들어갔어요. ${deg(leanDeg)}, 모범 주행은 약 ${deg(plan.leanDeg)}예요."
            }
        }

        if (lines.isEmpty()) lines += "순서는 모범 주행과 비슷했어요. 모범 주행을 보며 핸들을 꺾고 푸는 때를 맞춰 보세요."
        return lines.take(3)
    }

    /**
     * 사선주차 실패 피드백. 표준 순서는 "앞으로만 가며 오른쪽으로 꺾어 들어감"이므로,
     * 앞으로만 움직였고 꺾기 전까지 출발 방향 그대로였을 때만 숫자로 비교.
     */
    private fun diagonalCollisionFeedback(mine: ParkingSimulator, plan: CoachPlan?): List<String> {
        val general = "모범 주행을 보고 같은 순서로 따라 해 보세요."
        val segs = segments(mine.history).filter { travelled(it) > 0.15 }
        if (plan == null || segs.isEmpty()) return listOf(general)
        if (segs.size != 1 || segs[0].direction != Direction.FORWARD) {
            return listOf("모범 주행과 다른 순서로 움직여서 자세히 비교하기 어려워요. 사선 주차는 앞으로 비스듬히 들어가요.", general)
        }
        val frames = segs[0].frames
        // 핸들을 오른쪽으로 꺾기 시작한 곳 (바퀴 8° 이상)
        val turnFrame = frames.firstOrNull { it.wheelAngleDeg > 8 }
        val leftTurn = frames.any { it.wheelAngleDeg < -8 }
        val startHeading = mine.map.start.heading
        if (turnFrame == null) {
            return listOf(
                if (leftTurn) "핸들을 왼쪽으로 꺾었어요. 사선 칸은 오른쪽에 있어서 모범 주행은 오른쪽으로 꺾어요."
                else "핸들을 꺾지 않고 곧게 가다가 닿았어요. 목표 칸에 가까워지면 오른쪽으로 꺾어요.",
            )
        }
        if (abs(angleDiff(turnFrame.pose.heading, startHeading)) > Math.toRadians(15.0)) {
            return listOf("모범 주행과 다른 순서로 움직여서 자세히 비교하기 어려워요.", general)
        }

        val lines = mutableListOf<String>()
        val dx = turnFrame.pose.x - plan.actionStartX
        if (dx < -0.6) lines += "핸들을 일찍 꺾었어요. 모범 주행은 ${m(-dx)} 더 가서 꺾어요."
        else if (dx > 0.6) lines += "핸들을 늦게 꺾었어요. 모범 주행은 ${m(dx)} 먼저 꺾어요."
        val maxRight = frames.maxOf { it.wheelAngleDeg }
        if (maxRight < plan.actionWheelDeg * 0.7) {
            lines += "핸들을 덜 꺾었어요. 바퀴 ${deg(maxRight)}, 모범 주행은 ${deg(plan.actionWheelDeg)}예요."
        }
        if (lines.isEmpty()) lines += "순서는 모범 주행과 비슷했어요. 모범 주행을 보며 핸들을 꺾고 푸는 때를 맞춰 보세요."
        return lines.take(3)
    }

    /** 두 방향의 차이(라디안, -π~π) */
    private fun angleDiff(a: Double, b: Double): Double {
        var d = (a - b) % (2 * PI)
        if (d > PI) d -= 2 * PI
        if (d < -PI) d += 2 * PI
        return d
    }

    private fun m(v: Double) = String.format(Locale.US, "%.1fm", v)
    private fun deg(v: Double) = "${v.toInt()}°"
}

package com.team.hyuncha.sim

import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.DrivingInput
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

enum class SimStatus { DRIVING, COLLIDED, PARKED }

/** 주차 성공 시 채점 결과 */
data class ParkingResult(
    val angleErrorDeg: Double,   // 칸과 차가 어긋난 각도
    val centerOffsetM: Double,   // 칸 중앙에서 벗어난 거리
    val directionChanges: Int,   // 전진·후진 전환 횟수 (적을수록 좋음)
    val elapsedSec: Double,
)

/**
 * 운동학적 자전거 모델로 차를 움직이는 주차 시뮬레이터.
 * 주차는 아주 느린 속도라 타이어 미끄러짐을 무시해도 돼서 이 모델이 잘 맞음.
 * 입력은 DrivingInput으로만 받으므로 터치·차량·휠 어느 입력이든 그대로 동작함.
 */
class ParkingSimulator(val car: CarSpec, val map: ParkingMap) {

    var pose = map.start; private set
    var speed = 0.0; private set               // m/s. 전진 양수, 후진 음수
    var wheelAngleDeg = 0.0; private set       // 현재 앞바퀴 각도 (그리기용)
    var status = SimStatus.DRIVING; private set
    var result: ParkingResult? = null; private set
    var directionChanges = 0; private set
    var elapsedSec = 0.0; private set

    /** 지나온 경로 (화면 궤적 표시, 다시 보여주기에 사용) */
    val trail = mutableListOf(map.start)

    private var lastMoveDirection: Direction? = null

    /** 현재(또는 주어진 위치의) 차체 사각형 */
    fun bodyRect(p: CarPose = pose): OrientedRect {
        // 뒷바퀴 축에서 차체 중심까지의 거리
        val toCenter = car.lengthM / 2 - car.rearOverhangM
        return OrientedRect(
            center = Vec2(p.x, p.y) + Vec2.unit(p.heading) * toCenter,
            halfLength = car.lengthM / 2,
            halfWidth = car.widthM / 2,
            angle = p.heading,
        )
    }

    val isInsideSlot: Boolean get() = map.slot.containsRect(bodyRect())

    /** 칸 방향과 차 방향의 차이(도). 앞으로 넣든 뒤로 넣든 같은 것으로 봄 */
    val angleErrorDeg: Double
        get() {
            var d = (pose.heading - map.slot.angle) % PI
            if (d > PI / 2) d -= PI
            if (d < -PI / 2) d += PI
            return Math.toDegrees(abs(d))
        }

    /**
     * 한 프레임 진행.
     * @param dt 프레임 간격(초)
     */
    fun step(input: DrivingInput, dt: Double) {
        if (status != SimStatus.DRIVING) return
        elapsedSec += dt

        // 1) 핸들 각도 → 바퀴 각도 (끝까지 돌려도 최대 조향각 이상은 안 꺾임)
        wheelAngleDeg = (input.steeringDeg / car.steeringRatio)
            .coerceIn(-car.maxWheelAngleDeg, car.maxWheelAngleDeg)
        val wheelAngle = Math.toRadians(wheelAngleDeg)

        // 2) 목표 속도: 브레이크를 밟으면 0, 떼면 크리프 속도
        val target = when {
            input.brakePressed -> 0.0
            input.direction == Direction.FORWARD -> CREEP_SPEED
            else -> -CREEP_SPEED
        }
        // 순간적으로 바뀌지 않고 가속·감속 한계 안에서 목표 속도로 다가감
        val limit = (if (abs(target) < abs(speed) || target * speed < 0) BRAKE_DECEL else CREEP_ACCEL) * dt
        speed += (target - speed).coerceIn(-limit, limit)

        // 전진·후진 전환 횟수: 실제로 움직인 방향이 바뀔 때만 셈
        if (abs(speed) > MOVING_THRESHOLD) {
            val moving = if (speed > 0) Direction.FORWARD else Direction.REVERSE
            if (lastMoveDirection != null && moving != lastMoveDirection) directionChanges++
            lastMoveDirection = moving
        }

        // 3) 자전거 모델로 위치·방향 갱신
        //    후진이면 speed가 음수라 회전 방향도 자동으로 반대가 됨 (따로 뒤집으면 안 됨)
        val next = CarPose(
            x = pose.x + speed * cos(pose.heading) * dt,
            y = pose.y + speed * sin(pose.heading) * dt,
            heading = pose.heading + (speed / car.wheelBaseM) * tan(wheelAngle) * dt,
        )

        // 4) 충돌 판정: 부딪히면 그 자리에서 멈추고 실패
        val nextBody = bodyRect(next)
        pose = next
        recordTrail()
        if (map.obstacles.any { it.intersects(nextBody) }) {
            speed = 0.0
            status = SimStatus.COLLIDED
            return
        }

        // 5) 성공 판정: 브레이크로 멈춘 상태에서 칸 안에 들어와 있고 각도가 맞으면 성공
        if (input.brakePressed && abs(speed) < STOPPED_THRESHOLD &&
            isInsideSlot && angleErrorDeg <= MAX_ANGLE_ERROR_DEG
        ) {
            status = SimStatus.PARKED
            val body = bodyRect()
            result = ParkingResult(
                angleErrorDeg = angleErrorDeg,
                centerOffsetM = hypot(body.center.x - map.slot.center.x, body.center.y - map.slot.center.y),
                directionChanges = directionChanges,
                elapsedSec = elapsedSec,
            )
        }
    }

    /** 처음 상태로 되돌림 (start를 주면 그 위치에서 시작 — 테스트용) */
    fun reset(start: CarPose = map.start) {
        pose = start
        speed = 0.0
        wheelAngleDeg = 0.0
        status = SimStatus.DRIVING
        result = null
        directionChanges = 0
        elapsedSec = 0.0
        lastMoveDirection = null
        trail.clear()
        trail.add(start)
    }

    /** 일정 거리 이상 움직였을 때만 경로에 추가 (매 프레임 저장하면 너무 많아짐) */
    private fun recordTrail() {
        val last = trail.last()
        if (hypot(pose.x - last.x, pose.y - last.y) >= TRAIL_STEP_M) trail.add(pose)
    }

    companion object {
        const val CREEP_SPEED = 1.2           // 크리프 속도 m/s (약 4km/h)
        const val CREEP_ACCEL = 1.0           // 브레이크 뗐을 때 가속 m/s²
        const val BRAKE_DECEL = 3.0           // 브레이크 밟았을 때 감속 m/s²
        const val MAX_ANGLE_ERROR_DEG = 7.0   // 성공으로 인정하는 최대 각도 오차
        private const val MOVING_THRESHOLD = 0.05
        private const val STOPPED_THRESHOLD = 0.01
        private const val TRAIL_STEP_M = 0.1
    }
}

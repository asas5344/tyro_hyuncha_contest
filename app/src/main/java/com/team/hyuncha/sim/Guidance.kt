package com.team.hyuncha.sim

import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.input.Direction
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/**
 * 주차를 돕는 안내 계산: 예상 진행선(후방 카메라 가이드선)과 주차 센서.
 * 그리기와 분리해 두어서 단위 테스트로 확인할 수 있음.
 */
object Guidance {

    /** 예상 진행선 한 점: 차 폭의 왼쪽·오른쪽 끝이 지나갈 위치 */
    data class GuidePoint(val left: Vec2, val right: Vec2, val distanceM: Double)

    /**
     * 지금 바퀴 각도를 그대로 유지하고 distanceM만큼 갔을 때 범퍼 양 끝이 지나갈 길.
     * 후진이면 뒤 범퍼, 전진이면 앞 범퍼 기준 (실제 차 후방·전방 카메라 가이드선과 같음).
     */
    fun predictPath(
        car: CarSpec,
        pose: CarPose,
        wheelAngleDeg: Double,
        direction: Direction,
        distanceM: Double = 3.5,
        stepM: Double = 0.1,
    ): List<GuidePoint> {
        val sign = if (direction == Direction.FORWARD) 1.0 else -1.0
        val curvature = tan(Math.toRadians(wheelAngleDeg)) / car.wheelBaseM
        // 뒷바퀴 축에서 범퍼까지 (전진 = 앞 범퍼, 후진 = 뒤 범퍼)
        val bumperOffset = if (sign > 0) car.lengthM - car.rearOverhangM else -car.rearOverhangM
        val halfWidth = car.widthM / 2

        val points = mutableListOf<GuidePoint>()
        var p = pose
        var travelled = 0.0
        while (true) {
            val forward = Vec2.unit(p.heading)
            val side = Vec2(-sin(p.heading), cos(p.heading))   // 오른쪽 (y가 아래로 양수인 화면 좌표)
            val bumper = Vec2(p.x, p.y) + forward * bumperOffset
            points += GuidePoint(left = bumper - side * halfWidth, right = bumper + side * halfWidth, distanceM = travelled)
            if (travelled >= distanceM) break
            // 자전거 모델 한 걸음 (시뮬레이터와 같은 식)
            val ds = sign * stepM
            p = CarPose(
                x = p.x + ds * cos(p.heading),
                y = p.y + ds * sin(p.heading),
                heading = p.heading + ds * curvature,
            )
            travelled += stepM
        }
        return points
    }

    /** 센서 위치 */
    enum class SensorSpot { FRONT_LEFT, FRONT_CENTER, FRONT_RIGHT, REAR_LEFT, REAR_CENTER, REAR_RIGHT }

    /** 경고 단계: 실제 차 주차 센서처럼 가까울수록 단계가 올라감 */
    enum class Level(val maxDistanceM: Double) { NEAR(0.4), MID(0.8), FAR(1.2) }

    data class SensorReading(
        val spot: SensorSpot,
        val position: Vec2,      // 범퍼 위 센서 위치
        val facing: Double,      // 센서가 바라보는 방향 (라디안)
        val distanceM: Double,   // 가장 가까운 장애물까지 거리 (없으면 Double.MAX_VALUE)
    ) {
        val level: Level? get() = Level.entries.firstOrNull { distanceM <= it.maxDistanceM }
    }

    /** 범퍼 6곳의 센서가 감지하는 거리. 센서 앞쪽(바라보는 방향)에 있는 장애물만 감지함 */
    fun readSensors(body: OrientedRect, obstacles: List<OrientedRect>): List<SensorReading> {
        val forward = Vec2.unit(body.angle)
        val right = Vec2(-sin(body.angle), cos(body.angle))
        val front = body.center + forward * body.halfLength
        val rear = body.center - forward * body.halfLength
        val inset = body.halfWidth * 0.8   // 모서리 센서는 범퍼 끝에서 살짝 안쪽

        fun reading(spot: SensorSpot, pos: Vec2, facing: Double): SensorReading {
            val dir = Vec2.unit(facing)
            val nearest = obstacles
                .map { it.closestPoint(pos) }
                .filter { (it - pos) dot dir > 0.0 }      // 센서 뒤쪽 장애물은 무시
                .minOfOrNull { hypot(it.x - pos.x, it.y - pos.y) }
            return SensorReading(spot, pos, facing, nearest ?: Double.MAX_VALUE)
        }

        val h = body.angle
        val diag = Math.PI / 4
        return listOf(
            reading(SensorSpot.FRONT_LEFT, front - right * inset, h - diag),
            reading(SensorSpot.FRONT_CENTER, front, h),
            reading(SensorSpot.FRONT_RIGHT, front + right * inset, h + diag),
            reading(SensorSpot.REAR_LEFT, rear - right * inset, h + Math.PI + diag),
            reading(SensorSpot.REAR_CENTER, rear, h + Math.PI),
            reading(SensorSpot.REAR_RIGHT, rear + right * inset, h + Math.PI - diag),
        )
    }
}

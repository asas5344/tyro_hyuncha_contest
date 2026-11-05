package com.team.hyuncha.sim

import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.input.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class GuidanceTest {

    private val car = CarPresets.GRANDEUR
    private val pose = CarPose(10.0, 3.0, 0.0)

    @Test
    fun 핸들_가운데로_후진하면_진행선이_뒤로_곧게_뻗는다() {
        val path = Guidance.predictPath(car, pose, wheelAngleDeg = 0.0, direction = Direction.REVERSE, distanceM = 3.0)
        val first = path.first()
        val last = path.last()
        // 시작점은 뒤 범퍼, 차 폭만큼 벌어져 있음
        assertEquals(10.0 - car.rearOverhangM, first.left.x, 1e-9)
        assertEquals(car.widthM, hypot(first.left.x - first.right.x, first.left.y - first.right.y), 1e-9)
        // 3m 뒤로 곧게
        assertEquals(first.left.x - 3.0, last.left.x, 1e-6)
        assertEquals(first.left.y, last.left.y, 1e-9)
        assertEquals(3.0, last.distanceM, 1e-6)
    }

    @Test
    fun 전진_진행선은_앞_범퍼에서_시작한다() {
        val path = Guidance.predictPath(car, pose, wheelAngleDeg = 0.0, direction = Direction.FORWARD)
        assertEquals(10.0 + car.lengthM - car.rearOverhangM, path.first().left.x, 1e-9)
        assertTrue(path.last().left.x > path.first().left.x)
    }

    @Test
    fun 진행선은_시뮬레이터와_같은_방향으로_휜다() {
        // 후진하며 핸들 오른쪽 → 시뮬레이터에서 heading이 줄어듦 → 차 뒤가 아래(y 증가)로 감
        val path = Guidance.predictPath(car, pose, wheelAngleDeg = 25.0, direction = Direction.REVERSE)
        assertTrue(path.last().left.y > path.first().left.y)
    }

    @Test
    fun 가까운_장애물은_센서가_빨간_단계로_감지한다() {
        val body = OrientedRect(Vec2(10.0, 3.0), car.lengthM / 2, car.widthM / 2, 0.0)
        val behind = OrientedRect(Vec2(10.0 - car.lengthM / 2 - 0.3 - 0.5, 3.0), 0.5, 1.0, 0.0)  // 뒤 범퍼에서 30cm
        val readings = Guidance.readSensors(body, listOf(behind))
        val rearCenter = readings.first { it.spot == Guidance.SensorSpot.REAR_CENTER }
        assertEquals(0.3, rearCenter.distanceM, 1e-9)
        assertEquals(Guidance.Level.NEAR, rearCenter.level)
        // 앞쪽 센서는 뒤에 있는 장애물을 감지하지 않음
        assertNull(readings.first { it.spot == Guidance.SensorSpot.FRONT_CENTER }.level)
    }

    @Test
    fun 장애물이_멀면_경고가_없다() {
        val body = OrientedRect(Vec2(10.0, 3.0), car.lengthM / 2, car.widthM / 2, 0.0)
        val far = OrientedRect(Vec2(20.0, 3.0), 0.5, 1.0, 0.0)
        assertTrue(Guidance.readSensors(body, listOf(far)).all { it.level == null })
    }
}

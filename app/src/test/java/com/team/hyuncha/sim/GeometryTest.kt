package com.team.hyuncha.sim

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class GeometryTest {

    private val box = OrientedRect(Vec2(0.0, 0.0), halfLength = 2.0, halfWidth = 1.0, angle = 0.0)

    @Test
    fun 떨어진_사각형은_겹치지_않는다() {
        assertFalse(box.intersects(box.copy(center = Vec2(4.5, 0.0))))
    }

    @Test
    fun 겹친_사각형은_겹친다() {
        assertTrue(box.intersects(box.copy(center = Vec2(3.5, 0.0))))
    }

    @Test
    fun 회전하면_닿는_경우를_잡아낸다() {
        // 축 정렬 상태로는 x 3.2에서 안 닿지만, 45도 돌리면 모서리가 닿음
        val other = OrientedRect(Vec2(3.2, 0.0), halfLength = 1.0, halfWidth = 1.0, angle = 0.0)
        assertFalse(box.intersects(other.copy(center = Vec2(3.5, 0.0))))
        assertTrue(box.intersects(other.copy(angle = PI / 4)))
    }

    @Test
    fun 안에_완전히_들어있는지_판정한다() {
        assertTrue(box.containsRect(OrientedRect(Vec2(0.5, 0.0), 1.0, 0.5, 0.0)))
        assertFalse(box.containsRect(OrientedRect(Vec2(1.5, 0.0), 1.0, 0.5, 0.0)))
    }
}

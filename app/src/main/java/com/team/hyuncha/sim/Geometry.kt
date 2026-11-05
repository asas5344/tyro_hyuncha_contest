package com.team.hyuncha.sim

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 2D 좌표 (단위 m). 화면과 맞추려고 x는 오른쪽, y는 아래쪽이 양수 */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(k: Double) = Vec2(x * k, y * k)
    infix fun dot(o: Vec2) = x * o.x + y * o.y

    companion object {
        /** angle(라디안) 방향의 단위 벡터 */
        fun unit(angle: Double) = Vec2(cos(angle), sin(angle))
    }
}

/**
 * 회전된 직사각형 (차, 벽, 주차 칸 등).
 * halfLength는 angle 방향, halfWidth는 그 수직 방향의 절반 길이.
 */
data class OrientedRect(
    val center: Vec2,
    val halfLength: Double,
    val halfWidth: Double,
    val angle: Double,
) {
    private val axisL get() = Vec2.unit(angle)                       // 길이 방향
    private val axisW get() = Vec2(-sin(angle), cos(angle))          // 폭 방향

    /** 꼭짓점 4개 (앞-왼, 앞-오, 뒤-오, 뒤-왼 순서로 둘레를 따라감) */
    fun corners(): List<Vec2> {
        val l = axisL * halfLength
        val w = axisW * halfWidth
        return listOf(center + l - w, center + l + w, center - l + w, center - l - w)
    }

    fun contains(p: Vec2, tolerance: Double = 1e-9): Boolean {
        val d = p - center
        return abs(d dot axisL) <= halfLength + tolerance &&
            abs(d dot axisW) <= halfWidth + tolerance
    }

    /** 사각형 위(테두리 포함)에서 p에 가장 가까운 점. p가 안에 있으면 p 자신 */
    fun closestPoint(p: Vec2): Vec2 {
        val d = p - center
        val l = (d dot axisL).coerceIn(-halfLength, halfLength)
        val w = (d dot axisW).coerceIn(-halfWidth, halfWidth)
        return center + axisL * l + axisW * w
    }

    /** other가 이 사각형 안에 완전히 들어와 있는지 */
    fun containsRect(other: OrientedRect) = other.corners().all { contains(it) }

    /**
     * 분리축 정리(SAT)로 겹침 판정.
     * 두 사각형의 변 방향 4개 축 중 하나라도 투영이 안 겹치면 서로 떨어져 있음.
     */
    fun intersects(other: OrientedRect): Boolean {
        val mine = corners()
        val theirs = other.corners()
        for (axis in listOf(axisL, axisW, other.axisL, other.axisW)) {
            val a = mine.map { it dot axis }
            val b = theirs.map { it dot axis }
            if (a.max() < b.min() || b.max() < a.min()) return false
        }
        return true
    }
}

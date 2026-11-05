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

    /** p를 이 사각형 기준 좌표(길이 방향, 폭 방향)로 바꿈 */
    fun toLocal(p: Vec2): Pair<Double, Double> {
        val d = p - center
        return (d dot axisL) to (d dot axisW)
    }

    /** p가 안쪽으로 얼마나 깊이 들어와 있는지(m). 바깥이면 음수(떨어진 거리) */
    fun depthOf(p: Vec2): Double {
        val (l, w) = toLocal(p)
        val inL = halfLength - abs(l)
        val inW = halfWidth - abs(w)
        return if (inL >= 0 && inW >= 0) minOf(inL, inW)
        else -kotlin.math.hypot(maxOf(-inL, 0.0), maxOf(-inW, 0.0))
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
        // 빠른 사전 검사: 두 사각형을 감싸는 원끼리도 안 닿으면 계산할 필요 없음 (멀리 있는 장애물 대부분이 여기서 걸러짐)
        val reach = kotlin.math.hypot(halfLength, halfWidth) + kotlin.math.hypot(other.halfLength, other.halfWidth)
        val dx = center.x - other.center.x
        val dy = center.y - other.center.y
        if (dx * dx + dy * dy > reach * reach) return false
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

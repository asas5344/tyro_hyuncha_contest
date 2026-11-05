package com.team.hyuncha.sim

import com.team.hyuncha.input.Direction

/** 다시 보여주기용 한 순간의 기록 */
data class SimFrame(
    val timeSec: Double,
    val pose: CarPose,
    val wheelAngleDeg: Double,
    val braking: Boolean,
    val direction: Direction,
)

/** 차의 어느 부분이 닿았는지. 조사(이/가)까지 붙여서 문장을 바로 만들 수 있게 함 */
enum class CarPart(val subject: String) {
    FRONT_LEFT("앞 왼쪽 모서리가"),
    FRONT_RIGHT("앞 오른쪽 모서리가"),
    REAR_RIGHT("뒤 오른쪽 모서리가"),
    REAR_LEFT("뒤 왼쪽 모서리가"),
    FRONT("앞 범퍼가"),
    RIGHT_SIDE("오른쪽 옆면이"),
    REAR("뒤 범퍼가"),
    LEFT_SIDE("왼쪽 옆면이"),
}

/** 무엇에 닿았는지 */
enum class ObstacleKind(val label: String) { WALL("벽"), PARKED_CAR("주차된 차") }

/** 연습 중 생긴 일 (재생할 때 지도 위에 표시) */
sealed interface SimEvent {
    val timeSec: Double
    val position: Vec2

    /** 전진 ↔ 후진을 바꾼 곳 */
    data class DirectionChanged(override val timeSec: Double, override val position: Vec2, val to: Direction, val count: Int) : SimEvent

    /** 부딪힌 곳 */
    data class Collided(override val timeSec: Double, override val position: Vec2, val part: CarPart, val obstacle: ObstacleKind) : SimEvent {
        val message: String get() = "${part.subject} ${obstacle.label}에 닿았어요"
    }
}

object Contact {
    private const val TOUCH = 0.03   // 이 정도 차이는 "같이 닿았다"로 봄 (m)

    /**
     * 차체와 장애물이 겹쳤을 때 차의 어느 부분이 닿았는지 고름.
     * 1) 차 모서리가 장애물에 들어갔으면: 가장 깊이 들어간 모서리. 같은 면의 두 모서리가 함께 닿았으면 그 면(범퍼·옆면)
     * 2) 아니면 장애물 모서리가 차 옆구리를 찌른 것: 그 지점에서 가장 가까운 차의 면
     */
    fun find(body: OrientedRect, obstacle: OrientedRect): Pair<CarPart, Vec2> {
        val c = body.corners()   // 앞-왼, 앞-오, 뒤-오, 뒤-왼 순서
        val corners = listOf(CarPart.FRONT_LEFT, CarPart.FRONT_RIGHT, CarPart.REAR_RIGHT, CarPart.REAR_LEFT)
        // 이웃한 두 모서리 → 그 사이 면
        val edges = listOf(CarPart.FRONT, CarPart.RIGHT_SIDE, CarPart.REAR, CarPart.LEFT_SIDE)

        val depths = c.map { obstacle.depthOf(it) }
        val deepest = depths.max()
        if (deepest > -TOUCH) {
            val touching = depths.indices.filter { depths[it] >= deepest - TOUCH }
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                if (i in touching && j in touching) return edges[i] to mid(c[i], c[j])
            }
            val i = depths.indexOf(deepest)
            return corners[i] to c[i]
        }

        // 장애물 모서리 중 차 안으로 가장 깊이 들어온 점
        val poke = obstacle.corners().maxBy { body.depthOf(it) }
        val (l, w) = body.toLocal(poke)
        val part = if (body.halfLength - kotlin.math.abs(l) < body.halfWidth - kotlin.math.abs(w)) {
            if (l > 0) CarPart.FRONT else CarPart.REAR
        } else {
            if (w > 0) CarPart.RIGHT_SIDE else CarPart.LEFT_SIDE
        }
        return part to poke
    }

    private fun mid(a: Vec2, b: Vec2) = Vec2((a.x + b.x) / 2, (a.y + b.y) / 2)
}

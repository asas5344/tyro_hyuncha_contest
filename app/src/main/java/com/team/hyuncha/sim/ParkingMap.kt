package com.team.hyuncha.sim

import kotlin.math.PI
import kotlin.random.Random

/** 차의 위치와 방향. (x, y)는 뒷바퀴 축 중심, heading은 차가 바라보는 방향(라디안) */
data class CarPose(val x: Double, val y: Double, val heading: Double)

enum class ParkingMode(val label: String) {
    PERPENDICULAR("직각주차"),
    PARALLEL("평행주차"),
}

/** 주차장 한 판의 구성. 단위 m */
data class ParkingMap(
    val mode: ParkingMode,
    val widthM: Double,
    val heightM: Double,
    val walls: List<OrientedRect>,
    val parkedCars: List<OrientedRect>,
    val slot: OrientedRect,                  // 목표 주차 칸
    val lines: List<Pair<Vec2, Vec2>>,       // 바닥 주차선 (그리기용)
    val start: CarPose,
    val bays: List<OrientedRect> = emptyList(),  // 모든 주차 칸 (칸 번호·멈춤턱 그리기용)
) {
    /** 부딪히면 실패인 것들 */
    val obstacles: List<OrientedRect> = walls + parkedCars
}

object ParkingMaps {
    // 주차 칸 크기: 확장형 2.6 x 5.2m (그랜저도 들어가도록)
    private const val SLOT_W = 2.6
    private const val SLOT_D = 5.2
    private const val AISLE_W = 6.0   // 직각주차 통로 폭

    const val SLOT_COUNT = 8

    /**
     * 목표 칸이 될 수 있는 칸 (0부터). 오른쪽 끝 두 칸(7·8번)은 오른쪽 벽까지 여유가 없어
     * 기본 후진 주차(전진 → 꺾으며 후진)로는 못 넣으므로 목표에서 뺌 (단위 테스트로 확인)
     */
    val TARGET_RANGE = 0..5

    /**
     * 직각주차: 위쪽이 통로, 아래쪽에 주차 칸 8개.
     * 차는 통로 왼쪽에서 오른쪽을 보고 출발하고, 목표 칸(targetIndex)에 넣으면 성공.
     * @param parkedIndices 다른 차가 세워진 칸 번호 (0부터). 목표 칸은 들어 있어도 무시함
     */
    fun perpendicular(
        targetIndex: Int = 4,
        parkedIndices: List<Int> = listOf(0, 1, 3, 5, 6),
    ): ParkingMap {
        val slotCount = SLOT_COUNT
        val slotsLeft = 3.6
        val slotTop = AISLE_W
        val slotBottom = slotTop + SLOT_D
        val width = slotsLeft * 2 + SLOT_W * slotCount
        val height = slotBottom

        fun slotRect(i: Int) = OrientedRect(
            center = Vec2(slotsLeft + SLOT_W * (i + 0.5), slotTop + SLOT_D / 2),
            halfLength = SLOT_D / 2, halfWidth = SLOT_W / 2, angle = PI / 2,
        )

        // 바깥 벽: 주차장 테두리 바로 바깥에 두께 1m
        val walls = listOf(
            OrientedRect(Vec2(width / 2, -0.5), width / 2 + 1, 0.5, 0.0),           // 위
            OrientedRect(Vec2(width / 2, height + 0.5), width / 2 + 1, 0.5, 0.0),   // 아래
            OrientedRect(Vec2(-0.5, height / 2), 0.5, height / 2 + 1, 0.0),         // 왼쪽
            OrientedRect(Vec2(width + 0.5, height / 2), 0.5, height / 2 + 1, 0.0),  // 오른쪽
        )

        // 이웃 칸에 세워진 차 (칸 가운데, 일반 승용차 크기)
        val parkedCars = parkedIndices.filter { it != targetIndex && it in 0 until slotCount }.distinct().map { i ->
            slotRect(i).copy(halfLength = 2.4, halfWidth = 0.93)
        }

        // 칸 사이 세로선 + 칸 입구 쪽 짧은 표시는 생략하고 세로선만 그림
        val lines = (0..slotCount).map { i ->
            val x = slotsLeft + SLOT_W * i
            Vec2(x, slotTop) to Vec2(x, slotBottom)
        }

        return ParkingMap(
            mode = ParkingMode.PERPENDICULAR,
            widthM = width,
            heightM = height,
            walls = walls,
            parkedCars = parkedCars,
            slot = slotRect(targetIndex),
            lines = lines,
            start = CarPose(x = 5.5, y = AISLE_W / 2, heading = 0.0),
            bays = (0 until slotCount).map(::slotRect),
        )
    }

    /**
     * 연습할 때마다 다른 주차장: 목표 칸은 TARGET_RANGE 안에서 아무 칸이나, 나머지 칸은 대부분(약 75%) 차가 세워져 있음.
     * 목표 칸 바로 옆 두 칸은 항상 채워서 너무 쉬워지지 않게 함.
     */
    fun randomPerpendicular(random: Random = Random.Default): ParkingMap {
        val target = TARGET_RANGE.random(random)
        val parked = (0 until SLOT_COUNT).filter { i ->
            i != target && (kotlin.math.abs(i - target) == 1 || random.nextDouble() < 0.75)
        }
        return perpendicular(targetIndex = target, parkedIndices = parked)
    }

    // 평행주차 칸: 길이 6.5m (법정 일반형 6.0m보다 조금 넉넉하게, 그랜저 5.035m 기준), 폭 2.3m
    private const val PAR_SLOT_L = 6.5
    private const val PAR_SLOT_W = 2.3
    const val PARALLEL_SLOT_COUNT = 5

    /**
     * 목표가 될 수 있는 평행주차 칸. 앞(오른쪽)에 차가 있어야 그 차 옆에 맞춰 세우고 후진을 시작할 수 있고,
     * 뒤(왼쪽)에도 차가 있어야 평행주차 연습이 되므로 양 끝 칸은 뺌
     */
    val PARALLEL_TARGET_RANGE = 1..PARALLEL_SLOT_COUNT - 2

    /**
     * 평행주차: 위쪽이 차로, 아래쪽 연석을 따라 평행주차 칸 5개.
     * 차는 차로에서 오른쪽을 보고 출발해, 목표 칸 앞차 옆에 나란히 섰다가 후진으로 들어가면 성공.
     */
    fun parallel(
        targetIndex: Int = 2,
        parkedIndices: List<Int> = (0 until PARALLEL_SLOT_COUNT).toList(),
    ): ParkingMap {
        val left = 3.0
        val laneW = AISLE_W                       // 차로 폭 (직각주차 통로와 같음)
        val slotTop = laneW
        val slotBottom = slotTop + PAR_SLOT_W     // 연석
        val width = left + PAR_SLOT_L * PARALLEL_SLOT_COUNT + 6.0   // 오른쪽은 앞차를 지나 세울 공간
        val height = slotBottom

        fun slotRect(i: Int) = OrientedRect(
            center = Vec2(left + PAR_SLOT_L * (i + 0.5), slotTop + PAR_SLOT_W / 2),
            halfLength = PAR_SLOT_L / 2, halfWidth = PAR_SLOT_W / 2, angle = 0.0,
        )

        val walls = listOf(
            OrientedRect(Vec2(width / 2, -0.5), width / 2 + 1, 0.5, 0.0),           // 위
            OrientedRect(Vec2(width / 2, height + 0.5), width / 2 + 1, 0.5, 0.0),   // 아래 (연석·인도)
            OrientedRect(Vec2(-0.5, height / 2), 0.5, height / 2 + 1, 0.0),         // 왼쪽
            OrientedRect(Vec2(width + 0.5, height / 2), 0.5, height / 2 + 1, 0.0),  // 오른쪽
        )

        // 세워진 차는 칸 가운데, 연석 쪽으로 조금 붙여서
        val parkedCars = parkedIndices.filter { it != targetIndex && it in 0 until PARALLEL_SLOT_COUNT }.distinct().map { i ->
            val r = slotRect(i)
            r.copy(center = r.center + Vec2(0.0, 0.1), halfLength = 2.4, halfWidth = 0.93)
        }

        // 차로와 주차 칸 경계선 + 칸 사이 짧은 구분선
        val lines = buildList {
            add(Vec2(left, slotTop) to Vec2(left + PAR_SLOT_L * PARALLEL_SLOT_COUNT, slotTop))
            for (i in 0..PARALLEL_SLOT_COUNT) {
                val x = left + PAR_SLOT_L * i
                add(Vec2(x, slotTop) to Vec2(x, slotTop + 0.6))
            }
        }

        return ParkingMap(
            mode = ParkingMode.PARALLEL,
            widthM = width,
            heightM = height,
            walls = walls,
            parkedCars = parkedCars,
            slot = slotRect(targetIndex),
            lines = lines,
            start = CarPose(x = 2.5, y = laneW / 2 + 0.5, heading = 0.0),
            bays = (0 until PARALLEL_SLOT_COUNT).map(::slotRect),
        )
    }

    /** 연습할 때마다 다른 평행주차: 목표 칸 랜덤, 앞뒤 칸은 항상 차 있음, 나머지는 약 75% */
    fun randomParallel(random: Random = Random.Default): ParkingMap {
        val target = PARALLEL_TARGET_RANGE.random(random)
        val parked = (0 until PARALLEL_SLOT_COUNT).filter { i ->
            i != target && (kotlin.math.abs(i - target) == 1 || random.nextDouble() < 0.75)
        }
        return parallel(targetIndex = target, parkedIndices = parked)
    }

    /** 주차 방식에 맞는 랜덤 주차장 */
    fun random(mode: ParkingMode, random: Random = Random.Default): ParkingMap = when (mode) {
        ParkingMode.PERPENDICULAR -> randomPerpendicular(random)
        ParkingMode.PARALLEL -> randomParallel(random)
    }
}

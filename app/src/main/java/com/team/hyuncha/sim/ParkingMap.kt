package com.team.hyuncha.sim

import kotlin.math.PI

/** 차의 위치와 방향. (x, y)는 뒷바퀴 축 중심, heading은 차가 바라보는 방향(라디안) */
data class CarPose(val x: Double, val y: Double, val heading: Double)

enum class ParkingMode(val label: String) {
    PERPENDICULAR("직각주차"),
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

    /**
     * 직각주차: 위쪽이 통로, 아래쪽에 주차 칸 8개.
     * 차는 통로 왼쪽에서 오른쪽을 보고 출발하고, 가운데 빈 칸에 넣으면 성공.
     */
    fun perpendicular(): ParkingMap {
        val slotCount = 8
        val targetIndex = 4
        val parkedIndices = listOf(0, 1, 3, 5, 6)
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
        val parkedCars = parkedIndices.map { i ->
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
}

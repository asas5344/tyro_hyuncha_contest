package com.team.hyuncha.sim

import kotlin.math.PI
import kotlin.random.Random

/** 차의 위치와 방향. (x, y)는 뒷바퀴 축 중심, heading은 차가 바라보는 방향(라디안) */
data class CarPose(val x: Double, val y: Double, val heading: Double)

/** 주차 방식. label은 화면에 보이는 이름 (홈 카드·주차 화면 제목에 같이 씀) */
enum class ParkingMode(val label: String) {
    PERPENDICULAR("T자 주차"),   // 코드에서는 직각주차(perpendicular), 화면에서는 운전면허 시험 용어인 T자 주차
    PARALLEL("평행 주차"),
    DIAGONAL("사선 주차"),
}

/** 하드 모드에서 판마다 랜덤으로 하나 나오는 상황 */
enum class HardSituation(val label: String, val description: String) {
    PILLAR("기둥 옆 자리", "목표 칸 입구 바로 옆에 기둥이 있어요"),
    ENCROACH("옆 차 침범", "옆 차가 내 칸을 조금 침범해서 비뚤게 서 있어요"),
    DOUBLE_PARKED("이중주차", "통로에 이중주차된 차가 있어 길이 좁아요"),
}

/**
 * 연습 규칙. 기본은 일반 모드, 하드 모드는 ParkingMaps.randomHard()가 채움.
 * 주차장(ParkingMap)에 같이 담아서 시뮬레이터·모범 주행·점수가 같은 규칙으로 동작함.
 * @param creepSpeedMps 브레이크를 떼면 나가는 속도
 * @param timeLimitSec 제한시간 (처음 움직인 순간부터). null이면 없음
 * @param hornTimesSec 클락션이 울리는 시각 (처음 움직인 순간부터)
 */
data class PracticeRules(
    val creepSpeedMps: Double = ParkingSimulator.CREEP_SPEED,
    val timeLimitSec: Double? = null,
    val hornTimesSec: List<Double> = emptyList(),
    val situation: HardSituation? = null,
) {
    val isHard: Boolean get() = timeLimitSec != null
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
    val pillars: List<OrientedRect> = emptyList(),  // 기둥 (하드 모드 "기둥 옆 자리")
    val rules: PracticeRules = PracticeRules(),
) {
    /** 부딪히면 실패인 것들 */
    val obstacles: List<OrientedRect> = walls + parkedCars + pillars
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
        slotWidth: Double = SLOT_W,
        aisleWidth: Double = AISLE_W,
    ): ParkingMap {
        val SLOT_W = slotWidth          // 하드 모드는 칸이 더 좁음
        val slotCount = SLOT_COUNT
        val slotsLeft = 3.6
        val slotTop = aisleWidth
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
            start = CarPose(x = 5.5, y = aisleWidth / 2, heading = 0.0),
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

    // ───────── 하드 모드 (T자 주차만) ─────────
    // 항상: 제한시간 40초, 속도 8km/h, 클락션 3번(4초 고정 + 랜덤 2번), 좁은 통로와 주차 칸
    // 판마다 랜덤 하나: 기둥 옆 자리 / 옆 차 침범 / 이중주차

    const val HARD_SPEED_MPS = 8.0 / 3.6
    const val HARD_TIME_LIMIT_SEC = 40.0
    // 일반 2.6m(확장형) → 2.5m(법정 일반형). 2026-11-09 측정: 2.3·2.4m는 기둥이 있으면 그랜저(5.04m)를
    // 표준 방법으로 넣을 수 없어서 2.5m. 옆 차 침범과 겹치면 실제로 쓸 수 있는 폭은 약 2.3m
    const val HARD_SLOT_W = 2.5
    private const val HARD_AISLE_W = 5.7
    const val HORN_FIXED_SEC = 4.0
    const val HORN_COUNT = 3
    private const val HORN_RANDOM_FROM = 8.0
    private const val HORN_RANDOM_TO = 37.0
    private const val HORN_MIN_GAP = 6.0

    // 상황별 크기
    const val PILLAR_HALF = 0.3            // 기둥 0.6m x 0.6m
    const val PILLAR_GAP_M = 0.4           // 기둥과 주차 칸 입구 사이
    // 옆 차가 내 칸 쪽으로 밀려 선 거리. 칸 2.5m에 차 폭 1.86m라 선까지 여유가 0.32m → 0.35m 밀고 2° 비뚤면
    // 통로 쪽 모서리가 내 칸을 약 11cm 침범
    const val ENCROACH_SHIFT_M = 0.35
    const val ENCROACH_DEG = 2.0           // 옆 차가 비뚤어진 각도
    const val DOUBLE_PARK_GAP_M = 0.2      // 이중주차 차와 주차 칸 입구 사이
    const val DOUBLE_PARK_START_Y = 2.4    // 이중주차 때는 통로가 좁아서 조금 위쪽에서 출발

    /** 하드 모드에서 목표가 될 수 있는 칸 (HardModeTest로 모든 차종·상황에서 주차 가능한지 확인) */
    val HARD_TARGET_RANGE = 0..5

    // 랜덤으로 고를 수 있는 위치 (HardModeTest로 확인한 것만).
    // 차가 오른쪽에서 후진해 들어오면서 목표 칸 오른쪽 입구 모서리를 지나가서, 그쪽에 기둥이나 침범한 차가 있으면
    // 표준 방법으로 못 넣음 (기둥은 모든 차종, 침범은 그랜저·싼타페) → 둘 다 왼쪽만
    val PILLAR_SIDES = listOf(-1)
    val ENCROACH_SIDES = listOf(-1)
    // 이중주차 차 위치 (목표 칸에서 몇 칸): 오른쪽 1.5~2칸은 후진을 시작할 자리를 막아서 뺌
    val DOUBLE_PARK_OFFSETS = listOf(-2.5, -2.0, -1.5, 2.5, 3.0)

    /**
     * 하드 모드 주차장 (T자). 상황은 랜덤, 또는 확인용으로 고른 것.
     * @param situation null이면 랜덤
     */
    fun randomHard(random: Random = Random.Default, situation: HardSituation? = null): ParkingMap {
        val pick = situation ?: HardSituation.entries.random(random)
        val side = when (pick) {
            HardSituation.PILLAR -> PILLAR_SIDES.random(random)
            HardSituation.ENCROACH -> ENCROACH_SIDES.random(random)
            HardSituation.DOUBLE_PARKED -> 1
        }
        return hard(HARD_TARGET_RANGE.random(random), pick, side, hornTimes(random), DOUBLE_PARK_OFFSETS.random(random))
    }

    /** 클락션 시각: 4초 고정 + 8~37초 사이 랜덤 2번 (서로 6초 이상 떨어지게) */
    fun hornTimes(random: Random): List<Double> {
        while (true) {
            val extra = List(HORN_COUNT - 1) { HORN_RANDOM_FROM + random.nextDouble() * (HORN_RANDOM_TO - HORN_RANDOM_FROM) }.sorted()
            val all = listOf(HORN_FIXED_SEC) + extra
            if (all.zipWithNext().all { (a, b) -> b - a >= HORN_MIN_GAP }) return all
        }
    }

    /**
     * 하드 모드 주차장.
     * @param side 상황이 목표 칸의 어느 쪽에 있는지 (-1 왼쪽, 1 오른쪽)
     * @param doubleParkOffset 이중주차 차가 목표 칸 가운데에서 몇 칸 떨어져 있는지 (칸 수, 음수 = 왼쪽)
     */
    fun hard(
        targetIndex: Int,
        situation: HardSituation,
        side: Int,
        hornTimes: List<Double> = listOf(HORN_FIXED_SEC, 15.0, 27.0),
        doubleParkOffset: Double = -2.0,
    ): ParkingMap {
        val parked = (0 until SLOT_COUNT).filter { it != targetIndex }
        // 통로에 돌출된 기둥이 있으면 그랜저가 돌 수 있도록 기존 폭을 확보한다.
        val aisleWidth = if (situation == HardSituation.PILLAR) AISLE_W else HARD_AISLE_W
        val base = perpendicular(targetIndex, parked, slotWidth = HARD_SLOT_W, aisleWidth = aisleWidth)
        val slot = base.slot
        val slotTop = slot.center.y - slot.halfLength
        val rules = PracticeRules(HARD_SPEED_MPS, HARD_TIME_LIMIT_SEC, hornTimes, situation)
        return when (situation) {
            // 칸 입구 옆 통로 쪽에 기둥 (칸 선 위)
            HardSituation.PILLAR -> base.copy(
                pillars = listOf(OrientedRect(Vec2(slot.center.x + side * HARD_SLOT_W / 2, slotTop - PILLAR_HALF - PILLAR_GAP_M), PILLAR_HALF, PILLAR_HALF, 0.0)),
                rules = rules,
            )
            // 옆 칸 차를 내 칸 쪽으로 밀고, 통로 쪽 끝이 내 칸으로 기울게 돌림
            HardSituation.ENCROACH -> {
                val neighborX = slot.center.x + side * HARD_SLOT_W
                val cars = base.parkedCars.map { r ->
                    if (kotlin.math.abs(r.center.x - neighborX) > 0.01) r
                    else r.copy(center = Vec2(r.center.x - side * ENCROACH_SHIFT_M, r.center.y),
                        angle = PI / 2 - side * Math.toRadians(ENCROACH_DEG))
                }
                base.copy(parkedCars = cars, rules = rules)
            }
            // 통로에 가로로 선 차 (칸 입구 앞)
            HardSituation.DOUBLE_PARKED -> {
                val car = OrientedRect(Vec2(slot.center.x + doubleParkOffset * HARD_SLOT_W, slotTop - DOUBLE_PARK_GAP_M - 0.93), 2.4, 0.93, 0.0)
                base.copy(parkedCars = base.parkedCars + car, start = base.start.copy(y = DOUBLE_PARK_START_Y), rules = rules)
            }
        }
    }

    // 평행주차 칸: 길이 6.5m (법정 일반형 6.0m보다 조금 넉넉하게, 그랜저 5.035m 기준), 폭 2.3m
    private const val PAR_SLOT_L = 6.5
    private const val PAR_SLOT_W = 2.3
    const val PARALLEL_SLOT_COUNT = 5

    /** 평행주차 출발 위치(뒷바퀴 축 y). 차로 가운데보다 주차 칸 쪽에 붙어서 출발 */
    const val PARALLEL_START_Y = 4.6

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
            start = CarPose(x = 2.5, y = PARALLEL_START_Y, heading = 0.0),   // 주차된 차 옆으로 약 70cm 간격
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

    // 사선주차: 통로에 60° 기울어진 칸. 일방통행 통로에서 앞으로 비스듬히 들어감
    private const val DIAG_ANGLE_DEG = 60.0
    private const val DIAG_AISLE_W = 5.0
    const val DIAGONAL_SLOT_COUNT = 8

    /** 목표가 될 수 있는 사선 칸: 왼쪽 끝은 출발점에 너무 가까워 꺾을 거리가 없고, 오른쪽 끝은 옆 차가 한쪽뿐이라 뺌 */
    val DIAGONAL_TARGET_RANGE = 1..DIAGONAL_SLOT_COUNT - 2

    /**
     * 사선주차: 위쪽이 일방통행 통로(오른쪽으로 진행), 아래쪽에 60° 기울어진 칸 8개.
     * 칸은 칸 사이 선(평행사변형) 안에 들어가는 직사각형으로 만들고, 가장 높은 모서리가 통로 경계에 닿게 둠.
     */
    fun diagonal(
        targetIndex: Int = 4,
        parkedIndices: List<Int> = (0 until DIAGONAL_SLOT_COUNT).toList(),
    ): ParkingMap {
        val angle = Math.toRadians(DIAG_ANGLE_DEG)
        val u = Vec2.unit(angle)                       // 칸 안쪽 방향 (오른쪽 아래)
        val v = Vec2(-kotlin.math.sin(angle), kotlin.math.cos(angle))
        val hl = SLOT_D / 2
        val hw = SLOT_W / 2
        val spacing = SLOT_W / kotlin.math.sin(angle)  // 통로를 따라 잰 칸 간격 (약 3.0m)
        val left = 7.0
        val slotTop = DIAG_AISLE_W
        // 칸 사이 선 i 위의 점 p_i에서 칸 중심까지: 선을 따라 t, 옆으로 반 칸
        val t = hl + SLOT_W * kotlin.math.cos(angle) / kotlin.math.sin(angle)

        fun linePoint(i: Int) = Vec2(left + spacing * i, slotTop)
        fun slotRect(i: Int) = OrientedRect(center = linePoint(i) + u * t - v * hw, halfLength = hl, halfWidth = hw, angle = angle)

        val bays = (0 until DIAGONAL_SLOT_COUNT).map(::slotRect)
        val bottom = bays.flatMap { it.corners() }.maxOf { it.y }
        val width = bays.flatMap { it.corners() }.maxOf { it.x } + 3.0
        val height = bottom + 0.3

        val walls = listOf(
            OrientedRect(Vec2(width / 2, -0.5), width / 2 + 1, 0.5, 0.0),           // 위
            OrientedRect(Vec2(width / 2, height + 0.5), width / 2 + 1, 0.5, 0.0),   // 아래
            OrientedRect(Vec2(-0.5, height / 2), 0.5, height / 2 + 1, 0.0),         // 왼쪽
            OrientedRect(Vec2(width + 0.5, height / 2), 0.5, height / 2 + 1, 0.0),  // 오른쪽
        )

        // 세워진 차: 앞으로 들어간 차라 칸 안쪽(오른쪽 아래)을 봄
        val parkedCars = parkedIndices.filter { it != targetIndex && it in 0 until DIAGONAL_SLOT_COUNT }.distinct()
            .map { i -> slotRect(i).copy(halfLength = 2.4, halfWidth = 0.93) }

        // 칸 사이 선: 통로 경계에서 칸 끝까지 비스듬히
        val lines = (0..DIAGONAL_SLOT_COUNT).map { i -> linePoint(i) to linePoint(i) + u * (t + hl) }

        return ParkingMap(
            mode = ParkingMode.DIAGONAL,
            widthM = width,
            heightM = height,
            walls = walls,
            parkedCars = parkedCars,
            slot = slotRect(targetIndex),
            lines = lines,
            start = CarPose(x = 2.5, y = DIAG_AISLE_W / 2, heading = 0.0),
            bays = bays,
        )
    }

    /** 연습할 때마다 다른 사선주차: 목표 칸 랜덤, 양옆 칸은 항상 차 있음, 나머지는 약 75% */
    fun randomDiagonal(random: Random = Random.Default): ParkingMap {
        val target = DIAGONAL_TARGET_RANGE.random(random)
        val parked = (0 until DIAGONAL_SLOT_COUNT).filter { i ->
            i != target && (kotlin.math.abs(i - target) == 1 || random.nextDouble() < 0.75)
        }
        return diagonal(targetIndex = target, parkedIndices = parked)
    }

    /** 주차 방식에 맞는 랜덤 주차장 */
    fun random(mode: ParkingMode, random: Random = Random.Default): ParkingMap = when (mode) {
        ParkingMode.PERPENDICULAR -> randomPerpendicular(random)
        ParkingMode.PARALLEL -> randomParallel(random)
        ParkingMode.DIAGONAL -> randomDiagonal(random)
    }
}

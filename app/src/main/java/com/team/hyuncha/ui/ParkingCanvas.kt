package com.team.hyuncha.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.team.hyuncha.input.Direction
import com.team.hyuncha.sim.Guidance
import com.team.hyuncha.sim.OrientedRect
import com.team.hyuncha.sim.ParkingMap
import com.team.hyuncha.sim.ParkingMode
import com.team.hyuncha.sim.ParkingSimulator
import com.team.hyuncha.sim.SimEvent
import com.team.hyuncha.sim.SimStatus
import com.team.hyuncha.sim.Vec2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// 주차장
internal val Backdrop = Color(0xFF1E2024)
private val Asphalt = Color(0xFF34373C)
private val WallConcrete = Color(0xFF55595F)
private val CurbYellow = Color(0xFFE0B23A)
private val LineWhite = Color(0xFFE9E9E6)
private val WheelStop = Color(0xFFC9A646)
private val TargetGreen = Color(0xFF4CD08A)

// 차
private val MyCarBody = Color(0xFFEDEDEA)  // 이미지가 없을 때 코드로 그리는 내 차 색
private val CrashedRed = Color(0xFFE5484D)
private val GlassDark = Color(0xFF1B2530)
private val HeadLight = Color(0xFFFFF4D6)
private val TailDim = Color(0xFF7A1418)
private val BrakeRed = Color(0xFFFF2D2D)
private val ParkedColors = listOf(
    Color(0xFFE8E8E6), Color(0xFF2B2D31), Color(0xFF8E949C), Color(0xFF26395C), Color(0xFF8C2B2B),
)
// 은색 이미지에 곱해서 색을 입힘 (흰색 = 원래 은색 그대로). 유리·외곽선은 어두워서 거의 안 바뀜
// 주차된 차 색 후보 (내 차가 흰색이라 흰색·밝은 은색은 뺌)
internal val ParkedTints = listOf(
    Color(0xFF4A4E56), Color(0xFF2E3036), Color(0xFFD9C7A3), Color(0xFFC25555),
    Color(0xFF5D7FC4), Color(0xFF9BA1AA), Color(0xFF5E8A6E),
)
// 내 차: 은색 이미지를 살짝 밝게 해서 흰색으로
private val MyCarWhite = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(1.12f, 0f, 0f, 0f, 22f, 0f, 1.12f, 0f, 0f, 22f, 0f, 0f, 1.12f, 0f, 22f, 0f, 0f, 0f, 1f, 0f)),
)
private val CrashedTint = ColorFilter.tint(Color(0xFFFF7A7A), BlendMode.Modulate)

/** 주차된 차 한 대의 모습: 차종 이미지, 색, 앞뒤 방향 */
internal class ParkedStyle(val spriteIndex: Int, val tint: Color, val flipped: Boolean)

// 안내
private val GuideRed = Color(0xFFFF5A52)
private val GuideYellow = Color(0xFFFFD54F)
private val GuideGreen = Color(0xFF5BD68E)
private val TrailColor = Color(0xFFFFD54F)
private val MarkerBlue = Color(0xFF2F7BEA)
private val ModelGreen = Color(0xFF5BD68E)

/**
 * 다시 보기에서 그릴 것.
 * @param source 재생하는 주행 (내 주행이면 지금 시뮬레이터, 모범 주행이면 코치가 돌린 시뮬레이터)
 * @param compare 같이 옅게 보여줄 다른 주행 (비교용)
 */
class ReplayView(val source: ParkingSimulator, val timeSec: Double, val isModel: Boolean, val compare: ParkingSimulator?)

/** 한 화면에 보여줄 최대 가로 길이(m). 직각주차장(28m)은 거의 다 보이고, 평행주차 도로는 카메라가 따라감 */
private const val VIEW_WIDTH_M = 26.0

/** 월드 좌표(m) → 화면 좌표(px) 변환 */
internal class Projection(val scale: Float, val ox: Float, val oy: Float) {
    fun toScreen(p: Vec2) = Offset(ox + p.x.toFloat() * scale, oy + p.y.toFloat() * scale)
    fun m(meters: Double) = meters.toFloat() * scale
}

/** 아스팔트 질감용 점 (맵마다 한 번만 만들어 둠) */
internal class Speckle(val pos: Vec2, val radiusM: Float, val color: Color)

/**
 * 탑뷰 주차장 그리기.
 * @param frame 매 프레임 바뀌는 값. draw 안에서 읽어야 프레임마다 다시 그려짐
 * @param direction 지금 고른 진행 방향 (예상 진행선·후진등에 사용)
 * @param braking 브레이크를 밟고 있는지 (브레이크등에 사용)
 * @param replay 다시 보기 중이면 무엇을 몇 초째 재생하는지, 아니면 null (지금 상태를 그림)
 */
@Composable
fun ParkingCanvas(
    sim: ParkingSimulator,
    frame: () -> Long,
    direction: () -> Direction,
    braking: () -> Boolean,
    modifier: Modifier = Modifier,
    replay: () -> ReplayView? = { null },
) {
    val textMeasurer = rememberTextMeasurer()
    val assets = rememberTopAssets(sim)

    Canvas(modifier) {
        frame()  // 이 값을 읽어둬야 시뮬레이터가 움직일 때 다시 그려짐
        val scene = sceneOf(sim, replay(), direction(), braking())
        val map = sim.map

        // 축척: 가로는 최대 VIEW_WIDTH_M까지만 보여줘서 긴 주차장(평행주차)에서도 차가 작아지지 않게 함 (여백 1m)
        val margin = 1.0
        val viewWidthM = min(map.widthM, VIEW_WIDTH_M)
        val scale = min(
            size.width / (viewWidthM + margin * 2),
            size.height / (map.heightM + margin * 2),
        ).toFloat()
        // 화면보다 넓은 주차장이면 카메라가 차를 따라 좌우로 움직임 (끝에서는 멈춤)
        val visibleM = size.width / scale
        val cameraX = if (map.widthM + margin * 2 <= visibleM) {
            map.widthM / 2
        } else {
            scene.body.center.x.coerceIn(visibleM / 2 - margin, map.widthM + margin - visibleM / 2)
        }
        val proj = Projection(
            scale = scale,
            ox = size.width / 2 - cameraX.toFloat() * scale,
            oy = (size.height - map.heightM.toFloat() * scale) / 2,
        )
        drawRect(Backdrop)
        drawTopScene(scene, assets, proj, textMeasurer)
    }
}

/** 탑뷰·1인칭이 같이 쓰는 그림 재료 (판마다 한 번만 만듦) */
internal class TopAssets(
    val speckles: List<Speckle>,
    val sprites: Map<String, CarTopSprite>,
    val spriteList: List<CarTopSprite>,
    val parkedStyles: List<ParkedStyle>,
)

@Composable
internal fun rememberTopAssets(sim: ParkingSimulator): TopAssets {
    val speckles = remember(sim.map) { makeSpeckles(sim.map) }
    val sprites = rememberCarTopSprites()
    val spriteList = remember(sprites) { sprites.values.toList() }
    // 주차장 배치가 바뀔 때마다 주차된 차의 차종·색·방향을 새로 뽑음 (같은 배치에서는 그대로)
    val parkedStyles = remember(sim.map) {
        val rnd = Random(sim.map.parkedCars.hashCode())
        sim.map.parkedCars.map {
            ParkedStyle(
                spriteIndex = rnd.nextInt(4), tint = ParkedTints.random(rnd),
                // T자 주차만 앞뒤를 섞음. 평행(길가)·사선(앞으로 들어감)은 모두 같은 방향
                flipped = sim.map.mode == ParkingMode.PERPENDICULAR && rnd.nextBoolean(),
            )
        }
    }
    return remember(speckles, sprites, parkedStyles) { TopAssets(speckles, sprites, spriteList, parkedStyles) }
}

/**
 * 지금 그릴 내 차 상태. 평소에는 시뮬레이터의 지금 상태, 다시 보기 중에는 재생하는 주행의 그 시각 기록.
 * 탑뷰·1인칭·후방 카메라가 모두 이 값을 보고 그림.
 */
internal class Scene(
    val sim: ParkingSimulator,
    val replay: ReplayView?,
    val src: ParkingSimulator,
    val replayTime: Double?,
    val pose: com.team.hyuncha.sim.CarPose,
    val wheelAngleDeg: Double,
    val speedMps: Double,
    val direction: Direction,
    val braking: Boolean,
    val atEnd: Boolean,
    val crashed: Boolean,
    val body: OrientedRect,
) {
    /** 예상 진행선을 보여줄지 (주행 중이거나 재생 도중) */
    val showGuide: Boolean get() = sim.status == SimStatus.DRIVING || (replayTime != null && !atEnd)
}

internal fun sceneOf(sim: ParkingSimulator, rv: ReplayView?, direction: Direction, braking: Boolean): Scene {
    val src = rv?.source ?: sim
    val rt = rv?.timeSec
    val shown = rt?.let { src.frameAt(it) }
    val pose = shown?.pose ?: sim.pose
    val atEnd = rt == null || rt >= src.history.last().timeSec
    return Scene(
        sim = sim, replay = rv, src = src, replayTime = rt, pose = pose,
        wheelAngleDeg = shown?.wheelAngleDeg ?: sim.wheelAngleDeg,
        speedMps = shown?.speedMps ?: sim.speed,
        direction = shown?.direction ?: direction,
        braking = shown?.braking ?: braking,
        atEnd = atEnd,
        crashed = src.status == SimStatus.COLLIDED && atEnd,
        body = src.bodyRect(pose),
    )
}

/** 탑뷰 장면 전체 (바닥·칸·주차된 차·경로·진행선·내 차·센서·사건 표시). 배경색은 부르는 쪽에서 칠함 */
internal fun DrawScope.drawTopScene(scene: Scene, assets: TopAssets, proj: Projection, textMeasurer: TextMeasurer) {
    val sim = scene.sim
    val map = sim.map
    val rv = scene.replay
    val rt = scene.replayTime
    val pulse = 0.5f + 0.5f * sin(System.nanoTime() / 1e9 * 3.0).toFloat()  // 0~1, 목표 칸 깜빡임

    drawGround(map, proj, assets.speckles)
    drawBays(map, proj, textMeasurer, pulse)
    drawAisleArrows(map, proj)
    // 기둥 (하드 모드): 바닥 위에 콘크리트 + 노란 테두리
    map.pillars.forEach {
        drawOriented(it, proj, WallConcrete)
        drawOriented(it, proj, Color(0xFFFFC83D), Stroke(width = proj.m(0.06)))
    }

    map.parkedCars.forEachIndexed { i, rect ->
        // 주차된 차는 색과 방향을 섞어서 같은 차만 늘어선 느낌을 줄임
        val style = assets.parkedStyles[i]
        val heading = if (style.flipped) rect.angle + Math.PI else rect.angle
        drawCarTopDown(
            center = rect.center, heading = heading,
            lengthM = rect.halfLength * 2, widthM = rect.halfWidth * 2,
            bodyColor = ParkedColors[i % ParkedColors.size], proj = proj,
            sprite = assets.spriteList.getOrNull(style.spriteIndex % assets.spriteList.size.coerceAtLeast(1)),
            colorFilter = ColorFilter.tint(style.tint, BlendMode.Modulate),
        )
    }

    if (rv == null || rt == null) {
        // 지나온 경로 (옅게)
        drawPoseLine(sim.trail, proj, TrailColor.copy(alpha = 0.3f), 2f)
    } else {
        // 다시 보기: 비교할 다른 주행은 옅게, 재생 중인 주행은 전체를 옅게 + 지금까지 온 길을 진하게
        // 색: 내 주행 = 노랑, 모범 주행 = 초록
        val srcColor = if (rv.isModel) ModelGreen else TrailColor
        val otherColor = if (rv.isModel) TrailColor else ModelGreen
        rv.compare?.let { other -> drawPoseLine(other.history.map { it.pose }, proj, otherColor.copy(alpha = 0.35f), proj.m(0.06)) }
        drawPoseLine(scene.src.history.map { it.pose }, proj, srcColor.copy(alpha = 0.25f), proj.m(0.06))
        drawPoseLine(scene.src.history.takeWhile { it.timeSec <= rt }.map { it.pose }, proj, srcColor, proj.m(0.1))
    }

    if (scene.showGuide) drawGuideLines(sim, scene.pose, scene.wheelAngleDeg, scene.direction, proj)

    drawCarTopDown(
        center = scene.body.center, heading = scene.pose.heading,
        lengthM = sim.car.lengthM, widthM = sim.car.widthM,
        bodyColor = if (scene.crashed) CrashedRed else MyCarBody,
        proj = proj,
        sprite = assets.sprites.forCar(sim.car),
        colorFilter = if (scene.crashed) CrashedTint else MyCarWhite,
        lights = CarLights(brake = scene.braking, reverse = scene.direction == Direction.REVERSE),
        frontWheels = FrontWheels(
            axleOffsetM = sim.car.wheelBaseM - (sim.car.lengthM / 2 - sim.car.rearOverhangM),
            angleDeg = scene.wheelAngleDeg,
        ),
    )

    if (sim.status != SimStatus.PARKED || !scene.atEnd) {
        drawSensors(Guidance.readSensors(scene.body, map.obstacles), proj)
    }

    // 전환·충돌 표시는 차가 지나가도 가려지지 않게 맨 위에
    if (rt != null) drawEventMarkers(scene.src.events, rt, proj, textMeasurer)
}

private fun makeSpeckles(map: ParkingMap): List<Speckle> {
    val rnd = Random(7)
    val count = (map.widthM * map.heightM * 6).toInt()
    return List(count) {
        val light = rnd.nextBoolean()
        Speckle(
            pos = Vec2(rnd.nextDouble() * map.widthM, rnd.nextDouble() * map.heightM),
            radiusM = 0.02f + rnd.nextFloat() * 0.04f,
            color = (if (light) Color.White else Color.Black).copy(alpha = 0.05f + rnd.nextFloat() * 0.07f),
        )
    }
}

/** 바닥(아스팔트 질감) + 벽 + 노란 연석 */
private fun DrawScope.drawGround(map: ParkingMap, proj: Projection, speckles: List<Speckle>) {
    val topLeft = proj.toScreen(Vec2(0.0, 0.0))
    val groundSize = Size(proj.m(map.widthM), proj.m(map.heightM))
    map.walls.forEach { drawOriented(it, proj, WallConcrete) }
    drawRect(Asphalt, topLeft = topLeft, size = groundSize)
    speckles.forEach { drawCircle(it.color, radius = proj.m(it.radiusM.toDouble()), center = proj.toScreen(it.pos)) }
    // 벽과 바닥 경계의 노란 연석
    drawRect(CurbYellow.copy(alpha = 0.85f), topLeft = topLeft, size = groundSize, style = Stroke(width = proj.m(0.12)))
}

/** 주차선, 칸 번호, 바퀴 멈춤턱, 목표 칸 표시 */
private fun DrawScope.drawBays(map: ParkingMap, proj: Projection, textMeasurer: TextMeasurer, pulse: Float) {
    val target = map.slot

    // 목표 칸: 은은하게 깜빡이는 초록 + 바닥의 큰 P
    drawOriented(target, proj, TargetGreen.copy(alpha = 0.10f + 0.12f * pulse))
    drawOriented(target, proj, TargetGreen.copy(alpha = 0.55f + 0.45f * pulse), style = Stroke(width = proj.m(0.1)))
    drawLabel(textMeasurer, "P", target.center, proj, sizeM = 1.6, color = TargetGreen.copy(alpha = 0.35f))

    map.lines.forEach { (a, b) ->
        drawLine(LineWhite, proj.toScreen(a), proj.toScreen(b), strokeWidth = proj.m(0.12), cap = StrokeCap.Square)
    }

    map.bays.forEachIndexed { i, bay ->
        when (map.mode) {
            ParkingMode.PERPENDICULAR, ParkingMode.DIAGONAL -> {
                // 칸 길이 방향(halfLength)이 칸 안쪽. 입구 쪽에 번호, 안쪽 끝에 바퀴 멈춤턱 (직각 = A, 사선 = C)
                val entrance = bay.center - Vec2.unit(bay.angle) * (bay.halfLength - 0.55)
                val stopPos = bay.center + Vec2.unit(bay.angle) * (bay.halfLength - 0.55)
                val prefix = if (map.mode == ParkingMode.DIAGONAL) "C" else "A"
                drawLabel(textMeasurer, "$prefix${i + 1}", entrance, proj, sizeM = 0.42, color = LineWhite.copy(alpha = 0.55f))
                drawOriented(OrientedRect(stopPos, halfLength = 0.09, halfWidth = 0.75, angle = bay.angle), proj, WheelStop)
            }
            ParkingMode.PARALLEL -> {
                // 길가 칸: 멈춤턱 없음. 번호는 칸 왼쪽 위(차로 쪽) 구석에
                val corner = bay.center + Vec2(-(bay.halfLength - 0.6), -(bay.halfWidth - 0.35))
                drawLabel(textMeasurer, "B${i + 1}", corner, proj, sizeM = 0.42, color = LineWhite.copy(alpha = 0.55f))
            }
        }
    }
}

/** 차가 지나간 길 (뒷바퀴 축 위치를 이은 선) */
private fun DrawScope.drawPoseLine(poses: List<com.team.hyuncha.sim.CarPose>, proj: Projection, color: Color, width: Float) {
    if (poses.size < 2) return
    val path = Path()
    poses.forEachIndexed { i, p ->
        val o = proj.toScreen(Vec2(p.x, p.y))
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 다시 보여주기의 사건 표시: 파란 원 = 전진·후진 전환(번호), 빨간 원 = 부딪힌 곳. 아직 안 지난 사건은 흐리게 */
private fun DrawScope.drawEventMarkers(events: List<SimEvent>, timeSec: Double, proj: Projection, textMeasurer: TextMeasurer) {
    events.forEach { e ->
        val passed = e.timeSec <= timeSec
        val alpha = if (passed) 1f else 0.35f
        val c = proj.toScreen(e.position)
        when (e) {
            is SimEvent.DirectionChanged -> {
                drawCircle(MarkerBlue.copy(alpha = alpha), radius = proj.m(0.38), center = c)
                drawCircle(Color.White.copy(alpha = alpha), radius = proj.m(0.38), center = c, style = Stroke(width = proj.m(0.06)))
                drawLabel(textMeasurer, "${e.count}", e.position, proj, sizeM = 0.42, color = Color.White.copy(alpha = alpha))
            }
            is SimEvent.Collided -> {
                drawCircle(GuideRed.copy(alpha = 0.35f * alpha), radius = proj.m(0.75), center = c)
                drawCircle(GuideRed.copy(alpha = alpha), radius = proj.m(0.38), center = c)
                drawLabel(textMeasurer, "!", e.position, proj, sizeM = 0.5, color = Color.White.copy(alpha = alpha))
            }
        }
    }
}

/** 통로 바닥의 진행 방향 화살표 */
private fun DrawScope.drawAisleArrows(map: ParkingMap, proj: Projection) {
    val y = map.start.y
    var x = 4.0
    while (x < map.widthM - 3.0) {
        val path = Path().apply {
            val a = proj.toScreen(Vec2(x - 0.35, y - 0.45))
            val b = proj.toScreen(Vec2(x + 0.35, y))
            val c = proj.toScreen(Vec2(x - 0.35, y + 0.45))
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y)
        }
        drawPath(path, LineWhite.copy(alpha = 0.22f), style = Stroke(width = proj.m(0.14), cap = StrokeCap.Round, join = StrokeJoin.Round))
        x += 5.0
    }
}

/**
 * 예상 진행선: 지금 바퀴 각도 그대로 가면 범퍼 양 끝이 지나갈 길.
 * 실제 후방 카메라처럼 가까운 쪽부터 빨강(1m) → 노랑(2m) → 초록.
 */
private fun DrawScope.drawGuideLines(sim: ParkingSimulator, pose: com.team.hyuncha.sim.CarPose, wheelAngleDeg: Double, direction: Direction, proj: Projection) {
    val points = Guidance.predictPath(sim.car, pose, wheelAngleDeg, direction)
    if (points.size < 2) return

    // 차 폭만큼의 띠를 옅게 칠함
    val band = Path().apply {
        points.forEachIndexed { i, p -> val o = proj.toScreen(p.left); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        points.asReversed().forEach { p -> val o = proj.toScreen(p.right); lineTo(o.x, o.y) }
        close()
    }
    drawPath(band, GuideYellow.copy(alpha = 0.10f))

    fun colorAt(d: Double) = when {
        d < 1.0 -> GuideRed
        d < 2.0 -> GuideYellow
        else -> GuideGreen
    }
    val width = proj.m(0.08)
    points.zipWithNext().forEach { (a, b) ->
        val c = colorAt(a.distanceM)
        drawLine(c, proj.toScreen(a.left), proj.toScreen(b.left), strokeWidth = width, cap = StrokeCap.Round)
        drawLine(c, proj.toScreen(a.right), proj.toScreen(b.right), strokeWidth = width, cap = StrokeCap.Round)
    }
    // 1m, 2m, 3m 거리 표시선
    listOf(1.0, 2.0, 3.0).forEach { mark ->
        points.minByOrNull { kotlin.math.abs(it.distanceM - mark) }?.let { p ->
            drawLine(colorAt(mark - 0.01), proj.toScreen(p.left), proj.toScreen(p.right), strokeWidth = width * 0.7f)
        }
    }
}

/** 주차 센서: 가까울수록 호가 많아지고 빨갛게 (초록 1줄 → 노랑 2줄 → 빨강 3줄) */
private fun DrawScope.drawSensors(readings: List<Guidance.SensorReading>, proj: Projection) {
    readings.forEach { r ->
        val level = r.level ?: return@forEach
        val (color, arcs) = when (level) {
            Guidance.Level.NEAR -> GuideRed to 3
            Guidance.Level.MID -> GuideYellow to 2
            Guidance.Level.FAR -> GuideGreen to 1
        }
        val center = proj.toScreen(r.position)
        val startDeg = Math.toDegrees(r.facing).toFloat() - 30f
        for (k in 1..arcs) {
            val radius = proj.m(0.2 + 0.26 * k)
            drawArc(
                color = color.copy(alpha = 0.95f - 0.15f * (k - 1)),
                startAngle = startDeg, sweepAngle = 60f, useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2),
                style = Stroke(width = proj.m(0.12), cap = StrokeCap.Round),
            )
        }
    }
}

private class CarLights(val brake: Boolean, val reverse: Boolean)

/** @param axleOffsetM 차체 중심에서 앞바퀴 축까지 거리 */
private class FrontWheels(val axleOffsetM: Double, val angleDeg: Double)

/**
 * 위에서 본 차: 그림자, 차체(입체감 있는 그라데이션), 앞·뒤 유리, 지붕, 사이드미러, 헤드램프·후미등.
 * 차 중심을 원점, 앞쪽을 +x로 두고 그린 뒤 차 방향만큼 회전시킴.
 */
private fun DrawScope.drawCarTopDown(
    center: Vec2,
    heading: Double,
    lengthM: Double,
    widthM: Double,
    bodyColor: Color,
    proj: Projection,
    lights: CarLights? = null,
    frontWheels: FrontWheels? = null,
    sprite: CarTopSprite? = null,
    colorFilter: ColorFilter? = null,
) {
    val c = proj.toScreen(center)
    val l = proj.m(lengthM)
    val w = proj.m(widthM)
    val m = proj.scale  // 1m의 픽셀 수
    val corner = CornerRadius(0.42f * m)

    withTransform({
        translate(c.x, c.y)
        rotate(Math.toDegrees(heading).toFloat(), pivot = Offset.Zero)
    }) {
        // 그림자
        drawRoundRect(Color.Black.copy(alpha = 0.35f), topLeft = Offset(-l / 2 + 0.08f * m, -w / 2 + 0.12f * m), size = Size(l, w), cornerRadius = corner)

        // 앞바퀴: 핸들을 돌리면 꺾이는 게 보이도록 차체 밖으로 살짝 나오게 그림
        frontWheels?.let { fw ->
            val ax = proj.m(fw.axleOffsetM)
            for (s in listOf(-1f, 1f)) {
                val wc = Offset(ax, s * (w / 2 - 0.12f * m))
                rotate(fw.angleDeg.toFloat(), pivot = wc) {
                    drawRoundRect(Color(0xFF111214), topLeft = Offset(wc.x - 0.34f * m, wc.y - 0.13f * m),
                        size = Size(0.68f * m, 0.26f * m), cornerRadius = CornerRadius(0.08f * m))
                }
            }
        }

        if (sprite != null) {
            // 이미지: 가로 = 전장, 차체 부분 높이 = 전폭이 되도록 늘려서 그림 (미러는 그 바깥으로 나옴)
            val imgH = w / (sprite.bodyBottom - sprite.bodyTop)
            drawImage(
                image = sprite.image,
                dstOffset = IntOffset((-l / 2).roundToInt(), (-w / 2 - sprite.bodyTop * imgH).roundToInt()),
                dstSize = IntSize(l.roundToInt(), imgH.roundToInt()),
                colorFilter = colorFilter,
                filterQuality = FilterQuality.High,
            )
        } else {
            // 차체: 가운데가 밝고 양옆이 어두운 그라데이션으로 둥근 느낌
            val dark = bodyColor.darken(0.45f)
            val light = bodyColor.lighten(0.25f)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(dark, bodyColor, light, bodyColor, dark), startY = -w / 2, endY = w / 2),
                topLeft = Offset(-l / 2, -w / 2), size = Size(l, w), cornerRadius = corner,
            )

            // 사이드미러: 차체 양옆으로 12cm 튀어나옴
            for (s in listOf(-1f, 1f)) {
                val top = if (s > 0) w / 2 - 0.02f * m else -w / 2 - 0.12f * m
                drawRoundRect(dark, topLeft = Offset(0.13f * l, top), size = Size(0.2f * m, 0.14f * m), cornerRadius = CornerRadius(0.05f * m))
            }

            // 앞유리 (지붕 쪽이 살짝 좁은 사다리꼴)
            trapezoid(xNear = 0.10f * l, xFar = 0.25f * l, halfNear = 0.37f * w, halfFar = 0.42f * w, color = GlassDark)
            // 뒷유리
            trapezoid(xNear = -0.22f * l, xFar = -0.35f * l, halfNear = 0.37f * w, halfFar = 0.40f * w, color = GlassDark)
            // 지붕
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(bodyColor.darken(0.2f), bodyColor.lighten(0.15f), bodyColor.darken(0.2f)), startY = -0.37f * w, endY = 0.37f * w),
                topLeft = Offset(-0.22f * l, -0.37f * w), size = Size(0.32f * l, 0.74f * w), cornerRadius = CornerRadius(0.15f * m),
            )
            // 유리 반사광
            drawLine(Color.White.copy(alpha = 0.18f), Offset(0.16f * l, -0.3f * w), Offset(0.2f * l, 0.1f * w), strokeWidth = 0.05f * m)

            // 헤드램프
            for (s in listOf(-1f, 1f)) {
                drawRoundRect(HeadLight, topLeft = Offset(l / 2 - 0.16f * m, s * (w / 2 - 0.22f * m) - 0.17f * m),
                    size = Size(0.1f * m, 0.34f * m), cornerRadius = CornerRadius(0.04f * m))
            }

        }

        // 후미등: 브레이크를 밟으면 밝은 빨강 + 번짐
        val braking = lights?.brake == true
        for (s in listOf(-1f, 1f)) {
            val tl = Offset(-l / 2 + 0.03f * m, s * (w / 2 - 0.24f * m) - 0.2f * m)
            if (braking) {
                drawCircle(Brush.radialGradient(listOf(BrakeRed.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(tl.x, tl.y + 0.2f * m), radius = 0.7f * m), radius = 0.7f * m, center = Offset(tl.x, tl.y + 0.2f * m))
            }
            if (braking || sprite == null) {
                drawRoundRect(if (braking) BrakeRed else TailDim, topLeft = tl, size = Size(0.09f * m, 0.4f * m), cornerRadius = CornerRadius(0.04f * m))
            }
        }
        // 후진등: R일 때 뒤 범퍼 가운데 흰 불빛
        if (lights?.reverse == true) {
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(-l / 2, 0f), radius = 0.9f * m), radius = 0.9f * m, center = Offset(-l / 2, 0f))
            drawRoundRect(Color.White, topLeft = Offset(-l / 2 + 0.03f * m, -0.18f * m), size = Size(0.07f * m, 0.36f * m), cornerRadius = CornerRadius(0.03f * m))
        }
    }
}

/** x축을 따라 놓인 사다리꼴 (차 유리용). near/far는 차 중심에서의 x 위치 */
private fun DrawScope.trapezoid(xNear: Float, xFar: Float, halfNear: Float, halfFar: Float, color: Color) {
    val path = Path().apply {
        moveTo(xNear, -halfNear); lineTo(xFar, -halfFar); lineTo(xFar, halfFar); lineTo(xNear, halfNear); close()
    }
    drawPath(path, color)
}

/** 월드 좌표 위치에 글자를 가운데 맞춰 씀 */
private fun DrawScope.drawLabel(textMeasurer: TextMeasurer, text: String, at: Vec2, proj: Projection, sizeM: Double, color: Color) {
    val layout = textMeasurer.measure(text, TextStyle(color = color, fontSize = proj.m(sizeM).toSp(), fontWeight = FontWeight.Bold))
    val o = proj.toScreen(at)
    drawText(layout, topLeft = Offset(o.x - layout.size.width / 2f, o.y - layout.size.height / 2f))
}

private fun DrawScope.drawOriented(rect: OrientedRect, proj: Projection, color: Color, style: DrawStyle = Fill) {
    val path = Path()
    rect.corners().forEachIndexed { i, c ->
        val o = proj.toScreen(c)
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    path.close()
    drawPath(path, color, style = style)
}

private fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
private fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)

package com.team.hyuncha.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.team.hyuncha.input.Direction
import com.team.hyuncha.sim.Guidance
import com.team.hyuncha.sim.OrientedRect
import com.team.hyuncha.sim.ParkingMap
import com.team.hyuncha.sim.ParkingSimulator
import com.team.hyuncha.sim.SimStatus
import com.team.hyuncha.sim.Vec2
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// 주차장
private val Backdrop = Color(0xFF1E2024)
private val Asphalt = Color(0xFF34373C)
private val WallConcrete = Color(0xFF55595F)
private val CurbYellow = Color(0xFFE0B23A)
private val LineWhite = Color(0xFFE9E9E6)
private val WheelStop = Color(0xFFC9A646)
private val TargetGreen = Color(0xFF4CD08A)

// 차
private val MyCarBlue = Color(0xFF2F7BEA)
private val CrashedRed = Color(0xFFE5484D)
private val GlassDark = Color(0xFF1B2530)
private val HeadLight = Color(0xFFFFF4D6)
private val TailDim = Color(0xFF7A1418)
private val BrakeRed = Color(0xFFFF2D2D)
private val ParkedColors = listOf(
    Color(0xFFE8E8E6), Color(0xFF2B2D31), Color(0xFF8E949C), Color(0xFF26395C), Color(0xFF8C2B2B),
)

// 안내
private val GuideRed = Color(0xFFFF5A52)
private val GuideYellow = Color(0xFFFFD54F)
private val GuideGreen = Color(0xFF5BD68E)
private val TrailColor = Color(0xFFFFD54F)

/** 월드 좌표(m) → 화면 좌표(px) 변환 */
private class Projection(val scale: Float, val ox: Float, val oy: Float) {
    fun toScreen(p: Vec2) = Offset(ox + p.x.toFloat() * scale, oy + p.y.toFloat() * scale)
    fun m(meters: Double) = meters.toFloat() * scale
}

/** 아스팔트 질감용 점 (맵마다 한 번만 만들어 둠) */
private class Speckle(val pos: Vec2, val radiusM: Float, val color: Color)

/**
 * 탑뷰 주차장 그리기.
 * @param frame 매 프레임 바뀌는 값. draw 안에서 읽어야 프레임마다 다시 그려짐
 * @param direction 지금 고른 진행 방향 (예상 진행선·후진등에 사용)
 * @param braking 브레이크를 밟고 있는지 (브레이크등에 사용)
 */
@Composable
fun ParkingCanvas(
    sim: ParkingSimulator,
    frame: () -> Long,
    direction: () -> Direction,
    braking: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val speckles = remember(sim.map) { makeSpeckles(sim.map) }

    Canvas(modifier) {
        frame()  // 이 값을 읽어둬야 시뮬레이터가 움직일 때 다시 그려짐
        val map = sim.map

        // 주차장 전체가 화면에 들어오도록 축척 계산 (여백 1m)
        val margin = 1.0
        val scale = min(
            size.width / (map.widthM + margin * 2),
            size.height / (map.heightM + margin * 2),
        ).toFloat()
        val proj = Projection(
            scale = scale,
            ox = (size.width - map.widthM.toFloat() * scale) / 2,
            oy = (size.height - map.heightM.toFloat() * scale) / 2,
        )
        val pulse = 0.5f + 0.5f * sin(System.nanoTime() / 1e9 * 3.0).toFloat()  // 0~1, 목표 칸 깜빡임

        drawRect(Backdrop)
        drawGround(map, proj, speckles)
        drawBays(map, proj, textMeasurer, pulse)
        drawAisleArrows(map, proj)

        map.parkedCars.forEachIndexed { i, rect ->
            // 주차된 차는 색과 방향을 섞어서 같은 차만 늘어선 느낌을 줄임
            val heading = if (i % 2 == 0) rect.angle else rect.angle + Math.PI
            drawCarTopDown(
                center = rect.center, heading = heading,
                lengthM = rect.halfLength * 2, widthM = rect.halfWidth * 2,
                bodyColor = ParkedColors[i % ParkedColors.size], proj = proj,
            )
        }

        // 지나온 경로 (옅게)
        if (sim.trail.size >= 2) {
            val path = Path()
            sim.trail.forEachIndexed { i, p ->
                val o = proj.toScreen(Vec2(p.x, p.y))
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, TrailColor.copy(alpha = 0.3f), style = Stroke(width = 2f))
        }

        if (sim.status == SimStatus.DRIVING) {
            drawGuideLines(sim, direction(), proj)
        }

        val body = sim.bodyRect()
        val reversing = direction() == Direction.REVERSE
        drawCarTopDown(
            center = body.center, heading = sim.pose.heading,
            lengthM = sim.car.lengthM, widthM = sim.car.widthM,
            bodyColor = if (sim.status == SimStatus.COLLIDED) CrashedRed else MyCarBlue,
            proj = proj,
            lights = CarLights(brake = braking(), reverse = reversing),
            frontWheels = FrontWheels(
                axleOffsetM = sim.car.wheelBaseM - (sim.car.lengthM / 2 - sim.car.rearOverhangM),
                angleDeg = sim.wheelAngleDeg,
            ),
        )

        if (sim.status != SimStatus.PARKED) {
            drawSensors(Guidance.readSensors(body, map.obstacles), proj)
        }
    }
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
        // 칸이 세로(π/2)로 놓여 있으므로 halfLength 방향이 y
        val entrance = bay.center - Vec2.unit(bay.angle) * (bay.halfLength - 0.55)
        val stopPos = bay.center + Vec2.unit(bay.angle) * (bay.halfLength - 0.55)
        drawLabel(textMeasurer, "A${i + 1}", entrance, proj, sizeM = 0.42, color = LineWhite.copy(alpha = 0.55f))
        drawOriented(OrientedRect(stopPos, halfLength = 0.09, halfWidth = 0.75, angle = bay.angle), proj, WheelStop)
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
private fun DrawScope.drawGuideLines(sim: ParkingSimulator, direction: Direction, proj: Projection) {
    val points = Guidance.predictPath(sim.car, sim.pose, sim.wheelAngleDeg, direction)
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

        // 후미등: 브레이크를 밟으면 밝은 빨강 + 번짐
        val braking = lights?.brake == true
        for (s in listOf(-1f, 1f)) {
            val tl = Offset(-l / 2 + 0.03f * m, s * (w / 2 - 0.24f * m) - 0.2f * m)
            if (braking) {
                drawCircle(Brush.radialGradient(listOf(BrakeRed.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(tl.x, tl.y + 0.2f * m), radius = 0.7f * m), radius = 0.7f * m, center = Offset(tl.x, tl.y + 0.2f * m))
            }
            drawRoundRect(if (braking) BrakeRed else TailDim, topLeft = tl, size = Size(0.09f * m, 0.4f * m), cornerRadius = CornerRadius(0.04f * m))
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

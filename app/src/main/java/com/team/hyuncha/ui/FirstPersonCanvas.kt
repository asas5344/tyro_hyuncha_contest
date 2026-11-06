package com.team.hyuncha.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.team.hyuncha.data.CarCamera
import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.data.CarType
import com.team.hyuncha.input.Direction
import com.team.hyuncha.sim.CarPose
import com.team.hyuncha.sim.Guidance
import com.team.hyuncha.sim.OrientedRect
import com.team.hyuncha.sim.ParkingMode
import com.team.hyuncha.sim.ParkingSimulator
import com.team.hyuncha.sim.Vec2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

// 1인칭 색
private val Ceiling = Color(0xFF141619)          // 지하 주차장 천장
private val FarFloor = Color(0xFF26292D)         // 주차장 밖 바닥
private val Asphalt3D = Color(0xFF3A3E44)
private val Line3D = Color(0xFFE6E6E2)
private val Curb3D = Color(0xFFD9AE3A)
private val Wall3D = Color(0xFF6A6E75)
private val Stop3D = Color(0xFFC9A646)
private val Target3D = Color(0xFF4CD08A)
private val Glass3D = Color(0xFF1C2631)
private val Dash = Color(0xFF17191C)
private val Pillar = Color(0xFF101113)
private val WheelRim = Color(0xFF2B2E33)
private val Hood = Color(0xFFA9ACB1)              // 흰 차지만 어두운 지하 주차장이라 회색빛으로
private val GuideRed3D = Color(0xFFFF5A52)
private val GuideYellow3D = Color(0xFFFFD54F)
private val GuideGreen3D = Color(0xFF5BD68E)

/** 3D 점(m). x·y는 탑뷰와 같은 바닥 좌표(y가 아래로 양수), z는 위쪽 */
internal data class V3(val x: Double, val y: Double, val z: Double)

private const val NEAR = 0.1   // 카메라 바로 앞 이 거리보다 가까운 부분은 잘라냄 (m)

/**
 * 원근 카메라: eye에서 heading 방향을 보고 pitch만큼 아래를 내려다봄.
 * 카메라 좌표는 (앞 거리, 오른쪽, 위쪽)이고, 앞 거리로 나눠서 멀수록 작게 그림.
 * @param mirror 좌우를 뒤집어서 그림 (후방 카메라 화면은 거울처럼 보여줌)
 */
internal class PerspectiveCam(
    val eye: V3,
    heading: Double,
    pitchDeg: Double,
    vFovDeg: Double,
    val area: Rect,
    val mirror: Boolean = false,
) {
    private val pitch = Math.toRadians(pitchDeg)
    private val fx = cos(heading) * cos(pitch)
    private val fy = sin(heading) * cos(pitch)
    private val fz = -sin(pitch)
    private val rx = -sin(heading)                 // 오른쪽 (탑뷰에서 차의 오른쪽과 같음)
    private val ry = cos(heading)
    private val ux = cos(heading) * sin(pitch)
    private val uy = sin(heading) * sin(pitch)
    private val uz = cos(pitch)
    val focal = (area.height / 2) / tan(Math.toRadians(vFovDeg / 2))
    private val cx = area.center.x
    private val cy = area.center.y

    /** 월드 → 카메라 좌표 (x = 앞 거리, y = 오른쪽, z = 위쪽) */
    fun toCam(w: V3): V3 {
        val dx = w.x - eye.x
        val dy = w.y - eye.y
        val dz = w.z - eye.z
        return V3(dx * fx + dy * fy + dz * fz, dx * rx + dy * ry, dx * ux + dy * uy + dz * uz)
    }

    fun project(c: V3): Offset = Offset(
        cx + (if (mirror) -1f else 1f) * (c.y / c.x * focal).toFloat(),
        cy - (c.z / c.x * focal).toFloat(),
    )

    /** 지평선(눈높이의 아주 먼 곳)의 화면 y */
    val horizonY: Float get() = (cy - tan(pitch) * focal).toFloat()
}

/** 카메라 앞쪽(앞 거리 NEAR 이상)만 남기도록 다각형을 자름 */
private fun clipNear(poly: List<V3>): List<V3> {
    val out = ArrayList<V3>(poly.size + 2)
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        val ain = a.x >= NEAR
        val bin = b.x >= NEAR
        if (ain) out += a
        if (ain != bin) {
            val t = (NEAR - a.x) / (b.x - a.x)
            out += V3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)
        }
    }
    return out
}

private fun DrawScope.poly3(cam: PerspectiveCam, pts: List<V3>, color: Color) {
    val clipped = clipNear(pts.map(cam::toCam))
    if (clipped.size < 3) return
    val path = Path()
    clipped.forEachIndexed { i, c ->
        val o = cam.project(c)
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    path.close()
    drawPath(path, color)
}

/** 바닥에 그린 두께 있는 선 (원근에 맞게 멀수록 가늘어짐) */
private fun DrawScope.groundStrip(cam: PerspectiveCam, a: Vec2, b: Vec2, widthM: Double, color: Color, z: Double = 0.01) {
    val len = hypot(b.x - a.x, b.y - a.y)
    if (len < 1e-6) return
    val nx = -(b.y - a.y) / len * widthM / 2
    val ny = (b.x - a.x) / len * widthM / 2
    poly3(cam, listOf(V3(a.x + nx, a.y + ny, z), V3(b.x + nx, b.y + ny, z), V3(b.x - nx, b.y - ny, z), V3(a.x - nx, a.y - ny, z)), color)
}

private fun DrawScope.groundRect(cam: PerspectiveCam, r: OrientedRect, color: Color, z: Double = 0.005) =
    poly3(cam, r.corners().map { V3(it.x, it.y, z) }, color)

/** 바닥에 놓인 상자 (주차된 차·벽·바퀴 멈춤턱) */
private class Box(val foot: OrientedRect, val z0: Double, val z1: Double, val side: Color, val top: Color)

private fun shade(c: Color, k: Double) = Color((c.red * k).toFloat().coerceIn(0f, 1f), (c.green * k).toFloat().coerceIn(0f, 1f), (c.blue * k).toFloat().coerceIn(0f, 1f), c.alpha)

/** 상자의 카메라 쪽 면만 그림. 볼록한 상자라 보이는 면끼리는 겹치지 않음 */
private fun DrawScope.box3(cam: PerspectiveCam, b: Box) {
    val c = b.foot.corners()
    val center = b.foot.center
    for (i in 0 until 4) {
        val p = c[i]
        val q = c[(i + 1) % 4]
        val mx = (p.x + q.x) / 2
        val my = (p.y + q.y) / 2
        val nx = mx - center.x
        val ny = my - center.y
        if (nx * (cam.eye.x - mx) + ny * (cam.eye.y - my) <= 0) continue   // 카메라 반대쪽 면
        // 빛은 위·앞쪽에서: 면 방향에 따라 조금씩 밝기를 다르게
        val n = hypot(nx, ny).coerceAtLeast(1e-6)
        val light = 0.62 + 0.3 * max(0.0, (nx * 0.4 - ny * 0.6) / n)
        poly3(cam, listOf(V3(p.x, p.y, b.z0), V3(q.x, q.y, b.z0), V3(q.x, q.y, b.z1), V3(p.x, p.y, b.z1)), shade(b.side, light))
    }
    if (cam.eye.z > b.z1) poly3(cam, c.map { V3(it.x, it.y, b.z1) }, b.top)
}

/**
 * 원근 카메라로 본 주차장: 천장·바닥·주차선·목표 칸·예상 진행선·벽·주차된 차.
 * 내 차는 그리지 않음 (운전석·후방 카메라에서는 내 차가 안 보이므로).
 */
private fun DrawScope.drawPerspectiveScene(scene: Scene, assets: TopAssets, cam: PerspectiveCam, guideDirection: Direction?) {
    val map = scene.sim.map
    val a = cam.area
    // 하늘(천장)과 먼 바닥
    val hy = cam.horizonY.coerceIn(a.top, a.bottom)
    drawRect(Brush.verticalGradient(listOf(Ceiling, Color(0xFF22252A)), startY = a.top, endY = hy), topLeft = a.topLeft, size = Size(a.width, hy - a.top))
    drawRect(FarFloor, topLeft = Offset(a.left, hy), size = Size(a.width, a.bottom - hy))

    // 주차장 바닥, 연석, 주차선
    poly3(cam, listOf(V3(0.0, 0.0, 0.0), V3(map.widthM, 0.0, 0.0), V3(map.widthM, map.heightM, 0.0), V3(0.0, map.heightM, 0.0)), Asphalt3D)
    val corners = listOf(Vec2(0.0, 0.0), Vec2(map.widthM, 0.0), Vec2(map.widthM, map.heightM), Vec2(0.0, map.heightM))
    corners.zip(corners.drop(1) + corners.first()).forEach { (p, q) -> groundStrip(cam, p, q, 0.14, Curb3D) }
    map.lines.forEach { (p, q) -> groundStrip(cam, p, q, 0.12, Line3D) }

    // 목표 칸: 바닥을 초록으로 + 테두리
    groundRect(cam, map.slot, Target3D.copy(alpha = 0.28f))
    val sc = map.slot.corners()
    sc.zip(sc.drop(1) + sc.first()).forEach { (p, q) -> groundStrip(cam, p, q, 0.1, Target3D, z = 0.012) }

    // 주차된 차 그림자
    map.parkedCars.forEach { groundRect(cam, it.copy(halfLength = it.halfLength + 0.15, halfWidth = it.halfWidth + 0.12), Color.Black.copy(alpha = 0.35f), z = 0.008) }

    // 예상 진행선 (탑뷰와 같은 색: 1m 빨강 → 2m 노랑 → 초록)
    if (guideDirection != null && scene.showGuide) {
        val pts = Guidance.predictPath(scene.sim.car, scene.pose, scene.wheelAngleDeg, guideDirection)
        fun colorAt(d: Double) = when { d < 1.0 -> GuideRed3D; d < 2.0 -> GuideYellow3D; else -> GuideGreen3D }
        pts.zipWithNext().forEach { (p, q) ->
            poly3(cam, listOf(V3(p.left.x, p.left.y, 0.015), V3(q.left.x, q.left.y, 0.015), V3(q.right.x, q.right.y, 0.015), V3(p.right.x, p.right.y, 0.015)), GuideYellow3D.copy(alpha = 0.10f))
            val c = colorAt(p.distanceM)
            groundStrip(cam, p.left, q.left, 0.09, c, z = 0.02)
            groundStrip(cam, p.right, q.right, 0.09, c, z = 0.02)
        }
        listOf(1.0, 2.0, 3.0).forEach { mark ->
            pts.minByOrNull { kotlin.math.abs(it.distanceM - mark) }?.let { p -> groundStrip(cam, p.left, p.right, 0.07, colorAt(mark - 0.01), z = 0.02) }
        }
    }

    // 벽은 항상 맨 뒤(주차장 바깥 테두리)라 먼저 그림
    map.walls.forEach { box3(cam, Box(it, 0.0, 2.6, Wall3D, shade(Wall3D, 1.1))) }

    // 주차된 차·바퀴 멈춤턱: 먼 것부터 그려서 가까운 것이 위에 오게
    val boxes = ArrayList<Pair<Double, List<Box>>>()
    map.parkedCars.forEachIndexed { i, r ->
        val tint = assets.parkedStyles.getOrNull(i)?.tint ?: Color.Gray
        val bodyColor = shade(tint, 1.15)
        val cabin = r.copy(halfLength = r.halfLength * 0.52, halfWidth = r.halfWidth * 0.86)
        val parts = listOf(
            Box(r, 0.18, 0.82, bodyColor, shade(bodyColor, 1.12)),
            Box(cabin, 0.82, 1.42, Glass3D, shade(bodyColor, 1.05)),
        )
        boxes += hypot(r.center.x - cam.eye.x, r.center.y - cam.eye.y) to parts
    }
    if (map.mode != ParkingMode.PARALLEL) {
        map.bays.forEach { bay ->
            val stop = bay.center + Vec2.unit(bay.angle) * (bay.halfLength - 0.55)
            val foot = OrientedRect(stop, halfLength = 0.09, halfWidth = 0.75, angle = bay.angle)
            boxes += hypot(stop.x - cam.eye.x, stop.y - cam.eye.y) to listOf(Box(foot, 0.0, 0.12, Stop3D, shade(Stop3D, 1.15)))
        }
    }
    boxes.sortedByDescending { it.first }.forEach { (_, parts) -> parts.forEach { box3(cam, it) } }
}

/** 차 기준 좌표(앞 a, 오른쪽 b, 높이 z)를 월드 좌표로 */
private fun local(pose: CarPose, a: Double, b: Double, z: Double): V3 {
    val c = cos(pose.heading)
    val s = sin(pose.heading)
    return V3(pose.x + c * a - s * b, pose.y + s * a + c * b, z)
}

/** 운전석 눈 위치: 차 길이의 약 45% 지점, 왼쪽 좌석(한국은 왼쪽 운전석), 세단·SUV 높이 다르게 */
private fun driverCam(car: CarSpec, pose: CarPose, area: Rect): PerspectiveCam {
    val forward = car.lengthM * 0.45 - car.rearOverhangM
    val eyeZ = if (car.type == CarType.SUV) 1.38 else 1.18
    // 거의 수평으로 앞을 봄. 보닛 바로 아래부터는 대시보드에 가려 바닥이 안 보이는 실제 운전석과 같게
    return PerspectiveCam(local(pose, forward, -0.37, eyeZ), pose.heading, pitchDeg = 2.0, vFovDeg = 56.0, area = area)
}

/** 후방 카메라: 뒤 범퍼 위에서 뒤쪽 바닥을 넓게 내려다봄. 실제 차처럼 좌우를 거울처럼 뒤집어 보여줌 */
private fun rearCam(car: CarSpec, pose: CarPose, area: Rect): PerspectiveCam {
    val z = if (car.type == CarType.SUV) 1.05 else 0.95
    return PerspectiveCam(local(pose, -car.rearOverhangM + 0.05, 0.0, z), pose.heading + Math.PI, pitchDeg = 34.0, vFovDeg = 72.0, area = area, mirror = true)
}

/**
 * 1인칭(운전석) 주차 화면.
 * - 큰 화면: 운전석에서 본 앞유리 너머 (보닛·대시보드·돌아가는 핸들 포함)
 * - 오른쪽 위 작은 화면(차량 디스플레이): 전진 중엔 내 차 주변 미니맵,
 *   후진하면 커지면서 내 차 카메라 종류에 따라 후방 카메라 또는 탑뷰(서라운드 뷰)
 */
@Composable
fun FirstPersonCanvas(
    sim: ParkingSimulator,
    frame: () -> Long,
    direction: () -> Direction,
    braking: () -> Boolean,
    camera: CarCamera,
    modifier: Modifier = Modifier,
    replay: () -> ReplayView? = { null },
) {
    val textMeasurer = rememberTextMeasurer()
    val assets = rememberTopAssets(sim)

    Canvas(modifier) {
        frame()
        val scene = sceneOf(sim, replay(), direction(), braking())
        val car = sim.car
        val main = Rect(0f, 0f, size.width, size.height)

        // 1) 운전석 시점
        clipRect(main.left, main.top, main.right, main.bottom) {
            drawPerspectiveScene(scene, assets, driverCam(car, scene.pose, main), Direction.FORWARD.takeIf { scene.direction == Direction.FORWARD })
        }
        drawCockpit(car, scene, main)

        // 2) 차량 디스플레이 (오른쪽 위)
        val reversing = scene.direction == Direction.REVERSE
        val iw = size.width * if (reversing) 0.44f else 0.26f
        val ih = iw * 0.62f
        val margin = 24f
        val inset = Rect(size.width - iw - margin, margin, size.width - margin, margin + ih)
        val round = RoundRect(inset, CornerRadius(22f))
        val rearCamera = reversing && camera == CarCamera.REAR_ONLY
        val label = when {
            rearCamera -> "후방 카메라"
            reversing -> "서라운드 뷰"
            else -> "미니맵"
        }
        clipPath(Path().apply { addRoundRect(round) }) {
            if (rearCamera) {
                drawPerspectiveScene(scene, assets, rearCam(car, scene.pose, inset), Direction.REVERSE)
                drawRearCamOverlay(inset)
            } else {
                drawSurround(scene, assets, inset, viewM = if (reversing) 13.0 else 22.0, textMeasurer = textMeasurer,
                    focusY = if (reversing) 0.36f else 0.5f)
            }
        }
        drawRoundRect(Color.White.copy(alpha = 0.55f), topLeft = inset.topLeft, size = inset.size, cornerRadius = CornerRadius(22f), style = Stroke(width = 3f))
        val layout = textMeasurer.measure(label, TextStyle(color = Color.White, fontSize = (20f).toSp(), fontWeight = FontWeight.Bold))
        drawRoundRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(inset.left + 14f, inset.top + 14f),
            size = Size(layout.size.width + 24f, layout.size.height + 12f), cornerRadius = CornerRadius(12f))
        drawText(layout, topLeft = Offset(inset.left + 26f, inset.top + 20f))
    }
}

/**
 * 내 차를 위에서 본 화면. 실제 서라운드 뷰처럼 내 차가 항상 위쪽을 향하게 돌림.
 * @param focusY 내 차를 화면 위에서 몇 % 높이에 둘지 (후진할 때는 위쪽에 둬서 차 뒤쪽을 더 넓게 보여줌)
 */
private fun DrawScope.drawSurround(scene: Scene, assets: TopAssets, area: Rect, viewM: Double, textMeasurer: TextMeasurer, focusY: Float = 0.5f) {
    drawRect(Backdrop, topLeft = area.topLeft, size = area.size)
    val scale = (area.width / viewM).toFloat()
    val c = scene.body.center
    val focus = Offset(area.center.x, area.top + area.height * focusY)
    val proj = Projection(scale, focus.x - c.x.toFloat() * scale, focus.y - c.y.toFloat() * scale)
    withTransform({ rotate(-Math.toDegrees(scene.pose.heading).toFloat() - 90f, pivot = focus) }) {
        drawTopScene(scene, assets, proj, textMeasurer)
    }
}

/** 후방 카메라 화면 아래쪽의 내 차 범퍼와 안내 문구 */
private fun DrawScope.drawRearCamOverlay(area: Rect) {
    val bumper = Path().apply {
        moveTo(area.left, area.bottom)
        lineTo(area.left, area.bottom - area.height * 0.07f)
        quadraticTo(area.center.x, area.bottom - area.height * 0.15f, area.right, area.bottom - area.height * 0.07f)
        lineTo(area.right, area.bottom)
        close()
    }
    drawPath(bumper, Color(0xFF2A2C30))
    drawPath(bumper, Color.White.copy(alpha = 0.25f), style = Stroke(width = 3f))
}

/**
 * 운전석 안쪽: 앞 기둥(A필러), 보닛, 대시보드, 핸들.
 * 보닛은 실제 차 크기대로 원근 투영해서 앞 범퍼가 어디쯤인지 감을 잡을 수 있게 함.
 * 핸들은 바퀴 각도 × 조향비만큼(= 핸들 각도) 돌아감.
 */
private fun DrawScope.drawCockpit(car: CarSpec, scene: Scene, area: Rect) {
    val cam = driverCam(car, scene.pose, area)
    val front = car.lengthM - car.rearOverhangM - 0.05
    val windshield = car.lengthM * 0.45 - car.rearOverhangM + 0.85
    val hw = car.widthM / 2
    // 보닛: 앞 범퍼 끝에서 앞유리 아래까지
    val hoodPts = listOf(
        local(scene.pose, front, -(hw - 0.2), 0.86), local(scene.pose, front, hw - 0.2, 0.86),
        local(scene.pose, windshield, hw - 0.12, 1.0), local(scene.pose, windshield, -(hw - 0.12), 1.0),
    )
    poly3(cam, hoodPts, Hood)
    // 보닛 가운데 볼록한 선과 앞쪽 가장자리 반사광
    poly3(cam, listOf(local(scene.pose, front, -0.25, 0.87), local(scene.pose, front, 0.25, 0.87),
        local(scene.pose, windshield, 0.35, 1.0), local(scene.pose, windshield, -0.35, 1.0)), shade(Hood, 1.12))
    poly3(cam, listOf(hoodPts[0], hoodPts[1], local(scene.pose, front - 0.12, hw - 0.22, 0.88), local(scene.pose, front - 0.12, -(hw - 0.22), 0.88)), Color.White.copy(alpha = 0.25f))

    // 앞 기둥 (운전석이 왼쪽이라 오른쪽 기둥이 더 멀고 가늘게 보임)
    val w = area.width
    val h = area.height
    drawPath(Path().apply {
        moveTo(area.left, area.top); lineTo(area.left + w * 0.10f, area.top)
        lineTo(area.left + w * 0.03f, area.top + h * 0.72f); lineTo(area.left, area.top + h * 0.78f); close()
    }, Pillar)
    drawPath(Path().apply {
        moveTo(area.right, area.top); lineTo(area.right - w * 0.05f, area.top)
        lineTo(area.right - w * 0.012f, area.top + h * 0.62f); lineTo(area.right, area.top + h * 0.66f); close()
    }, Pillar)

    // 대시보드: 앞유리 아래쪽 선(= 보닛이 끝나는 곳)부터 화면 아래까지. 그 아래 바닥은 실제로도 안 보임
    val wsCam = cam.toCam(local(scene.pose, windshield, 0.0, 1.0))
    val dashTop = (if (wsCam.x > NEAR) cam.project(wsCam).y else area.bottom - h * 0.3f).coerceIn(area.top + h * 0.4f, area.bottom - h * 0.1f)
    drawPath(Path().apply {
        moveTo(area.left, area.bottom)
        lineTo(area.left, dashTop + h * 0.03f)
        quadraticTo(area.center.x, dashTop - h * 0.015f, area.right, dashTop + h * 0.04f)
        lineTo(area.right, area.bottom)
        close()
    }, Brush.verticalGradient(listOf(Color(0xFF24272C), Dash), startY = dashTop - h * 0.05f, endY = area.bottom))

    // 핸들: 화면 아래 가운데, 핸들 각도만큼 돌아감 (오른쪽으로 돌리면 시계 방향)
    val steeringDeg = (scene.wheelAngleDeg * car.steeringRatio).toFloat()
    val r = ((area.bottom - dashTop) * 0.95f).coerceAtMost(h * 0.34f)
    val c = Offset(area.center.x, area.bottom + r * 0.12f)
    rotate(steeringDeg, pivot = c) {
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = r + r * 0.09f, center = c + Offset(0f, 6f), style = Stroke(width = r * 0.16f))
        drawCircle(WheelRim, radius = r, center = c, style = Stroke(width = r * 0.15f))
        drawCircle(Color.White.copy(alpha = 0.08f), radius = r - r * 0.05f, center = c, style = Stroke(width = r * 0.03f))
        // 살 3개 (왼쪽·오른쪽·아래)
        listOf(180f, 0f, 90f).forEach { deg ->
            val rad = Math.toRadians(deg.toDouble())
            val end = c + Offset((cos(rad) * r * 0.93).toFloat(), (sin(rad) * r * 0.93).toFloat())
            drawLine(WheelRim, c, end, strokeWidth = r * 0.13f, cap = StrokeCap.Round)
        }
        drawCircle(Color(0xFF35383E), radius = r * 0.24f, center = c)
        // 12시 표시: 핸들이 얼마나 돌았는지 바로 보이게
        drawRoundRect(Color(0xFFE0E0E0), topLeft = Offset(c.x - r * 0.05f, c.y - r - r * 0.08f), size = Size(r * 0.1f, r * 0.16f), cornerRadius = CornerRadius(r * 0.03f))
    }
}

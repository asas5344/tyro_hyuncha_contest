package com.team.hyuncha.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.team.hyuncha.data.CarCamera
import com.team.hyuncha.input.Direction
import com.team.hyuncha.sim.ParkingSimulator

/**
 * The same driving aid used by the original cockpit, shared with the 3D presentation.
 * @param reverseCamera 후진 중 미니맵 자리에 띄울 카메라 (null이면 미니맵).
 *   서라운드 뷰는 여기서 2D 탑뷰로 그리고, 후방 카메라는 테두리만 그려 가운데로 3D 화면이 그린 카메라 화면이 보이게 함
 */
@Composable
internal fun DriverMinimap(
    sim: ParkingSimulator,frame: () -> Long,direction: () -> Direction,braking: () -> Boolean,
    replay: () -> ReplayView?,modifier: Modifier,reverseCamera: () -> CarCamera? = { null },
) {
    val assets=rememberTopAssets(sim)
    val textMeasurer=rememberTextMeasurer()
    Canvas(modifier) {
        frame()
        val area=Rect(0f,0f,size.width,size.height)
        val scene=sceneOf(sim,replay(),direction(),braking())
        when(reverseCamera()) {
            CarCamera.REAR_ONLY -> drawScreenBezel(area,"후방 카메라",textMeasurer)
            // 내 차를 위쪽에 둬서 차 뒤쪽을 더 넓게 보여 줌 (차량 화면의 서라운드 뷰와 같은 배율)
            CarCamera.SURROUND -> drawScreenFrame(area,"서라운드 뷰",textMeasurer) {
                drawSurround(scene,assets,area,viewM=13.0,textMeasurer=textMeasurer,focusY=0.36f)
            }
            null -> drawScreenFrame(area,"미니맵",textMeasurer) {
                drawSurround(scene,assets,area,viewM=18.0,textMeasurer=textMeasurer)
            }
        }
    }
}

/** drawScreenFrame과 같은 테두리·이름표지만 가운데를 칠하지 않음 (뒤에 깔린 화면이 그대로 보임) */
private fun DrawScope.drawScreenBezel(r: Rect, label: String, textMeasurer: TextMeasurer) {
    val outer = Rect(r.left - 10f, r.top - 10f, r.right + 10f, r.bottom + 10f)
    val ring = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(RoundRect(outer, CornerRadius(28f)))
        addRoundRect(RoundRect(r, CornerRadius(20f)))
    }
    drawPath(ring, Color(0xFF0B0C0E))
    drawRoundRect(Color.White.copy(alpha = 0.18f), topLeft = outer.topLeft, size = outer.size, cornerRadius = CornerRadius(28f), style = Stroke(width = 2f))
    val layout = textMeasurer.measure(label, TextStyle(color = Color.White, fontSize = (18f).toSp(), fontWeight = FontWeight.Bold))
    drawRoundRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(r.left + 12f, r.top + 12f),
        size = Size(layout.size.width + 22f, layout.size.height + 10f), cornerRadius = CornerRadius(12f))
    drawText(layout, topLeft = Offset(r.left + 23f, r.top + 17f))
}

/** 화면 속 작은 화면: 그림자·검은 테두리·둥근 모서리, 왼쪽 위에 이름표 */
private fun DrawScope.drawScreenFrame(r: Rect, label: String, textMeasurer: TextMeasurer, content: DrawScope.() -> Unit) {
    drawRoundRect(Color.Black.copy(alpha = 0.45f), topLeft = r.topLeft + Offset(6f, 10f), size = r.size, cornerRadius = CornerRadius(26f))
    drawRoundRect(Color(0xFF0B0C0E), topLeft = r.topLeft - Offset(10f, 10f), size = Size(r.width + 20f, r.height + 20f), cornerRadius = CornerRadius(28f))
    clipPath(Path().apply { addRoundRect(RoundRect(r, CornerRadius(20f))) }) { content() }
    drawRoundRect(Color.White.copy(alpha = 0.18f), topLeft = r.topLeft - Offset(10f, 10f), size = Size(r.width + 20f, r.height + 20f),
        cornerRadius = CornerRadius(28f), style = Stroke(width = 2f))
    val layout = textMeasurer.measure(label, TextStyle(color = Color.White, fontSize = (18f).toSp(), fontWeight = FontWeight.Bold))
    drawRoundRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(r.left + 12f, r.top + 12f),
        size = Size(layout.size.width + 22f, layout.size.height + 10f), cornerRadius = CornerRadius(12f))
    drawText(layout, topLeft = Offset(r.left + 23f, r.top + 17f))
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

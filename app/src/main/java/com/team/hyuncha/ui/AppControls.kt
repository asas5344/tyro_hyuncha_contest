package com.team.hyuncha.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.team.hyuncha.ui.theme.LocalAppTextScale

/** 글자 확대 시 터치 영역도 커진다. 화면 전체의 density는 변경하지 않는다. */
@Composable
internal fun controlHeight(base: Dp = 100.dp): Dp = base * (LocalDensity.current.fontScale * LocalAppTextScale.current).coerceAtLeast(1f)

@Composable
internal fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val factor by animateFloatAsState(if (pressed) .98f else 1f,
        spring(dampingRatio = 1f, stiffness = 700f), label = "button_press")
    Button(onClick = onClick,
        modifier = modifier.heightIn(min = controlHeight(88.dp)).graphicsLayer { scaleX = factor; scaleY = factor },
        shape = RoundedCornerShape(24.dp), interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) colors.primary else colors.surfaceVariant,
            contentColor = if (primary) colors.onPrimary else colors.onSurface),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
        if (icon == "back") {
            OutlineIcon(icon, Modifier.size(30.dp)); Spacer(Modifier.width(12.dp))
        }
        Text(label, style = if (primary) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge)
        if (icon != null && icon != "back") {
            Spacer(Modifier.width(16.dp)); OutlineIcon(icon, Modifier.size(32.dp))
        }
    }
}

@Composable
internal fun OutlineIcon(kind: String, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val u = size.minDimension / 32f
        val path = Path()
        fun line(vararg p: Float) {
            path.moveTo(p[0] * u, p[1] * u)
            for (i in 2 until p.size step 2) path.lineTo(p[i] * u, p[i + 1] * u)
        }
        when (kind) {
            "power" -> {
                drawArc(color, -50f, 280f, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(5f * u, 6f * u),
                    size = androidx.compose.ui.geometry.Size(22f * u, 22f * u),
                    style = Stroke(2.2f * u, cap = StrokeCap.Round))
                line(16f, 3f, 16f, 16f)
            }
            "parking" -> {
                line(7f,27f,7f,5f,18f,5f)
                path.cubicTo(30f*u,5f*u,30f*u,19f*u,18f*u,19f*u)
                line(7f,19f,18f,19f)
            }
            "car" -> {
                line(5f,14f,9f,6f,23f,6f,27f,14f)
                line(4f,14f,28f,14f,28f,25f,4f,25f,4f,14f)
                line(8f,25f,8f,28f); line(24f,25f,24f,28f)
                line(8f,19f,11f,19f); line(21f,19f,24f,19f)
            }
            "settings" -> {
                line(4f,8f,28f,8f); line(4f,16f,28f,16f); line(4f,24f,28f,24f)
                drawCircle(color,3*u,androidx.compose.ui.geometry.Offset(11*u,8*u))
                drawCircle(color,3*u,androidx.compose.ui.geometry.Offset(22*u,16*u))
                drawCircle(color,3*u,androidx.compose.ui.geometry.Offset(13*u,24*u))
            }
            "trophy" -> {
                // 컵
                line(9f,5f,23f,5f,23f,10f)
                path.cubicTo(23f*u,16f*u,19f*u,19f*u,16f*u,19f*u)
                path.cubicTo(13f*u,19f*u,9f*u,16f*u,9f*u,10f*u)
                path.close()
                // 손잡이
                line(9f,7f,5f,7f,5f,9f); path.cubicTo(5f*u,12f*u,7f*u,14f*u,9.5f*u,14f*u)
                line(23f,7f,27f,7f,27f,9f); path.cubicTo(27f*u,12f*u,25f*u,14f*u,22.5f*u,14f*u)
                // 기둥과 받침
                line(16f,19f,16f,24f); line(11f,27f,21f,27f); line(12f,24f,20f,24f)
            }
            "mic" -> {
                // 마이크 머리
                path.addRoundRect(androidx.compose.ui.geometry.RoundRect(12f*u, 4f*u, 20f*u, 19f*u, 4f*u, 4f*u))
                // 받침
                line(8f,15f); path.cubicTo(8f*u,21f*u,11f*u,23f*u,16f*u,23f*u)
                path.cubicTo(21f*u,23f*u,24f*u,21f*u,24f*u,15f*u)
                line(16f,23f,16f,28f); line(12f,28f,20f,28f)
            }
            "check" -> line(5f,16f,12f,23f,27f,8f)
            "chevron" -> line(12f,6f,22f,16f,12f,26f)
            "back" -> { line(26f,16f,5f,16f); line(14f,7f,5f,16f,14f,25f) }
            "arrow" -> { line(4f,16f,28f,16f); line(19f,7f,28f,16f,19f,25f) }
        }
        drawPath(path, color, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

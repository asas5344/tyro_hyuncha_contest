package com.team.hyuncha.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** sp는 시스템 글자 크기와 앱 설정을 함께 따른다. */
val Typography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 44.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 36.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 26.sp, lineHeight = 38.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 24.sp, lineHeight = 34.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 22.sp, lineHeight = 32.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 22.sp, lineHeight = 32.sp),
)

/** 앱 선택 배율. 시스템 fontScale은 따로 그대로 유지한다. */
val LocalAppTextScale = staticCompositionLocalOf { 1f }

private fun TextStyle.scaled(scale: Float) = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)

fun scaledTypography(scale: Float): Typography = Typography.run {
    copy(
        displayLarge = displayLarge.scaled(scale), displayMedium = displayMedium.scaled(scale), displaySmall = displaySmall.scaled(scale),
        headlineLarge = headlineLarge.scaled(scale), headlineMedium = headlineMedium.scaled(scale), headlineSmall = headlineSmall.scaled(scale),
        titleLarge = titleLarge.scaled(scale), titleMedium = titleMedium.scaled(scale), titleSmall = titleSmall.scaled(scale),
        bodyLarge = bodyLarge.scaled(scale), bodyMedium = bodyMedium.scaled(scale), bodySmall = bodySmall.scaled(scale),
        labelLarge = labelLarge.scaled(scale), labelMedium = labelMedium.scaled(scale), labelSmall = labelSmall.scaled(scale),
    )
}

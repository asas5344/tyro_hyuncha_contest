package com.team.hyuncha.data

import android.content.Context
import com.team.hyuncha.input.SteeringUnit

enum class AppTextSize(val label: String, val multiplier: Float) {
    STANDARD("기본", 1f), LARGE("크게", 1.2f), EXTRA_LARGE("아주 크게", 1.4f),
}

/**
 * 화면 환경과 실차 조향값 해석 설정만 저장한다. 주차 시뮬레이터와 차량 SDK 상태는 저장하지 않는다.
 * 조향값 기본값은 에뮬레이터에서 확인한 값(핸들 각도, 좌우 반전). 실제 차에서 다르면 설정 화면에서 바꿈.
 */
class UiPreferences(context: Context, name: String = "driving_mate_ui") {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    var carName: String
        get() = prefs.getString("car", CarPresets.GRANDEUR.name) ?: CarPresets.GRANDEUR.name
        set(value) { prefs.edit().putString("car", value).apply() }
    var darkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", true)
        set(value) { prefs.edit().putBoolean("dark_theme", value).apply() }
    var textSize: AppTextSize
        get() = AppTextSize.entries.firstOrNull { it.name == prefs.getString("text_size", null) } ?: AppTextSize.STANDARD
        set(value) { prefs.edit().putString("text_size", value.name).apply() }
    var steeringUnit: SteeringUnit
        get() = SteeringUnit.entries.firstOrNull { it.name == prefs.getString("steering_unit", null) } ?: SteeringUnit.STEERING_WHEEL
        set(value) { prefs.edit().putString("steering_unit", value.name).apply() }
    var invertSteering: Boolean
        get() = prefs.getBoolean("invert_steering", true)
        set(value) { prefs.edit().putBoolean("invert_steering", value).apply() }
}

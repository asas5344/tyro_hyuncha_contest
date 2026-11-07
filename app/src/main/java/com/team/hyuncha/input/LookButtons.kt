package com.team.hyuncha.input

import android.util.Log
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 운전석 화면에서 고개를 어디로 돌렸는지 */
enum class LookSide(val step: Int) {
    LEFT(-1),    // 왼쪽 사이드미러
    FRONT(0),
    RIGHT(1),    // 오른쪽 사이드미러
}

/**
 * 핸들의 이전 곡(◀◀)·다음 곡(▶▶) 버튼으로 고개 돌리기.
 * Pleos SDK에는 핸들 버튼 함수가 없어서, 차량용 안드로이드가 핸들 버튼을 바꿔 보내는
 * 일반 미디어 버튼 신호(KEYCODE_MEDIA_PREVIOUS / NEXT)를 받음.
 *
 * - 짧게 누름: 한 칸씩 돌림 (◀◀ = 왼쪽으로, ▶▶ = 오른쪽으로. 오른쪽 미러 → 앞 → 왼쪽 미러)
 * - 누르고 있다가 뗌: 누르는 동안만 미러를 보고 떼면 원래대로 (잠깐 확인)
 *
 * 운전석 화면이 떠 있을 때만(active) 버튼을 가져감. 다른 화면에서는 원래대로 음악 앱이 받음.
 */
object LookButtons {
    var side by mutableStateOf(LookSide.FRONT)
        private set
    var active = false
        set(value) {
            field = value
            if (!value) {
                side = LookSide.FRONT
                pressed = null
            }
        }

    private const val HOLD_MS = 500L   // 이보다 오래 누르면 "누르는 동안만 보기"
    private var downAt = 0L
    private var pressed: Int? = null   // 지금 누르고 있는 버튼 (짝이 없는 "뗌" 신호는 무시)
    private var before = LookSide.FRONT

    /** 음성 명령 등으로 바로 그쪽을 보게 함 */
    fun lookAt(target: LookSide) {
        side = target
        pressed = null
    }

    /** @return 버튼을 가져갔으면 true (다른 앱으로 안 넘어감) */
    fun onKey(event: KeyEvent): Boolean {
        val dir = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> -1
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> 1
            else -> return false
        }
        Log.d(TAG, "버튼 ${KeyEvent.keyCodeToString(event.keyCode)} action=${event.action} repeat=${event.repeatCount} active=$active")
        if (!active) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                pressed = event.keyCode
                downAt = event.eventTime
                before = side
                side = LookSide.entries.firstOrNull { it.step == (side.step + dir).coerceIn(-1, 1) } ?: side
            }
            KeyEvent.ACTION_UP -> if (pressed == event.keyCode) {
                pressed = null
                if (event.eventTime - downAt >= HOLD_MS) side = before
            }
        }
        return true
    }

    private const val TAG = "LookButtons"
}

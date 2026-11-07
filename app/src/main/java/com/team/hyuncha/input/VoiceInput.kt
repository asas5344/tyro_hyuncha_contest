package com.team.hyuncha.input

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Pleos SpeechToText SDK로 음성 명령 받기 (SDK 3.3.1, 2026-11-07 aar의 클래스로 함수 확인).
 * - 차 안 엔진(ON_DEVICE): 인터넷·앱 등록 없이 동작
 * - 마이크 버튼을 누르면 한 번 듣고, 말이 끝나면(SDK가 끝을 감지) 결과를 onCommand로 알려줌
 * - 에뮬레이터 확인(2026-11-07): 아무 말이 없으면 SDK가 끝없이 들음 → LISTEN_TIMEOUT_MS 뒤 앱이 멈춤.
 *   stop()을 불러도 onEndedRecognition이 오지 않음 → 멈출 때 상태는 앱이 직접 정리.
 * SDK 콜백이 어느 스레드에서 오는지 문서에 없어서 화면 상태는 항상 메인 스레드에서 바꿈.
 */
class VoiceInput(context: Context) {

    enum class State { PREPARING, READY, LISTENING, UNAVAILABLE }

    var state by mutableStateOf(State.PREPARING); private set
    /** 지금까지 들린 말 (말하는 중에도 계속 바뀜) */
    var heard by mutableStateOf(""); private set
    /** 쓸 수 없을 때 이유 (SDK 오류 이름) */
    var error by mutableStateOf<String?>(null); private set

    /** 말이 끝났을 때: 알아들은 명령(못 알아들으면 null)과 들린 말 */
    var onCommand: ((VoiceCommand?, String) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private var gotFinal = false
    private val timeout = Runnable { if (state == State.LISTENING) cancel() }

    private fun post(block: () -> Unit) { main.post(block) }

    /** 한 번의 듣기를 끝냄: 결과를 한 번만 알리고 버튼을 다시 누를 수 있게 함 */
    private fun finish(cmd: VoiceCommand?, said: String) {
        main.removeCallbacks(timeout)
        if (state == State.LISTENING) state = State.READY
        if (gotFinal) return
        gotFinal = true
        onCommand?.invoke(cmd, said)
    }

    /** 듣기를 멈춤 (SDK가 끝 신호를 안 보내서 직접 마무리) */
    private fun cancel() {
        stt.stop()
        finish(null, heard)
    }

    private val listener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = post { state = State.LISTENING }
        override fun onUpdated(stt: String, completed: Boolean) = post {
            Log.d(TAG, "들림: \"$stt\" 완료=$completed")
            heard = stt
            if (completed) finish(VoiceCommands.parse(stt), stt)
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() = post {
            Log.d(TAG, "인식 끝")
            // 아무 말도 안 했거나 최종 결과 없이 끝나면 못 알아들은 것으로 알려줌
            finish(null, heard)
        }
        override fun onError(errorType: ErrorType) = post {
            Log.w(TAG, "오류: $errorType")
            error = errorType.name
            finish(null, heard)
        }
    }

    init {
        stt.initialize(object : OnInitializeListener {
            override fun onSuccess() = post {
                stt.addListener(listener)
                stt.setLocale(Locale.KOREA)
                state = State.READY
                Log.d(TAG, "준비됨")
            }
            override fun onFailure(errorType: ErrorType) = post {
                Log.w(TAG, "초기화 실패: $errorType")
                error = errorType.name
                state = State.UNAVAILABLE
            }
        })
    }

    /** 한 번 듣기 시작. 듣는 중에 다시 누르면 그만 듣기 */
    fun toggle() {
        when (state) {
            State.READY -> {
                heard = ""
                error = null
                gotFinal = false
                state = State.LISTENING
                stt.request()
                main.postDelayed(timeout, LISTEN_TIMEOUT_MS)
            }
            State.LISTENING -> cancel()
            else -> Unit
        }
    }

    fun release() {
        main.removeCallbacks(timeout)
        if (state == State.LISTENING) stt.stop()
        onCommand = null
        stt.removeListener(listener)
        stt.release()
    }

    private companion object {
        const val TAG = "VoiceInput"
        const val LISTEN_TIMEOUT_MS = 8_000L   // 이 시간 안에 명령이 안 들리면 그만 들음
    }
}

/**
 * 핸들의 음성 인식 버튼(KEYCODE_VOICE_ASSIST). 주차 화면이 떠 있을 때만 가져가서 음성 명령을 시작함.
 * 다른 화면에서는 원래대로 차의 음성 비서가 받음.
 */
object VoiceKey {
    var onPress: (() -> Unit)? = null

    fun onKey(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOICE_ASSIST) return false
        val handler = onPress ?: return false
        if (event.action == KeyEvent.ACTION_UP) handler()
        return true
    }
}

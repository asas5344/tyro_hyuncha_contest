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
 *   실제 마이크로 말하면 onEndedRecognition이 최종 onUpdated보다 먼저 오기도 함 → 끝날 때 들린 말로 판단.
 * 명령을 실행하는 경우: SDK가 "끝"을 알렸을 때, 8초 타임아웃으로 끊었을 때. 오류·버튼으로 취소했을 때는 실행하지 않음.
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
    private var gotFinal = true          // 지금 듣기의 결과를 이미 알렸는지 (듣는 중이 아닐 때도 true)
    private var released = false
    /**
     * 우리가 stop()으로 끊은 직후에는 그 듣기의 늦은 콜백이 뒤따라올 수 있음.
     * 바로 다시 듣기 시작했을 때 그 콜백이 새 듣기의 결과로 섞이지 않도록,
     * 새 듣기가 실제로 시작됐다는 신호(onStartedRecognition)가 올 때까지 결과를 버림.
     */
    private var dropUntilStarted = false
    private val stopDropping = Runnable { dropUntilStarted = false }
    // 8초가 지나면 그만 들음. 말이 길어져서 끊긴 것일 수 있으니 그때까지 들린 말은 명령으로 해석함
    private val timeout = Runnable {
        if (state == State.LISTENING) { stopEngine(); finish(VoiceCommands.parse(heard), heard) }
    }

    private fun post(block: () -> Unit) { main.post(block) }

    /** 한 번의 듣기를 끝냄: 결과를 한 번만 알리고 버튼을 다시 누를 수 있게 함 */
    private fun finish(cmd: VoiceCommand?, said: String) {
        main.removeCallbacks(timeout)
        if (state == State.LISTENING) state = State.READY
        if (gotFinal) return
        gotFinal = true
        onCommand?.invoke(cmd, said)
    }

    /** SDK 듣기를 멈춤. SDK가 끝 신호를 안 보내므로 마무리(finish)는 부르는 쪽이 직접 함 */
    private fun stopEngine() {
        stt.stop()
        dropUntilStarted = true
    }

    /** 사용자가 버튼을 다시 눌러 그만 들음: 명령은 실행하지 않음 */
    private fun cancel() {
        stopEngine()
        finish(null, heard)
    }

    /** 지금 듣기의 콜백으로 받아들일지 (끝난 듣기·끊은 듣기의 늦은 콜백은 버림) */
    private fun accepting() = !released && !gotFinal && !dropUntilStarted

    private val listener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = post {
            Log.d(TAG, "인식 시작")
            main.removeCallbacks(stopDropping)
            dropUntilStarted = false
        }
        override fun onUpdated(stt: String, completed: Boolean) = post {
            Log.d(TAG, "들림: \"$stt\" 완료=$completed")
            if (!accepting()) return@post
            heard = stt
            if (completed) finish(VoiceCommands.parse(stt), stt)
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() = post {
            Log.d(TAG, "인식 끝")
            if (!accepting()) return@post
            // 에뮬레이터 확인: "인식 끝"이 최종 결과(completed=true)보다 먼저 오기도 함 → 그때까지 들린 말로 판단
            // (아무 말도 없었으면 heard가 비어 있어서 못 알아들은 것으로 알려줌)
            finish(VoiceCommands.parse(heard), heard)
        }
        override fun onError(errorType: ErrorType) = post {
            Log.w(TAG, "오류: $errorType")
            if (released) return@post
            error = errorType.name
            // 오류로 끊긴 듣기는 믿을 수 없으므로, 일부만 들린 말로 명령을 실행하지 않음 ("다시 말해 주세요"로 안내)
            if (!gotFinal) finish(null, "")
        }
    }

    init {
        stt.initialize(object : OnInitializeListener {
            override fun onSuccess() = post {
                if (released) return@post   // 준비가 끝나기 전에 화면을 떠났으면 해제된 SDK를 건드리지 않음
                stt.addListener(listener)
                stt.setLocale(Locale.KOREA)
                state = State.READY
                Log.d(TAG, "준비됨")
            }
            override fun onFailure(errorType: ErrorType) = post {
                if (released) return@post
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
                // 시작 신호는 아무 말도 없으면 오지 않음 (2026-11-11 에뮬레이터 확인) → 잠시 뒤에는 받기 시작
                main.removeCallbacks(stopDropping)
                if (dropUntilStarted) main.postDelayed(stopDropping, DROP_LATE_CALLBACKS_MS)
            }
            State.LISTENING -> cancel()
            else -> Unit
        }
    }

    fun release() {
        released = true
        main.removeCallbacks(timeout)
        main.removeCallbacks(stopDropping)
        if (state == State.LISTENING) stt.stop()
        onCommand = null
        stt.removeListener(listener)
        stt.release()
    }

    private companion object {
        const val TAG = "VoiceInput"
        const val LISTEN_TIMEOUT_MS = 8_000L   // 이 시간 안에 명령이 안 들리면 그만 들음
        const val DROP_LATE_CALLBACKS_MS = 1_000L   // 끊은 직후 다시 들을 때, 이전 듣기의 늦은 콜백을 버리는 최대 시간
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

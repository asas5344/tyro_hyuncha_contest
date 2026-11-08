package com.team.hyuncha.ui

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.team.hyuncha.input.VoiceCommand
import com.team.hyuncha.input.VoiceCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 실제 Pleos 차 안 음성 인식 엔진에 녹음 파일을 들려주고 명령으로 알아듣는지 확인.
 * 녹음 파일(androidTest/assets/voice_*.pcm)은 macOS say(유나 목소리)로 만든 16kHz·16bit·모노 PCM.
 * 마이크 대신 sendAudio()로 넣음 (ON_DEVICE 모드에서 공식 지원).
 */
class VoiceRecognitionTest {

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets get() = InstrumentationRegistry.getInstrumentation().context.assets

    private fun recognize(asset: String): String {
        val stt = SpeechToText(app, Mode.ON_DEVICE)
        val ready = CountDownLatch(1)
        val done = CountDownLatch(1)
        var text = ""
        var error: ErrorType? = null
        val listener = object : ResultListener {
            override fun onReady() = Unit
            override fun onStartedRecognition() = Unit
            override fun onUpdated(stt: String, completed: Boolean) {
                text = stt
                if (completed) done.countDown()
            }
            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onEndedRecognition() = done.countDown()
            override fun onError(errorType: ErrorType) { error = errorType; done.countDown() }
        }
        stt.initialize(object : OnInitializeListener {
            override fun onSuccess() = ready.countDown()
            override fun onFailure(errorType: ErrorType) { error = errorType; ready.countDown() }
        })
        assertTrue("초기화 시간 초과", ready.await(10, TimeUnit.SECONDS))
        stt.addListener(listener)
        stt.setLocale(Locale.KOREA)
        // 실제 말하는 속도로 100ms(3,200바이트)씩 보냄
        val pcm = assets.open(asset).use { it.readBytes() }
        pcm.asList().chunked(3_200).forEach { stt.sendAudio(it.toByteArray()); Thread.sleep(100) }
        stt.completeAudioSend()
        done.await(15, TimeUnit.SECONDS)
        stt.removeListener(listener)
        stt.release()
        Log.d("VoiceRecognitionTest", "$asset → \"$text\" (오류: $error)")
        return text
    }

    @Test fun 녹음한_명령을_알아듣는다() {
        val cases = listOf(
            "voice_reverse.pcm" to VoiceCommand.REVERSE,       // "후진해 줘"
            "voice_left_mirror.pcm" to VoiceCommand.LOOK_LEFT,
            "voice_restart.pcm" to VoiceCommand.RESTART,
            "voice_top_view.pcm" to VoiceCommand.TOP_VIEW,     // "위에서 보기"
            "voice_forward.pcm" to VoiceCommand.FORWARD,       // "앞으로 갈게"
            "voice_look_front.pcm" to VoiceCommand.LOOK_FRONT, // "앞을 보여 줘"
            "voice_close.pcm" to VoiceCommand.CLOSE,           // "그만 보기"
            "voice_gear_r.pcm" to VoiceCommand.REVERSE,        // "기어 R로 바꿔줘" → "기아 r로 바꿔 줘"
            "voice_gear_p.pcm" to VoiceCommand.PARK_INFO,      // "P로 바꿔줘"
            "voice_toggle.pcm" to VoiceCommand.TOGGLE_DIRECTION, // "방향 바꿔 줘"
        )
        val results = cases.map { (asset, expected) -> Triple(asset, expected, recognize(asset)) }
        results.forEach { (asset, expected, text) ->
            assertEquals("$asset 인식 결과 \"$text\"", expected, VoiceCommands.parse(text))
        }
    }
}


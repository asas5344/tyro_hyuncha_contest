package com.team.hyuncha.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * 하드 모드 클락션 소리. 소리 파일 없이 자동차 경적처럼 두 음(약 420Hz·520Hz)을 섞어 직접 만듦.
 * 앱이 낼 수 있는 가장 큰 크기(트랙 볼륨 최대)로 재생. 실제 크기는 차의 음량 설정을 따름.
 */
class HornPlayer {
    private val sampleRate = 44_100
    private val pcm: ShortArray = buildHorn(0.7)
    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
        .setAudioFormat(AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build())
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(pcm.size * 2)
        .build()
        .also {
            it.write(pcm, 0, pcm.size)
            it.setVolume(AudioTrack.getMaxVolume())
        }

    /** 한 번 "빵—" */
    fun honk() {
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    fun release() = runCatching { track.release() }

    /** 두 음을 섞고 고조파를 조금 넣어 거친 경적 느낌. 앞뒤 20ms는 부드럽게 (딸깍 소리 방지) */
    private fun buildHorn(seconds: Double): ShortArray {
        val n = (sampleRate * seconds).toInt()
        val fade = (sampleRate * 0.02).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            var v = 0.0
            for (f in listOf(420.0, 520.0)) {
                v += sin(2 * PI * f * t) + 0.45 * sin(2 * PI * f * 2 * t) + 0.25 * sin(2 * PI * f * 3 * t)
            }
            val env = min(1.0, min(i, n - 1 - i).toDouble() / fade)
            (v / 3.4 * env * 0.95 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}

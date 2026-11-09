package com.team.hyuncha.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh

internal const val HORN_DURATION_MS = 1_600

/**
 * 하드 모드 클락션 소리. 낮은 두 음과 강한 고조파를 섞어 길게 울리는 자동차 경적을 만든다.
 * 앱이 낼 수 있는 가장 큰 크기(트랙 볼륨 최대)로 재생. 실제 크기는 차의 음량 설정을 따름.
 */
class HornPlayer {
    private val sampleRate = 44_100
    private val pcm: ShortArray = buildHorn()
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

    private fun buildHorn(): ShortArray {
        val sampleCount = sampleRate * HORN_DURATION_MS / 1_000
        val attackSamples = sampleRate * 12 / 1_000
        val releaseSamples = sampleRate * 90 / 1_000
        return ShortArray(sampleCount) { index ->
            val time = index.toDouble() / sampleRate
            val signal = hornTone(392.0, time) + hornTone(494.0, time)
            val envelope = minOf(1.0, index.toDouble() / attackSamples, (sampleCount - 1 - index).toDouble() / releaseSamples)
            // 파형의 꼭대기를 부드럽게 눌러 음량을 키우지 않고도 더 거친 소리를 낸다.
            (tanh(1.15 * signal) * envelope * 0.95 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun hornTone(frequency: Double, time: Double): Double {
        val phase = 2 * PI * frequency * time
        return sin(phase) + 0.65 * sin(2 * phase) + 0.40 * sin(3 * phase) + 0.18 * sin(4 * phase)
    }
}

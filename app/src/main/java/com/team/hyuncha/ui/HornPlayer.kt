package com.team.hyuncha.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.random.Random

/** 길게 한 번 울리는 경적의 길이. 화면 가장자리 강조(ParkingScreen)도 이 시간만큼 보여 줌 */
internal const val HORN_DURATION_MS = 850

/** 하드 모드 경적. 참고 음원의 기본음과 배음을 합성해 차량의 미디어 음량으로 재생한다. */
class HornPlayer {
    private val sampleRate = 44_100
    private val longTrack: AudioTrack
    private val doubleTrack: AudioTrack

    init {
        val waveform = DoubleArray(sampleRate * HORN_DURATION_MS / 1_000)
        // 참고 음원의 짧은 경적에서 측정한 위상(rad)과 상대 진폭. 경적이 선명하게 들리도록 높은 배음을 강조했다.
        addHarmonics(waveform, 335.3, listOf(
            0.32 to 0.0, 1.00 to 1.48, 0.09 to -0.41, 0.90 to 1.89,
            0.42 to 2.38, 0.47 to -0.34, 0.32 to 2.02, 0.07 to 1.10, 0.16 to -2.10,
        ))
        addHarmonics(waveform, 409.1, listOf(
            0.35 to 0.0, 0.56 to 1.64, 0.80 to 0.98, 0.91 to -0.33, 1.45 to -2.84, 0.51 to -0.03,
        ))

        val gain = 0.95 * Short.MAX_VALUE / waveform.maxOf { abs(it) }
        val long = pulse(waveform, waveform.size, gain)
        val short = pulse(waveform, sampleRate * 170 / 1_000, gain)
        val gap = ShortArray(sampleRate * 110 / 1_000)
        longTrack = createTrack(long)
        doubleTrack = createTrack(short + gap + short)
    }

    fun honk() {
        val track = if (Random.nextBoolean()) longTrack else doubleTrack
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    fun release() {
        runCatching { longTrack.release() }
        runCatching { doubleTrack.release() }
    }

    private fun createTrack(pcm: ShortArray): AudioTrack = AudioTrack.Builder()
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

    private fun addHarmonics(waveform: DoubleArray, baseHz: Double, harmonics: List<Pair<Double, Double>>) {
        harmonics.forEachIndexed { index, (amplitude, phase) ->
            val phaseStep = 2 * PI * baseHz * (index + 1) / sampleRate
            for (i in waveform.indices) {
                waveform[i] += amplitude * cos(phaseStep * i + phase)
            }
        }
    }

    private fun pulse(waveform: DoubleArray, samples: Int, gain: Double): ShortArray {
        val attack = sampleRate * 3 / 1_000.0
        val release = sampleRate * 30 / 1_000.0
        return ShortArray(samples) { i ->
            val envelope = min(1.0, min(i / attack, (samples - 1 - i) / release))
            (waveform[i] * envelope * gain).toInt().toShort()
        }
    }
}

package com.team.hyuncha.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.team.hyuncha.R

/** 파일 길이를 못 읽었을 때 화면 가장자리 강조를 보여 줄 시간 */
private const val FALLBACK_DURATION_MS = 1_500

/**
 * 하드 모드 경적. res/raw의 녹음 파일을 재생한다.
 * 첫 번째 경적(4초)은 "빵빵"(horn_double), 두 번째·세 번째는 길게 "빠아앙"(horn_long).
 * 앱이 낼 수 있는 가장 큰 크기로 재생. 실제 크기는 차의 음량 설정을 따름.
 */
class HornPlayer(context: Context) {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var activeRequest: AudioFocusRequest? = null
    private var playbackGeneration = 0
    private var streamId = 0
    private val finishPlayback = Runnable { stop() }
    private val pool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(attributes)
        .build()

    // SoundPool은 미리 불러 둬야 정해진 시각에 늦지 않게 울림 (첫 경적까지 4초라 충분)
    private val double = Horn(context, R.raw.horn_double)
    private val long = Horn(context, R.raw.horn_long)

    /**
     * @param order 이번 판에서 몇 번째 경적인지 (0부터)
     * @return 재생 길이(ms). 재생을 시작하지 못하면 0
     */
    fun honk(order: Int): Int {
        stop()
        val manager = audioManager ?: return 0
        val generation = playbackGeneration
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (change < 0 && generation == playbackGeneration) stop()
            }, main)
            .build()
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return 0
        activeRequest = request

        val horn = if (order == 0) double else long
        streamId = runCatching { pool.play(horn.soundId, 1f, 1f, 1, 0, 1f) }.getOrDefault(0)
        if (streamId == 0) {
            stop()
            return 0
        }
        main.postDelayed(finishPlayback, horn.durationMs.toLong())
        return horn.durationMs
    }

    fun stop() {
        playbackGeneration++
        main.removeCallbacks(finishPlayback)
        if (streamId != 0) {
            pool.stop(streamId)
            streamId = 0
        }
        val request = activeRequest
        activeRequest = null
        if (request != null) audioManager?.abandonAudioFocusRequest(request)
    }

    fun release() {
        stop()
        pool.release()
    }

    private inner class Horn(context: Context, resId: Int) {
        val soundId = pool.load(context, resId, 1)
        val durationMs = durationOf(context, resId)
    }

    private fun durationOf(context: Context, resId: Int): Int = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            context.resources.openRawResourceFd(resId).use {
                retriever.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull()
        } finally {
            retriever.release()
        }
    }.getOrNull() ?: FALLBACK_DURATION_MS
}

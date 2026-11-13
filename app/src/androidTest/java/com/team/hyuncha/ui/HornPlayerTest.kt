package com.team.hyuncha.ui

import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.team.hyuncha.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HornPlayerTest {
    @Test
    fun releasesFocusAtClipEndAndWhenStopped() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var horn: HornPlayer
            scenario.onActivity { horn = HornPlayer(it) }
            try {
                playWhenLoaded(scenario, horn)
                assertTrue("경적 재생 중 포커스가 있어야 함", hasGameAudioFocus())
                waitForFocusRelease()

                playWhenLoaded(scenario, horn)
                assertTrue("두 번째 재생 중 포커스가 있어야 함", hasGameAudioFocus())
                scenario.onActivity { horn.stop() }
                waitForFocusRelease()
            } finally {
                scenario.onActivity { horn.release() }
            }
        }
    }

    private fun playWhenLoaded(scenario: ActivityScenario<MainActivity>, horn: HornPlayer) {
        repeat(20) {
            var duration = 0
            scenario.onActivity { duration = horn.honk(1) }
            if (duration > 0) return
            Thread.sleep(100)
        }
        error("경적 재생을 시작하지 못함")
    }

    private fun waitForFocusRelease() {
        repeat(40) {
            if (!hasGameAudioFocus()) return
            Thread.sleep(100)
        }
        error("경적 오디오 포커스를 반환하지 않음")
    }

    private fun hasGameAudioFocus(): Boolean {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val descriptor = automation.executeShellCommand("dumpsys audio")
        val dump = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        val focusOwners = dump.substringAfter("Audio Focus stack entries", "")
            .substringBefore("Events log: focus commands")
        assertTrue("오디오 포커스 목록을 읽을 수 있어야 함", focusOwners.isNotEmpty())
        return focusOwners.lineSequence().any { line ->
            line.contains("com.team.hyuncha") && line.contains("USAGE_GAME")
        }
    }
}

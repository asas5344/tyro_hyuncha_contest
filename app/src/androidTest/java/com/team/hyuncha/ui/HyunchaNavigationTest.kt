package com.team.hyuncha.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.team.hyuncha.data.AppTextSize
import com.team.hyuncha.data.UiPreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 실제 화면 전환과 설정 저장을 검증하며 사용자의 설정 저장소와 분리한다. */
class HyunchaNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferenceName = "driving_mate_ui_instrumented_test"
    private lateinit var store: UiPreferences

    @Before fun prepare() {
        context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit().clear().commit()
        store = UiPreferences(context, preferenceName)
        // 기존 시뮬레이터의 연속 프레임 루프를 테스트가 명시적으로 진행한다.
        compose.mainClock.autoAdvance = false
    }
    @After fun clean() {
        context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun settle() { compose.mainClock.advanceTimeBy(500); compose.waitForIdle() }
    private fun capture(tag: String, filename: String) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        FileOutputStream(File(context.cacheDir, filename)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun openApp() { compose.setContent { HyunchaApp(vehicle = null, preferences = store) }; settle() }

    @Test fun startOpensExistingPerpendicularPracticeAndReturnsHome() {
        openApp()
        capture("home_screen", "home-standard.png")
        compose.onNodeWithTag("start_practice").performScrollTo().performClick(); settle()
        compose.onNodeWithTag("parking_screen").assertIsDisplayed()
        compose.onNodeWithText("직각주차 · 그랜저").assertIsDisplayed()
        capture("parking_screen", "parking-standard.png")
        compose.onNodeWithTag("back_home").performClick(); settle()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test fun parallelParkingCanBeSelectedAndStarted() {
        openApp()
        compose.onNodeWithTag("mode_PARALLEL").performScrollTo().performClick(); settle()
        compose.onNodeWithTag("start_practice").performScrollTo().performClick(); settle()
        compose.onNodeWithText("평행주차 · 그랜저").assertIsDisplayed()
        capture("parking_screen", "parking-parallel.png")
        // 홈으로 돌아와도 고른 주차 방식이 유지됨
        compose.onNodeWithTag("back_home").performClick(); settle()
        compose.onNodeWithTag("start_practice").performScrollTo().performClick(); settle()
        compose.onNodeWithText("평행주차 · 그랜저").assertIsDisplayed()
    }

    @Test fun diagonalParkingCanBeSelectedAndStarted() {
        openApp()
        compose.onNodeWithTag("mode_DIAGONAL").performScrollTo().performClick(); settle()
        compose.onNodeWithTag("start_practice").performScrollTo().performClick(); settle()
        compose.onNodeWithText("사선주차 · 그랜저").assertIsDisplayed()
        capture("parking_screen", "parking-diagonal.png")
    }

    @Test fun selectedCarIsPassedIntoExistingSimulator() {
        openApp()
        compose.onNodeWithTag("choose_car").performScrollTo().performClick(); settle()
        compose.onNodeWithTag("select_car_아반떼").performScrollTo().performClick(); settle()
        compose.onNodeWithTag("home_car_name", useUnmergedTree = true).assertTextEquals("아반떼")
        compose.onNodeWithTag("start_practice").performScrollTo().performClick(); settle()
        compose.onNodeWithText("직각주차 · 아반떼").assertIsDisplayed()
        compose.onNodeWithText("차종").assertDoesNotExist()
        compose.onNodeWithText("그랜저 (세단)").assertDoesNotExist()
        compose.onNodeWithTag("back_home").performClick(); settle()
        compose.onNodeWithTag("home_car_name", useUnmergedTree = true).assertTextEquals("아반떼")
        assertEquals("아반떼", UiPreferences(context, preferenceName).carName)
    }

    @Test fun extraLargeTextIsStoredAndHomeRemainsUsable() {
        openApp()
        val originalTextHeight = compose.onNodeWithTag("home_car_name", useUnmergedTree = true).getUnclippedBoundsInRoot().height.value
        compose.onNodeWithTag("open_settings").performClick(); settle()
        compose.onNodeWithTag("font_EXTRA_LARGE").performScrollTo().performClick(); settle()
        assertEquals(AppTextSize.EXTRA_LARGE, UiPreferences(context, preferenceName).textSize)
        capture("settings_screen", "settings-extra-large.png")
        compose.onNodeWithTag("settings_back").performScrollTo().performClick(); settle()
        val enlargedTextHeight = compose.onNodeWithTag("home_car_name", useUnmergedTree = true).getUnclippedBoundsInRoot().height.value
        assertTrue("실제 글자 영역이 커져야 함: $originalTextHeight -> $enlargedTextHeight", enlargedTextHeight > originalTextHeight * 1.25f)
        capture("home_screen", "home-extra-large.png")
        compose.onNodeWithTag("start_practice").performScrollTo().assertIsDisplayed().performClick(); settle()
        compose.onNodeWithTag("parking_screen").assertIsDisplayed()
        compose.onNodeWithTag("back_home").performClick(); settle()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test fun storedTextSizeAndThemeAreRestoredAtLaunch() {
        store.textSize = AppTextSize.LARGE
        store.darkTheme = false
        openApp()
        compose.onNodeWithTag("open_settings").performClick(); settle()
        assertEquals(AppTextSize.LARGE, UiPreferences(context, preferenceName).textSize)
        assertFalse(UiPreferences(context, preferenceName).darkTheme)
        compose.onNodeWithTag("font_LARGE").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("theme_light").performScrollTo().assertIsSelected()
    }
}

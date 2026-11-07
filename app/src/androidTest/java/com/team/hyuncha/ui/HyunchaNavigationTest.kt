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
import com.team.hyuncha.data.ScoreRecord
import com.team.hyuncha.data.ScoreRecords
import com.team.hyuncha.data.UiPreferences
import com.team.hyuncha.sim.ParkingMode
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

    @Test fun rankingShowsRecordsByModeInScoreOrder() {
        val recordsName = "driving_mate_ranking_instrumented_test"
        context.getSharedPreferences(recordsName, Context.MODE_PRIVATE).edit().clear().commit()
        val records = ScoreRecords(context, recordsName)
        records.add(ScoreRecord("아빠", ParkingMode.PERPENDICULAR, "그랜저", 950, "S", 1_700_000_000_000))
        records.add(ScoreRecord("엄마", ParkingMode.PERPENDICULAR, "아반떼", 981, "S", 1_700_000_100_000))
        records.add(ScoreRecord("나", ParkingMode.PERPENDICULAR, "투싼", 742, "B", 1_700_000_200_000))
        compose.setContent { HyunchaApp(vehicle = null, preferences = store, scoreRecords = records) }; settle()
        compose.onNodeWithTag("open_ranking").performClick(); settle()
        compose.onNodeWithTag("ranking_screen").assertIsDisplayed()
        // 점수 높은 순서
        compose.onNodeWithTag("rank_row_1").assert(hasAnyDescendant(hasText("엄마")))
        compose.onNodeWithTag("rank_row_2").assert(hasAnyDescendant(hasText("아빠")))
        compose.onNodeWithTag("rank_row_3").assert(hasAnyDescendant(hasText("나")))
        capture("ranking_screen", "ranking-perpendicular.png")
        // 주차 방식마다 따로: 평행 주차는 기록 없음
        compose.onNodeWithTag("ranking_mode_PARALLEL").performClick(); settle()
        compose.onNodeWithTag("ranking_empty").assertIsDisplayed()
        compose.onNodeWithTag("ranking_back").performClick(); settle()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        context.getSharedPreferences(recordsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun startOpensExistingPerpendicularPracticeAndReturnsHome() {
        openApp()
        capture("home_screen", "home-standard.png")
        compose.onNodeWithTag("start_practice").performScrollTo().performClick(); settle()
        compose.onNodeWithTag("parking_screen").assertIsDisplayed()
        compose.onNodeWithText("T자 주차 · 그랜저").assertIsDisplayed()
        capture("parking_screen", "parking-standard.png")
        compose.onNodeWithTag("back_home").performClick(); settle()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test fun parallelParkingCanBeSelectedAndStarted() {
        openApp()
        compose.onNodeWithTag("mode_PARALLEL").performScrollTo().performClick(); settle()
        compose.onNodeWithTag("start_practice").performScrollTo().performClick(); settle()
        compose.onNodeWithText("평행 주차 · 그랜저").assertIsDisplayed()
        capture("parking_screen", "parking-parallel.png")
        // 홈으로 돌아와도 고른 주차 방식이 유지됨
        compose.onNodeWithTag("back_home").performClick(); settle()
        compose.onNodeWithTag("start_practice").performScrollTo().performClick(); settle()
        compose.onNodeWithText("평행 주차 · 그랜저").assertIsDisplayed()
    }

    @Test fun diagonalParkingCanBeSelectedAndStarted() {
        openApp()
        compose.onNodeWithTag("mode_DIAGONAL").performScrollTo().performClick(); settle()
        compose.onNodeWithTag("start_practice").performScrollTo().performClick(); settle()
        compose.onNodeWithText("사선 주차 · 그랜저").assertIsDisplayed()
        capture("parking_screen", "parking-diagonal.png")
    }

    @Test fun steeringSettingsAreStored() {
        // 설정 화면 아래쪽으로 스크롤해야 하는데, 스크롤은 애니메이션이라 시계가 멈춰 있으면 끝나지 않음.
        // 이 테스트는 주차 화면(계속 움직이는 화면)을 열지 않으므로 시계를 자동으로 진행시킴
        compose.mainClock.autoAdvance = true
        openApp()
        compose.onNodeWithTag("open_settings").performClick(); settle()
        compose.onNodeWithTag("steer_unit_FRONT_WHEEL").performScrollTo().performClick(); settle()
        compose.onNodeWithTag("steer_invert_off").performScrollTo().performClick(); settle()
        val saved = UiPreferences(context, preferenceName)
        assertEquals(com.team.hyuncha.input.SteeringUnit.FRONT_WHEEL, saved.steeringUnit)
        assertFalse(saved.invertSteering)
        // 내 차 카메라 설정도 저장됨
        compose.onNodeWithTag("camera_SURROUND").performScrollTo().performClick(); settle()
        assertEquals(com.team.hyuncha.data.CarCamera.SURROUND, UiPreferences(context, preferenceName).carCamera)
    }

    @Test fun parkingScreenStartsInDriverViewAndCanSwitchToTopView() {
        openApp()
        compose.onNodeWithTag("start_practice").performScrollTo().performClick(); settle()
        compose.onNodeWithText("탑뷰로 보기").assertIsDisplayed()
        compose.onNodeWithTag("toggle_view").performClick(); settle()
        compose.onNodeWithText("운전석으로 보기").assertIsDisplayed()
        capture("parking_screen", "parking-topview.png")
    }

    @Test fun selectedCarIsPassedIntoExistingSimulator() {
        openApp()
        compose.onNodeWithTag("choose_car").performScrollTo().performClick(); settle()
        compose.onNodeWithTag("select_car_아반떼").performScrollTo().performClick(); settle()
        compose.onNodeWithTag("home_car_name", useUnmergedTree = true).assertTextEquals("아반떼")
        compose.onNodeWithTag("start_practice").performScrollTo().performClick(); settle()
        compose.onNodeWithText("T자 주차 · 아반떼").assertIsDisplayed()
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

package com.team.hyuncha.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.input.Direction
import com.team.hyuncha.sim.ParkingSimulator
import com.team.hyuncha.sim.ParkingMaps

/** Exercise the actual home -> parking layout, including the control panel's viewport width. */
class DriverViewRegressionTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun capture(name: String) {
        val i=InstrumentationRegistry.getInstrumentation()
        val image=i.uiAutomation.takeScreenshot()
        val preview=Bitmap.createScaledBitmap(image,1280,720,true)
        val bytes=ByteArrayOutputStream().also { preview.compress(Bitmap.CompressFormat.JPEG,90,it) }.toByteArray()
        val chunks=Base64.encodeToString(bytes,Base64.NO_WRAP).chunked(3000)
        chunks.forEachIndexed { index,data -> Log.i("DriverShot","$name:$index:${chunks.size}:$data") }
        preview.recycle();image.recycle()
    }
    @Test fun actualParkingLayoutReturnsToForwardView() {
        compose.mainClock.autoAdvance=false
        compose.setContent { HyunchaApp(null) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("start_practice").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil(60_000) { compose.mainClock.advanceTimeBy(16);compose.onAllNodesWithTag("parking3d_ready").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle();Thread.sleep(3000);capture("driver-layout-front")
        compose.onNodeWithTag("look_left").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle();Thread.sleep(6000);capture("driver-layout-left")
        compose.onNodeWithTag("look_right").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle();Thread.sleep(6000);capture("driver-layout-right")
        compose.onNodeWithTag("look_front").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle();Thread.sleep(3000);capture("driver-layout-returned")
        compose.onNodeWithTag("toggle_3d").assertDoesNotExist()
        compose.onNodeWithText("기존 운전석").assertDoesNotExist()
    }
    @Test fun mirrorFramesWithoutAdjacentVehicleOcclusion() {
        compose.mainClock.autoAdvance=false
        val sim=ParkingSimulator(CarPresets.GRANDEUR,ParkingMaps.perpendicular().copy(parkedCars=emptyList()))
        compose.setContent {
            BoxWithConstraints {
                RealisticParkingView(sim,{0L},{Direction.FORWARD},{true},
                    Modifier.width(maxWidth*.74f).height(maxHeight*.90f))
            }
        }
        compose.waitUntil(60_000) { compose.mainClock.advanceTimeBy(16);compose.onAllNodesWithTag("parking3d_ready").fetchSemanticsNodes().isNotEmpty() }
        for((tag,name) in listOf("look_left" to "isolatedleft","look_right" to "isolatedright")) {
            compose.onNodeWithTag(tag).performClick();compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle();Thread.sleep(6000);capture("driver-layout-$name")
        }
    }
}

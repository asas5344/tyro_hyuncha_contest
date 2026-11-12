package com.team.hyuncha.ui

import ai.pleos.playground.vehicle.Vehicle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.team.hyuncha.data.AppTextSize
import com.team.hyuncha.data.CarCamera
import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.data.ScoreRecords
import com.team.hyuncha.data.UiPreferences
import com.team.hyuncha.input.SteeringUnit
import com.team.hyuncha.sim.HardSituation
import com.team.hyuncha.sim.ParkingMode
import com.team.hyuncha.ui.theme.HyunchaTheme
import com.team.hyuncha.ui.theme.LocalAppTextScale

@Composable
fun HyunchaApp(
    vehicle: Vehicle?,
    preferences: UiPreferences? = null,
    scoreRecords: ScoreRecords? = null,
    vehicleInitializationFailure: Exception? = null,
) {
    val context = LocalContext.current
    val store = remember(preferences, context) { preferences ?: UiPreferences(context) }
    var carName by rememberSaveable { mutableStateOf(store.carName) }
    var dark by rememberSaveable { mutableStateOf(store.darkTheme) }
    var textSizeName by rememberSaveable { mutableStateOf(store.textSize.name) }
    var steeringUnitName by rememberSaveable { mutableStateOf(store.steeringUnit.name) }
    var carCameraName by rememberSaveable { mutableStateOf(store.carCamera.name) }
    val carCamera = CarCamera.valueOf(carCameraName)
    var invertSteering by rememberSaveable { mutableStateOf(store.invertSteering) }
    var driverName by rememberSaveable { mutableStateOf(store.driverName) }
    val records = remember(scoreRecords, context) { scoreRecords ?: ScoreRecords(context) }
    val steeringUnit = SteeringUnit.valueOf(steeringUnitName)
    var screen by rememberSaveable { mutableStateOf("home") }
    var choosingCar by rememberSaveable { mutableStateOf(false) }
    var parkingModeName by rememberSaveable { mutableStateOf(ParkingMode.PERPENDICULAR.name) }
    val parkingMode = ParkingMode.valueOf(parkingModeName)
    // 하드 모드 (T자 주차만). hardSituationName: 확인용 상황 고르기, null이면 랜덤 (나중에 뺄 예정)
    var hardMode by rememberSaveable { mutableStateOf(false) }
    var hardSituationName by rememberSaveable { mutableStateOf<String?>(null) }
    val hardSituation = hardSituationName?.let { HardSituation.valueOf(it) }
    val textSize = AppTextSize.entries.firstOrNull { it.name == textSizeName } ?: AppTextSize.STANDARD
    val selectedCar = CarPresets.all.firstOrNull { it.name == carName } ?: CarPresets.GRANDEUR
    BackHandler(choosingCar || screen != "home") {
        if (choosingCar) choosingCar = false else screen = "home"
    }
    CompositionLocalProvider(LocalAppTextScale provides textSize.multiplier) {
        HyunchaTheme(
            darkTheme = dark, textScale = textSize.multiplier,
            pleosVisuals = screen == "home" || screen == "parking",
        ) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
                Surface(Modifier.fillMaxSize().padding(insets), color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground) {
                    when (screen) {
                        "parking" -> Column(Modifier.fillMaxSize().testTag("parking_screen")) {
                            AppButton("홈으로", onClick = { screen = "home" },
                                modifier = Modifier.padding(16.dp).testTag("back_home"), icon = "back")
                            ParkingScreen(vehicle, Modifier.weight(1f), initialCar = selectedCar, parkingMode = parkingMode,
                                steeringUnit = steeringUnit, invertSteering = invertSteering, carCamera = carCamera,
                                driverName = driverName, records = records,
                                hardMode = hardMode && parkingMode == ParkingMode.PERPENDICULAR, hardSituation = hardSituation,
                                vehicleInitializationFailure = vehicleInitializationFailure)
                        }
                        "settings" -> SettingsScreen(textSize, dark,
                            onTextSizeChange = { textSizeName = it.name; store.textSize = it },
                            onDarkThemeChange = { dark = it; store.darkTheme = it },
                            steeringUnit = steeringUnit, invertSteering = invertSteering,
                            onSteeringUnitChange = { steeringUnitName = it.name; store.steeringUnit = it },
                            onInvertSteeringChange = { invertSteering = it; store.invertSteering = it },
                            carCamera = carCamera,
                            onCarCameraChange = { carCameraName = it.name; store.carCamera = it },
                            driverName = driverName,
                            onDriverNameChange = { driverName = it; store.driverName = it },
                            onBack = { screen = "home" })
                        "ranking" -> RankingScreen(records, initialMode = parkingMode, onBack = { screen = "home" })
                        else -> HomeScreen(selectedCar, onChooseCar = { choosingCar = true },
                            onSettings = { screen = "settings" }, onStart = { screen = "parking" },
                            mode = parkingMode,
                            onModeChange = { parkingModeName = it.name; if (it != ParkingMode.PERPENDICULAR) hardMode = false },
                            hardMode = hardMode,
                            onHardModeChange = { on -> hardMode = on; if (on) parkingModeName = ParkingMode.PERPENDICULAR.name },
                            hardSituation = hardSituation,
                            onHardSituationChange = { hardSituationName = it?.name },
                            darkTheme = dark,
                            onRanking = { screen = "ranking" })
                    }
                    if (choosingCar) CarSelectionSheet(selectedCar, onSelect = {
                        carName = it.name; store.carName = it.name; choosingCar = false
                    }, onDismiss = { choosingCar = false })
                }
            }
        }
    }
}

package com.team.hyuncha

import ai.pleos.playground.vehicle.Vehicle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.team.hyuncha.ui.HyunchaApp

class MainActivity : ComponentActivity() {

    private lateinit var vehicle: Vehicle   // 화면이 살아있는 동안 사용

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vehicle = Vehicle(applicationContext)
        vehicle.initialize()  // 차량 서비스 연결 시작 (연결 전 요청은 SDK가 모아뒀다가 처리)

        enableEdgeToEdge()
        setContent { HyunchaApp(vehicle) }
    }

    // 화면이 완전히 끝날 때 연결 해제
    override fun onDestroy() {
        vehicle.release()
        super.onDestroy()
    }
}

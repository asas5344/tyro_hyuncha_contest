package com.team.hyuncha.input

import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.ErrorCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class VehicleConnectionFailureTest {
    @Test
    fun onlyReadableCapabilitiesAllowStartup() {
        for (signal in listOf(VehicleSignal.STEER, VehicleSignal.BRAKE)) {
            requireReadAccess(signal, AccessType.READ)
            requireReadAccess(signal, AccessType.READ_WRITE)
            for (access in listOf(AccessType.NONE, AccessType.WRITE)) {
                val failure = assertThrows(VehicleConnectionFailure::class.java) {
                    requireReadAccess(signal, access)
                }
                assertEquals("CAP-${signal.name}-NO_READ", failure.code)
                val feature = if (signal == VehicleSignal.STEER) "조향" else "브레이크"
                assertEquals("이 차량에서는 주차 연습에 필요한 ${feature} 정보를 사용할 수 없어요.", failure.message)
            }
        }
    }

    @Test
    fun sdkFailureKeepsItsCodeAndRequestStage() {
        val failure = assertThrows(VehicleConnectionFailure::class.java) {
            runBlocking {
                awaitVehicle<Boolean>(VehicleStage.CAP, VehicleSignal.BRAKE) { _, fail ->
                    fail(Exception(ErrorCode.ERROR_SECURITY_PERMISSION.name))
                }
            }
        }
        assertEquals("CAP-BRAKE-800001", failure.code)
        assertEquals("차량 정보를 읽을 권한이 없어요.", failure.message)
    }

    @Test
    fun securityExceptionWithoutSdkCodeStillIdentifiesPermissionFailure() {
        val failure = assertThrows(VehicleConnectionFailure::class.java) {
            runBlocking {
                awaitVehicle<Boolean>(VehicleStage.CAP, VehicleSignal.STEER) { _, _ ->
                    throw SecurityException("checkSteeringWheelAngleCapabilities need Permission")
                }
            }
        }
        assertEquals("CAP-STEER-NO_PERMISSION", failure.code)
        assertEquals("차량 정보를 읽을 권한이 없어요.", failure.message)
    }

    @Test
    fun noResponseTimesOutWithThePendingStage() {
        val failure = assertThrows(VehicleConnectionFailure::class.java) {
            runBlocking {
                awaitVehicle<Boolean>(VehicleStage.READ, VehicleSignal.GEAR, timeoutMillis = 20) { _, _ -> }
            }
        }
        assertEquals("READ-GEAR-TIMEOUT", failure.code)
        assertEquals("차량의 응답이 없어요.", failure.message)
    }

    @Test
    fun explicitNullResponseIsDistinctFromTimeout() {
        val value = runBlocking {
            awaitVehicle<Boolean?>(VehicleStage.READ, VehicleSignal.GEAR) { complete, _ -> complete(null) }
        }
        assertNull(value)
        val failure = VehicleConnectionFailure.missingValue(VehicleSignal.GEAR)
        assertEquals("READ-GEAR-NO_VALUE", failure.code)
        assertEquals("차량의 기어 상태를 확인할 수 없어요.", failure.message)
    }
}

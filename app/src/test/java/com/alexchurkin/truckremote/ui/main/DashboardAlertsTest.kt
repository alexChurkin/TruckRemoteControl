package com.alexchurkin.truckremote.ui.main

import com.alexchurkin.truckremote.data.controller.Dashboard
import com.alexchurkin.truckremote.data.controller.TruckWarning
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardAlertsTest {

    private val dashboard = Dashboard(
        speed = 20f,
        speedLimit = 0f,
        cruiseSpeed = 0f,
        gear = 8,
        engineRpm = 1200,
        engineRpmMax = 2500,
        fuelPercent = 12,
        isAts = false,
    )

    @Test
    fun `old server - low fuel is guessed by the level`() {
        assertEquals(listOf(DashboardAlert(AlertKind.Fuel, severe = false, 12)), dashboard.alerts())
        assertEquals(emptyList<DashboardAlert>(), dashboard.copy(fuelPercent = 40).alerts())
    }

    @Test
    fun `nothing is shown while everything is fine`() {
        val fine = dashboard.copy(warnings = emptySet(), wearPercent = 4, restStopMinutes = 300)
        assertEquals(emptyList<DashboardAlert>(), fine.alerts())
    }

    @Test
    fun `serious problems come first`() {
        val alerts = dashboard.copy(
            warnings = setOf(TruckWarning.Fuel, TruckWarning.AirPressure, TruckWarning.AirPressureEmergency),
            fuelRangeKm = 85,
            wearPercent = 23,
            restStopMinutes = 40,
        ).alerts()

        assertEquals(
            listOf(
                DashboardAlert(AlertKind.AirPressure, severe = true),
                DashboardAlert(AlertKind.Fuel, severe = false, 12, rangeKm = 85),
                DashboardAlert(AlertKind.Rest, severe = false, 40),
                DashboardAlert(AlertKind.Wear, severe = false, 23),
            ),
            alerts,
        )
    }
}

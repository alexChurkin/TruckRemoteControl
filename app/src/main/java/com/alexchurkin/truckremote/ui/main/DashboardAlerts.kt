package com.alexchurkin.truckremote.ui.main

import com.alexchurkin.truckremote.data.controller.Dashboard
import com.alexchurkin.truckremote.data.controller.TruckWarning

enum class AlertKind {
    AirPressure,
    OilPressure,
    WaterTemperature,
    Battery,
    AdBlue,
    Fuel,
    Wear,
    Rest,
}

// [value]: the fuel or wear percent, minutes until the rest; [rangeKm]: how far the fuel is enough for
data class DashboardAlert(val kind: AlertKind, val severe: Boolean, val value: Int = 0, val rangeKm: Int = 0)

private const val LOW_FUEL_PERCENT = 15
private const val WEAR_PERCENT = 10
private const val REST_SOON_MINUTES = 60

/**
 * What needs attention, the most important first. Nothing is shown while everything is fine.
 * Servers without the extended state don't send warnings: low fuel is guessed by the level then.
 */
fun Dashboard.alerts(): List<DashboardAlert> {
    val warnings = warnings
        ?: return listOfNotNull(
            DashboardAlert(AlertKind.Fuel, severe = false, fuelPercent).takeIf { fuelPercent in 0..LOW_FUEL_PERCENT },
        )
    return buildList {
        if (TruckWarning.AirPressureEmergency in warnings) {
            add(DashboardAlert(AlertKind.AirPressure, severe = true))
        } else if (TruckWarning.AirPressure in warnings) {
            add(DashboardAlert(AlertKind.AirPressure, severe = false))
        }
        if (TruckWarning.OilPressure in warnings) add(DashboardAlert(AlertKind.OilPressure, severe = true))
        if (TruckWarning.WaterTemperature in warnings) add(DashboardAlert(AlertKind.WaterTemperature, severe = true))
        if (TruckWarning.Battery in warnings) add(DashboardAlert(AlertKind.Battery, severe = false))
        if (TruckWarning.Fuel in warnings) add(DashboardAlert(AlertKind.Fuel, severe = false, fuelPercent, fuelRangeKm))
        if (TruckWarning.AdBlue in warnings) add(DashboardAlert(AlertKind.AdBlue, severe = false))
        if (restStopMinutes in
            1..REST_SOON_MINUTES
        ) {
            add(DashboardAlert(AlertKind.Rest, severe = false, restStopMinutes))
        }
        if (wearPercent >= WEAR_PERCENT) add(DashboardAlert(AlertKind.Wear, severe = false, wearPercent))
    }
}

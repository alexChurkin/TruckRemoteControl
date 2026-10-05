package com.alexchurkin.truckremote.ui.main

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.data.controller.Dashboard
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val KMH_IN_MS = 3.6f
private const val MPH_IN_MS = 2.236936f
private const val RED_ZONE = 0.9f
private const val LOW_FUEL_PERCENT = 15
private const val REPEAT_DELAY_MS = 450L
private const val REPEAT_INTERVAL_MS = 220L

/**
 * Instruments in the middle of the controller screen: speed with its unit, the gear, the speed limit sign,
 * engine rpm and the cruise control (its speed is changed here). Only what matters is shown:
 * the speed limit when there is one, the fuel when it's low.
 * [onCruiseToggle] and [onCruiseStep] return true if the command was sent (the button gives haptic feedback then).
 */
@Composable
fun DashboardPanel(
    dashboard: Dashboard,
    onCruiseToggle: () -> Boolean,
    onCruiseStep: (up: Boolean) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val factor = if (dashboard.imperial) MPH_IN_MS else KMH_IN_MS
    val unit = stringResource(if (dashboard.imperial) R.string.dashboard_mph else R.string.dashboard_kmh)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dashboard.speedLimit > 0f) {
                SpeedLimitSign((dashboard.speedLimit * factor).roundToInt(), dashboard.imperial)
                Spacer(Modifier.width(14.dp))
            }
            BasicText(
                text = (abs(dashboard.speed) * factor).roundToInt().toString(),
                style = TextStyle(color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                BasicText(
                    text = unit,
                    style = TextStyle(color = colorResource(R.color.dashboardSecondary), fontSize = 13.sp),
                )
                BasicText(
                    text = gearText(dashboard.gear),
                    style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        RpmBar(dashboard.engineRpm, dashboard.engineRpmMax, Modifier.padding(top = 4.dp))
        CruiseControl(
            speed = (dashboard.cruiseSpeed * factor).roundToInt(),
            unit = unit,
            onToggle = onCruiseToggle,
            onStep = onCruiseStep,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (dashboard.fuelPercent in 0..LOW_FUEL_PERCENT) {
            BasicText(
                text = stringResource(R.string.dashboard_low_fuel, dashboard.fuelPercent),
                style = TextStyle(
                    color = colorResource(R.color.dashboardCaution),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun gearText(gear: Int) = when {
    gear < 0 -> stringResource(R.string.dashboard_gear_reverse, -gear)
    gear == 0 -> stringResource(R.string.dashboard_gear_neutral)
    else -> gear.toString()
}

// A round European sign in ETS2, a rectangular American one in ATS
@Composable
private fun SpeedLimitSign(limit: Int, imperial: Boolean) {
    val shape: Shape = if (imperial) RoundedCornerShape(4.dp) else CircleShape
    val description = stringResource(R.string.dashboard_speed_limit, limit)
    Box(
        modifier = Modifier
            .size(width = if (imperial) 34.dp else 40.dp, height = 40.dp)
            .background(Color.White, shape)
            .border(
                if (imperial) 2.dp else 4.dp,
                if (imperial) Color.Black else colorResource(R.color.indicatorRed),
                shape,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = limit.toString(),
            style = TextStyle(color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold),
        )
    }
}

// Thin bar of the engine rpm, the last part of the range is red
@Composable
private fun RpmBar(rpm: Int, rpmMax: Int, modifier: Modifier = Modifier, width: Dp = 190.dp) {
    val fraction = if (rpmMax > 0) (rpm.toFloat() / rpmMax).coerceIn(0f, 1f) else 0f
    val track = colorResource(R.color.dashboardTrack)
    val fill = colorResource(if (fraction >= RED_ZONE) R.color.indicatorRed else R.color.dashboardRpm)
    Canvas(modifier = modifier.size(width, 4.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(color = track, cornerRadius = radius)
        drawRoundRect(
            color = fill,
            size = Size(size.width * fraction, size.height),
            cornerRadius = radius,
        )
    }
}

// Off: a button that turns it on. On: its speed with - and + (held, they repeat)
@Composable
private fun CruiseControl(
    speed: Int,
    unit: String,
    onToggle: () -> Boolean,
    onStep: (up: Boolean) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val on = speed > 0
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(colorResource(if (on) R.color.actionItemActive else R.color.actionItem), shape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (on) StepButton(text = "−", up = false, onStep = onStep)
        val description = stringResource(if (on) R.string.dashboard_cruise_off else R.string.dashboard_cruise_on)
        BasicText(
            text = if (on) "$speed $unit" else stringResource(R.string.dashboard_cruise),
            style = TextStyle(
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .clickable(role = Role.Button) {
                    if (onToggle()) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                .semantics { contentDescription = description }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
        if (on) StepButton(text = "+", up = true, onStep = onStep)
    }
}

@Composable
private fun StepButton(text: String, up: Boolean, onStep: (up: Boolean) -> Boolean) {
    val view = LocalView.current
    val description = stringResource(if (up) R.string.dashboard_cruise_up else R.string.dashboard_cruise_down)
    fun step() {
        if (onStep(up)) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    Box(
        modifier = Modifier
            .size(56.dp, 40.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .pointerInput(up) {
                detectTapGestures(
                    onPress = {
                        coroutineScope {
                            // A click, then repeated clicks while the button is held
                            val repeat = launch {
                                step()
                                delay(REPEAT_DELAY_MS)
                                while (true) {
                                    step()
                                    delay(REPEAT_INTERVAL_MS)
                                }
                            }
                            tryAwaitRelease()
                            repeat.cancel()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold))
    }
}

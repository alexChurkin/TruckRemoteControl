package com.alexchurkin.truckremote.ui.main

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.data.controller.Dashboard
import com.alexchurkin.truckremote.data.controller.Job
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val KMH_IN_MS = 3.6f
private const val MPH_IN_MS = 2.236936f
private const val RED_ZONE = 0.9f

// The economical range of truck engines
private const val ECONOMY_RPM_FROM = 1000
private const val ECONOMY_RPM_TO = 1500
private const val RPM_TRACK_ALPHA = 0.3f
private val RpmThickness = 6.dp

// The top of a circle in the angles of drawArc (0 is to the right, clockwise)
private const val ARC_TOP_ANGLE = 270f
private const val METERS_IN_KILOMETER = 1000f
private const val METERS_IN_MILE = 1609.344f
private const val PRECISE_DISTANCE_BELOW = 10f
private const val SECONDS_IN_MINUTE = 60L
private const val MINUTES_IN_HOUR = 60
private const val MAX_ALERTS = 3
private const val REPEAT_DELAY_MS = 450L
private const val REPEAT_INTERVAL_MS = 220L
private const val SLOT_ANIMATION_MS = 250
private const val AMERICAN_SIGN_WIDTH = 0.85f

// The place of the speed limit sign on one side of the speed and of the unit with the gear on the other
private val SideWidth = 64.dp

// Three digits of the speed
private const val SPEED_WIDTH_SP = 96

/**
 * The place of the instruments at the top of the controller screen. Without them it has no height, so the controls
 * under it stay at the top. When the game starts sending the truck state, the instruments slide in from the top and
 * push the controls down; when the state is gone ([dashboard] is null), they slide out and the controls come back.
 * [visible] is false while the quick actions panel is open: it needs the place of the pushed controls, so
 * instruments that come or go meanwhile move nothing.
 */
@Composable
fun DashboardSlot(
    dashboard: Dashboard?,
    job: Job?,
    visible: Boolean,
    imperialUnits: Boolean,
    modifier: Modifier = Modifier,
) {
    // The last instruments are drawn while they slide out
    val last = remember { Ref<Dashboard>() }
    if (dashboard != null) last.value = dashboard
    val shown = dashboard ?: last.value
    AnimatedVisibility(
        visible = visible && dashboard != null,
        enter = expandVertically(tween(SLOT_ANIMATION_MS), expandFrom = Alignment.Bottom) +
            fadeIn(tween(SLOT_ANIMATION_MS)),
        exit = shrinkVertically(tween(SLOT_ANIMATION_MS), shrinkTowards = Alignment.Bottom) +
            fadeOut(tween(SLOT_ANIMATION_MS)),
        modifier = modifier,
    ) {
        if (shown != null) DashboardPanel(shown, job, imperialUnits)
    }
}

/**
 * The cruise control under the middle controls: a button that turns it on, or its speed with - and +.
 * It is shown together with the instruments (the cruise speed comes with them) and fades in and out with them;
 * it takes no place of the other controls. [onToggle] and [onStep] return true if the command was sent
 * (the button gives haptic feedback then).
 */
@Composable
fun CruiseSlot(
    dashboard: Dashboard?,
    visible: Boolean,
    imperialUnits: Boolean,
    onToggle: () -> Boolean,
    onStep: (up: Boolean) -> Boolean,
    modifier: Modifier = Modifier,
) {
    // The last speed is drawn while the control fades out
    val last = remember { Ref<Dashboard>() }
    if (dashboard != null) last.value = dashboard
    val shown = dashboard ?: last.value
    AnimatedVisibility(
        visible = visible && dashboard != null,
        enter = fadeIn(tween(SLOT_ANIMATION_MS)),
        exit = fadeOut(tween(SLOT_ANIMATION_MS)),
        modifier = modifier,
    ) {
        if (shown != null) {
            CruiseControl(
                speed = (shown.cruiseSpeed * if (imperialUnits) MPH_IN_MS else KMH_IN_MS).roundToInt(),
                unit = stringResource(if (imperialUnits) R.string.dashboard_mph else R.string.dashboard_kmh),
                onToggle = onToggle,
                onStep = onStep,
            )
        }
    }
}

/**
 * Instruments at the top of the controller screen, to take little height: the engine rpm as a flat arc on top,
 * and in one row under it the speed limit sign, the speed (always in the middle and of the same width, whatever its
 * number of digits) with its unit and the gear. Only what matters is shown: the speed limit when there is one,
 * the route while driving by the navigation, the [job] (cargo, destination and the time left), warnings when there
 * are problems; the lines that come and go change the height smoothly.
 */
@Composable
fun DashboardPanel(dashboard: Dashboard, job: Job?, imperialUnits: Boolean, modifier: Modifier = Modifier) {
    val factor = if (imperialUnits) MPH_IN_MS else KMH_IN_MS
    val unit = stringResource(if (imperialUnits) R.string.dashboard_mph else R.string.dashboard_kmh)
    Column(
        modifier = modifier
            .animateContentSize(tween(SLOT_ANIMATION_MS))
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The arc spans the speed and both sides: its lowered ends are over the speed limit sign and the gear
        val speedWidth = with(LocalDensity.current) { SPEED_WIDTH_SP.sp.toDp() }
        RpmArc(dashboard.engineRpm, dashboard.engineRpmMax, width = SideWidth * 2 + speedWidth)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            // The sides have the same width, so the speed stays in the middle with or without the sign
            Box(modifier = Modifier.width(SideWidth), contentAlignment = Alignment.CenterEnd) {
                if (dashboard.speedLimit > 0f) {
                    SpeedLimitSign(
                        limit = (dashboard.speedLimit * factor).roundToInt(),
                        american = dashboard.isAts,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
            // The place of three digits: the row doesn't change its size while the speed grows
            BasicText(
                text = (abs(dashboard.speed) * factor).roundToInt().toString(),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                    textAlign = TextAlign.Center,
                    // Without the font padding the row is lower
                    lineHeight = 52.sp,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                maxLines = 1,
                modifier = Modifier.width(speedWidth),
            )
            Column(modifier = Modifier.width(SideWidth).padding(start = 8.dp)) {
                BasicText(
                    text = unit,
                    style = TextStyle(color = colorResource(R.color.dashboardSecondary), fontSize = 13.sp),
                    maxLines = 1,
                )
                BasicText(
                    text = gearText(dashboard.gear),
                    style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
        // The route and the job share a line: everything under the instruments is pushed down by their height
        if (dashboard.routeDistance > 0f || job != null) {
            Row(
                modifier = Modifier.padding(top = 4.dp).widthIn(max = 540.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dashboard.routeDistance > 0f) {
                    BasicText(
                        text = routeText(dashboard, imperialUnits),
                        style = TextStyle(color = colorResource(R.color.dashboardSecondary), fontSize = 13.sp),
                        maxLines = 1,
                    )
                }
                if (job != null) JobLine(job, Modifier.weight(1f, fill = false))
            }
        }
        val alerts = dashboard.alerts()
        if (alerts.isNotEmpty()) Alerts(alerts, imperialUnits, Modifier.padding(top = 12.dp))
    }
}

// "128 km · 1 h 45 min" to the end of the route
@Composable
internal fun routeText(dashboard: Dashboard, imperialUnits: Boolean): String {
    val distance = dashboard.routeDistance / if (imperialUnits) METERS_IN_MILE else METERS_IN_KILOMETER
    val distanceText = if (distance >= PRECISE_DISTANCE_BELOW) {
        distance.roundToInt().toString()
    } else {
        String.format(LocalConfiguration.current.locales[0], "%.1f", distance)
    }
    val time = durationText(((dashboard.routeTimeSeconds + SECONDS_IN_MINUTE / 2) / SECONDS_IN_MINUTE).toInt())
    val unit = stringResource(if (imperialUnits) R.string.dashboard_unit_mi else R.string.dashboard_unit_km)
    return stringResource(R.string.dashboard_route, distanceText, unit, time)
}

@Composable
internal fun durationText(minutes: Int) = if (minutes >= MINUTES_IN_HOUR) {
    stringResource(R.string.dashboard_time_hours, minutes / MINUTES_IN_HOUR, minutes % MINUTES_IN_HOUR)
} else {
    stringResource(R.string.dashboard_time_minutes, minutes)
}

// "Logs → Berlin · 3 h 10 min left" (game time), the delay in the caution color
@Composable
private fun JobLine(job: Job, modifier: Modifier = Modifier) {
    val late = job.deliveryMinutesLeft < 0
    val deadline = if (late) {
        stringResource(R.string.dashboard_job_late, durationText(-job.deliveryMinutesLeft))
    } else {
        stringResource(R.string.dashboard_job_left, durationText(job.deliveryMinutesLeft))
    }
    BasicText(
        text = stringResource(R.string.dashboard_job, job.cargo, job.destinationCity, deadline),
        style = TextStyle(
            color = colorResource(if (late) R.color.dashboardCaution else R.color.dashboardSecondary),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.widthIn(max = 460.dp),
    )
}

// Small labels of what needs attention: red ones are serious
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Alerts(alerts: List<DashboardAlert>, imperialUnits: Boolean, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.widthIn(max = 460.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // One row at most: the most important ones and the number of the others
        alerts.take(MAX_ALERTS).forEach { alert ->
            AlertLabel(
                text = alertText(alert, imperialUnits),
                color = colorResource(if (alert.severe) R.color.indicatorRed else R.color.dashboardCaution),
            )
        }
        if (alerts.size > MAX_ALERTS) {
            AlertLabel(text = "+${alerts.size - MAX_ALERTS}", color = colorResource(R.color.dashboardCaution))
        }
    }
}

@Composable
private fun AlertLabel(text: String, color: Color) {
    BasicText(
        text = text,
        style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
internal fun alertText(alert: DashboardAlert, imperialUnits: Boolean) = when (alert.kind) {
    AlertKind.AirPressure -> stringResource(R.string.alert_air_pressure)

    AlertKind.OilPressure -> stringResource(R.string.alert_oil_pressure)

    AlertKind.WaterTemperature -> stringResource(R.string.alert_water_temperature)

    AlertKind.Battery -> stringResource(R.string.alert_battery)

    AlertKind.AdBlue -> stringResource(R.string.alert_adblue)

    AlertKind.Fuel -> if (alert.rangeKm > 0) {
        val range = if (imperialUnits) {
            (alert.rangeKm * METERS_IN_KILOMETER / METERS_IN_MILE).roundToInt()
        } else {
            alert.rangeKm
        }
        val unit = stringResource(if (imperialUnits) R.string.dashboard_unit_mi else R.string.dashboard_unit_km)
        stringResource(R.string.dashboard_low_fuel_range, alert.value, range, unit)
    } else {
        stringResource(R.string.dashboard_low_fuel, alert.value)
    }

    AlertKind.Wear -> stringResource(R.string.alert_wear, alert.value)

    AlertKind.Rest -> stringResource(R.string.alert_rest, alert.value)
}

@Composable
internal fun gearText(gear: Int) = when {
    gear < 0 -> stringResource(R.string.dashboard_gear_reverse, -gear)
    gear == 0 -> stringResource(R.string.dashboard_gear_neutral)
    else -> gear.toString()
}

// A round European sign in ETS2, a rectangular American one in ATS
@Composable
internal fun SpeedLimitSign(limit: Int, american: Boolean, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val shape: Shape = if (american) RoundedCornerShape(4.dp) else CircleShape
    val description = stringResource(R.string.dashboard_speed_limit, limit)
    // The proportions of the 40dp sign
    val scale = size / 40.dp
    Box(
        modifier = modifier
            .size(width = if (american) size * AMERICAN_SIGN_WIDTH else size, height = size)
            .background(Color.White, shape)
            .border(
                if (american) 2.dp * scale else 4.dp * scale,
                if (american) Color.Black else colorResource(R.color.indicatorRed),
                shape,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = limit.toString(),
            style = TextStyle(color = Color.Black, fontSize = 15.sp * scale, fontWeight = FontWeight.Bold),
        )
    }
}

/*
 * The engine rpm as a flat arc, bulging upwards (its ends are [sag] lower than the middle), with the zones of a truck
 * tachometer: low rpm, the economical (green) range, high rpm and the red zone at the end. Every zone is dim on
 * the track and bright where the rpm has reached it.
 */
@Composable
private fun RpmArc(rpm: Int, rpmMax: Int, width: Dp, modifier: Modifier = Modifier, sag: Dp = 10.dp) {
    val fraction = if (rpmMax > 0) (rpm.toFloat() / rpmMax).coerceIn(0f, 1f) else 0f
    val economyStart = if (rpmMax > 0) (ECONOMY_RPM_FROM.toFloat() / rpmMax).coerceAtMost(RED_ZONE) else 0f
    val economyEnd = if (rpmMax > 0) (ECONOMY_RPM_TO.toFloat() / rpmMax).coerceAtMost(RED_ZONE) else 0f
    // The end of every zone (a part of the range) with its color
    val zones = listOf(
        economyStart to colorResource(R.color.dashboardRpm),
        economyEnd to colorResource(R.color.indicatorGreen),
        RED_ZONE to colorResource(R.color.dashboardCaution),
        1f to colorResource(R.color.indicatorRed),
    )
    val thickness = RpmThickness
    Canvas(modifier = modifier.size(width, sag + thickness)) {
        val stroke = thickness.toPx()
        // The circle through the ends and the top of the arc (the middle of the stroke)
        val halfChord = (size.width - stroke) / 2
        val height = sag.toPx()
        val radius = (halfChord * halfChord + height * height) / (2 * height)
        val center = Offset(size.width / 2, stroke / 2 + radius)
        val halfSweep = Math.toDegrees(asin(halfChord / radius).toDouble()).toFloat()
        val startAngle = ARC_TOP_ANGLE - halfSweep
        val sweep = halfSweep * 2
        val box = Size(radius * 2, radius * 2)
        val topLeft = Offset(center.x - radius, center.y - radius)
        fun part(from: Float, to: Float, color: Color) = drawArc(
            color = color,
            startAngle = startAngle + sweep * from,
            sweepAngle = sweep * (to - from),
            useCenter = false,
            topLeft = topLeft,
            size = box,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )

        // Round ends: the color of the zone at the end, bright if the rpm has reached it
        fun end(at: Float, color: Color) {
            val angle = Math.toRadians((startAngle + sweep * at).toDouble())
            val point = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
            drawCircle(color, radius = stroke / 2, center = point)
        }
        val first = zones.first { it.first > 0f }.second
        val last = zones.last().second
        end(0f, if (fraction > 0f) first else first.copy(alpha = RPM_TRACK_ALPHA))
        end(1f, if (fraction >= 1f) last else last.copy(alpha = RPM_TRACK_ALPHA))
        var start = 0f
        zones.forEach { (zoneEnd, color) ->
            if (zoneEnd > start) {
                part(start, zoneEnd, color.copy(alpha = RPM_TRACK_ALPHA))
                val reached = minOf(zoneEnd, fraction)
                if (reached > start) part(start, reached, color)
                start = zoneEnd
            }
        }
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

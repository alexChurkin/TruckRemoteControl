// The gauge is drawn in parts of its size: naming each of the proportions wouldn't make them clearer
@file:Suppress("MagicNumber")

package com.alexchurkin.truckremote.ui.dashboard

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.data.controller.Dashboard
import com.alexchurkin.truckremote.data.controller.Job
import com.alexchurkin.truckremote.data.controller.ServerState
import com.alexchurkin.truckremote.ui.main.SpeedLimitSign
import com.alexchurkin.truckremote.ui.main.alertText
import com.alexchurkin.truckremote.ui.main.alerts
import com.alexchurkin.truckremote.ui.main.durationText
import com.alexchurkin.truckremote.ui.main.gearText
import com.alexchurkin.truckremote.ui.main.routeText
import com.alexchurkin.truckremote.ui.main.speedColor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val KMH_IN_MS = 3.6f
private const val MPH_IN_MS = 2.236936f

// The gauge: an arc of 240° open at the bottom, its start at 150° (drawArc angles: 0 is to the right, clockwise)
private const val GAUGE_START = 150f
private const val GAUGE_SWEEP = 240f

// The part of the height of its circle the arc takes: from the top to its ends, 30 degrees under the middle
private const val ARC_HEIGHT = 0.75f
private const val RED_ZONE = 0.9f
private const val ECONOMY_RPM_FROM = 1000
private const val ECONOMY_RPM_TO = 1500
private const val RPM_TICK = 500
private const val TRACK_ALPHA = 0.25f
private const val DIM_ALPHA = 0.2f
private const val NEEDLE_MS = 150
private const val FULL_PERCENT = 100f
private const val WIDE_RATIO = 1.2f

private val Background = Color(0xFF101010)
private val Card = Color(0xFF1C1C1C)
private val CardShape = RoundedCornerShape(16.dp)

// The place of the lamps at a side of the gauge
private val LampColumn = 56.dp

// The place of the settings button under the cards
private val SettingsRow = 40.dp

// The instruments under a black veil at night
private const val NIGHT_SCRIM = 0.35f
private const val NIGHT_FADE_MS = 400

/**
 * The dashboard mode: big instruments of the truck for a tablet or a second phone. Wide screens have the gauge
 * on the left and the cards on the right, tall ones have the cards under it.
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNightChange: (Boolean) -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize().background(Background)) {
        val truck = state.truck
        val dashboard = truck?.dashboard
        if (truck == null || dashboard == null) {
            Waiting(state, Modifier.align(Alignment.Center))
        } else {
            Instruments(truck, dashboard, state.job, state.imperialUnits, state.speedingWarning)
        }
        // At night the screen is at its lowest brightness (the activity sets it), and even that is too bright
        // on many tablets: the instruments are dimmed more
        val scrim by animateFloatAsState(if (state.night) NIGHT_SCRIM else 0f, tween(NIGHT_FADE_MS), label = "night")
        if (scrim > 0f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = scrim)))
        Row(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
            TextButton(onClick = { onNightChange(!state.night) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_night),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (state.night) R.string.dashboard_mode_day else R.string.dashboard_mode_night))
            }
            // The mode of the app is changed in the settings
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.settings))
            }
        }
    }
}

// The server is searched for, or the game isn't running yet
@Composable
private fun Waiting(state: DashboardUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp).width(460.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(color = colorResource(R.color.indicatorGreen))
        val address = state.serverAddress
        Text(
            text = if (address == null) {
                stringResource(R.string.dashboard_mode_searching)
            } else {
                stringResource(R.string.dashboard_mode_connected, address)
            },
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(
                if (address == null) R.string.dashboard_mode_searching_hint else R.string.dashboard_mode_no_game,
            ),
            color = colorResource(R.color.dashboardSecondary),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Instruments(
    truck: ServerState,
    dashboard: Dashboard,
    job: Job?,
    imperialUnits: Boolean,
    speedingWarning: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val wide = maxWidth > maxHeight * WIDE_RATIO
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Cluster(truck, dashboard, imperialUnits, speedingWarning, Modifier.weight(1.3f).fillMaxHeight())
                Cards(
                    truck,
                    dashboard,
                    job,
                    imperialUnits,
                    Modifier.weight(1f).fillMaxHeight().padding(bottom = SettingsRow),
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Cluster(truck, dashboard, imperialUnits, speedingWarning, Modifier.fillMaxWidth().aspectRatio(1.2f))
                Cards(
                    truck,
                    dashboard,
                    job,
                    imperialUnits,
                    Modifier.weight(1f).fillMaxWidth().padding(bottom = SettingsRow),
                )
            }
        }
    }
}

// The instrument cluster as in a truck: the gauge in the middle of its place, the blinkers at the upper corners
// and the lamps in two columns beside the gauge (what is switched on the left, the systems on the right)
@Composable
private fun Cluster(
    truck: ServerState,
    dashboard: Dashboard,
    imperialUnits: Boolean,
    speedingWarning: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Gauge(dashboard, imperialUnits, speedingWarning, Modifier.fillMaxSize().padding(horizontal = LampColumn))
        Lamp(
            icon = if (truck.leftBlinker) R.drawable.left_enabled else R.drawable.left_disabled,
            tint = null,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Lamp(
            icon = if (truck.rightBlinker) R.drawable.right_enabled else R.drawable.right_disabled,
            tint = null,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        LampColumn(Modifier.align(Alignment.CenterStart)) {
            Lamp(
                when (truck.lightsMode) {
                    1 -> R.drawable.lights_gab
                    2 -> R.drawable.lights_low
                    3 -> R.drawable.lights_high
                    else -> R.drawable.lights_off
                },
                tint = null,
            )
            Lamp(if (truck.parkingBrake) R.drawable.parking_break_on else R.drawable.parking_break_off, tint = null)
            Lamp(R.drawable.ic_action_engine, tint = colorResource(R.color.indicatorGreen), on = truck.engineOn)
            Lamp(R.drawable.ic_action_trailer, tint = colorResource(R.color.indicatorGreen), on = truck.trailerAttached)
        }
        LampColumn(Modifier.align(Alignment.CenterEnd)) {
            Lamp(R.drawable.ic_action_beacon, tint = colorResource(R.color.indicatorYellow), on = truck.beaconOn)
            Lamp(
                R.drawable.ic_action_diff_lock,
                tint = colorResource(R.color.indicatorYellow),
                on = truck.differentialLock,
            )
            Lamp(R.drawable.ic_action_lift_axle, tint = colorResource(R.color.indicatorYellow), on = truck.liftAxle)
            Lamp(
                R.drawable.ic_action_engine_brake,
                tint = colorResource(R.color.indicatorGreen),
                on = truck.engineBrake,
            )
        }
    }
}

@Composable
private fun LampColumn(modifier: Modifier = Modifier, lamps: @Composable () -> Unit) {
    Column(
        modifier = modifier.width(LampColumn),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { lamps() }
}

// A picture of the lamp's state as it is (tint null), or an icon in its color when on and dim when off
@Composable
private fun Lamp(@DrawableRes icon: Int, tint: Color?, modifier: Modifier = Modifier, on: Boolean = true) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = tint?.let { ColorFilter.tint(if (on) it else Color.White.copy(alpha = DIM_ALPHA)) },
        modifier = modifier.size(if (tint == null) 40.dp else 28.dp),
    )
}

// The rpm on the arc with the zones of a truck tachometer, the speed and the gear inside, the limit and the cruise
@Composable
private fun Gauge(
    dashboard: Dashboard,
    imperialUnits: Boolean,
    speedingWarning: Boolean,
    modifier: Modifier = Modifier,
) {
    val factor = if (imperialUnits) MPH_IN_MS else KMH_IN_MS
    val unit = stringResource(if (imperialUnits) R.string.dashboard_mph else R.string.dashboard_kmh)
    val rpmMax = dashboard.engineRpmMax
    val fraction by animateFloatAsState(
        targetValue = if (rpmMax > 0) (dashboard.engineRpm.toFloat() / rpmMax).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(NEEDLE_MS),
        label = "rpm",
    )
    val trackColor = colorResource(R.color.dashboardRpm)
    val zones = listOf(
        zoneEnd(ECONOMY_RPM_FROM, rpmMax) to colorResource(R.color.dashboardRpm),
        zoneEnd(ECONOMY_RPM_TO, rpmMax) to colorResource(R.color.indicatorGreen),
        RED_ZONE to colorResource(R.color.dashboardCaution),
        1f to colorResource(R.color.indicatorRed),
    )
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // The arc is open at the bottom, so it takes only a part of the height of its circle: the circle is as big
        // as the arc fits, and it is moved down so that the arc (not the circle) is in the middle of the place
        val side = minOf(maxWidth, maxHeight / ARC_HEIGHT)
        val centered = Modifier.requiredSize(side).offset(y = side * (1 - ARC_HEIGHT) / 2)
        Canvas(centered) {
            val stroke = size.minDimension * 0.06f
            drawZones(zones, fraction, stroke)
            drawTicks(rpmMax, stroke, trackColor)
        }
        Column(
            modifier = centered,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (dashboard.speedLimit > 0f) {
                SpeedLimitSign(
                    limit = (dashboard.speedLimit * factor).roundToInt(),
                    american = dashboard.isAts,
                    size = side * 0.14f,
                )
                Spacer(Modifier.height(side * 0.02f))
            }
            BasicText(
                text = (abs(dashboard.speed) * factor).roundToInt().toString(),
                style = TextStyle(
                    color = speedColor(dashboard, speedingWarning),
                    fontSize = (side.value * 0.26f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                ),
            )
            BasicText(
                text = unit,
                style = TextStyle(
                    color = colorResource(R.color.dashboardSecondary),
                    fontSize = (side.value * 0.05f).sp,
                ),
            )
            BasicText(
                text = gearText(dashboard.gear),
                style = TextStyle(
                    color = Color.White,
                    fontSize = (side.value * 0.09f).sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (dashboard.cruiseSpeed > 0f) {
                BasicText(
                    text = stringResource(R.string.dashboard_cruise) + " " +
                        (dashboard.cruiseSpeed * factor).roundToInt() + " " + unit,
                    style = TextStyle(
                        color = colorResource(R.color.indicatorGreen),
                        fontSize = (side.value * 0.045f).sp,
                    ),
                )
            }
        }
    }
}

// The part of the range where a zone ends, the red zone is always the last part
private fun zoneEnd(rpm: Int, rpmMax: Int) = if (rpmMax > 0) (rpm.toFloat() / rpmMax).coerceAtMost(RED_ZONE) else 0f

private fun DrawScope.drawZones(zones: List<Pair<Float, Color>>, fraction: Float, stroke: Float) {
    val inset = stroke / 2
    val arcSize = Size(size.width - stroke, size.height - stroke)
    var start = 0f
    zones.forEach { (end, color) ->
        if (end <= start) return@forEach
        fun part(from: Float, to: Float, partColor: Color) = drawArc(
            color = partColor,
            startAngle = GAUGE_START + GAUGE_SWEEP * from,
            sweepAngle = GAUGE_SWEEP * (to - from),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
        part(start, end, color.copy(alpha = TRACK_ALPHA))
        val reached = minOf(end, fraction)
        if (reached > start) part(start, reached, color)
        start = end
    }
}

// A tick every 500 rpm inside the arc, longer every 1000
private fun DrawScope.drawTicks(rpmMax: Int, stroke: Float, color: Color) {
    if (rpmMax <= 0) return
    val radius = size.minDimension / 2 - stroke * 1.4f
    val center = Offset(size.width / 2, size.height / 2)
    for (rpm in 0..rpmMax step RPM_TICK) {
        val angle = Math.toRadians((GAUGE_START + GAUGE_SWEEP * rpm / rpmMax).toDouble())
        val length = if (rpm % (RPM_TICK * 2) == 0) stroke * 0.9f else stroke * 0.45f
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        drawLine(
            color = color,
            start = center + direction * radius,
            end = center + direction * (radius - length),
            strokeWidth = stroke * 0.12f,
            cap = StrokeCap.Round,
        )
    }
}

// Fuel, the route, the job, the retarder and the warnings
@Composable
private fun Cards(
    truck: ServerState,
    dashboard: Dashboard,
    job: Job?,
    imperialUnits: Boolean,
    modifier: Modifier = Modifier,
) {
    // In the middle of their place while they fit, scrolled when there are more of them
    Box(modifier, contentAlignment = Alignment.Center) {
        CardList(truck, dashboard, job, imperialUnits)
    }
}

@Composable
private fun CardList(truck: ServerState, dashboard: Dashboard, job: Job?, imperialUnits: Boolean) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FuelCard(dashboard, imperialUnits)
        if (dashboard.routeDistance > 0f) {
            InfoCard(title = stringResource(R.string.dashboard_mode_route), text = routeText(dashboard, imperialUnits))
        }
        if (job != null) JobCard(job)
        if (truck.retarderSteps > 0) {
            InfoCard(
                title = stringResource(R.string.dashboard_mode_retarder),
                text = "${truck.retarderLevel} / ${truck.retarderSteps}",
            )
        }
        val alerts = dashboard.alerts()
        if (alerts.isNotEmpty()) {
            InfoCard(title = stringResource(R.string.dashboard_mode_warnings)) {
                alerts.forEach { alert ->
                    Text(
                        text = alertText(alert, imperialUnits),
                        color = colorResource(if (alert.severe) R.color.indicatorRed else R.color.dashboardCaution),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FuelCard(dashboard: Dashboard, imperialUnits: Boolean) {
    val low = dashboard.fuelPercent <= LOW_FUEL_PERCENT
    val range = if (dashboard.fuelRangeKm > 0) {
        val value = if (imperialUnits) (dashboard.fuelRangeKm / KM_IN_MILE).roundToInt() else dashboard.fuelRangeKm
        " · $value " + stringResource(if (imperialUnits) R.string.dashboard_unit_mi else R.string.dashboard_unit_km)
    } else {
        ""
    }
    InfoCard(title = stringResource(R.string.dashboard_mode_fuel), text = "${dashboard.fuelPercent}%$range") {
        Bar(
            fraction = dashboard.fuelPercent / FULL_PERCENT,
            color = colorResource(if (low) R.color.dashboardCaution else R.color.indicatorGreen),
        )
    }
}

@Composable
private fun JobCard(job: Job) {
    val late = job.deliveryMinutesLeft < 0
    val deadline = if (late) {
        stringResource(R.string.dashboard_job_late, durationText(-job.deliveryMinutesLeft))
    } else {
        stringResource(R.string.dashboard_job_left, durationText(job.deliveryMinutesLeft))
    }
    InfoCard(title = stringResource(R.string.dashboard_mode_job), text = "${job.cargo} → ${job.destinationCity}") {
        Text(
            text = deadline,
            color = colorResource(if (late) R.color.dashboardCaution else R.color.dashboardSecondary),
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    content: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().background(Card, CardShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, color = colorResource(R.color.dashboardSecondary), fontSize = 13.sp)
        if (text != null) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content()
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(color = color.copy(alpha = TRACK_ALPHA), cornerRadius = radius)
        drawRoundRect(
            color = color,
            size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
            cornerRadius = radius,
        )
    }
}

private const val LOW_FUEL_PERCENT = 15
private const val KM_IN_MILE = 1.609344f

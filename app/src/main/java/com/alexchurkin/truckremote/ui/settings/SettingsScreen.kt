@file:OptIn(ExperimentalMaterial3Api::class)

package com.alexchurkin.truckremote.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexchurkin.truckremote.BuildConfig
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.billing.BillingEvent
import com.alexchurkin.truckremote.settings.AppSettings
import com.alexchurkin.truckremote.settings.PedalMode
import com.alexchurkin.truckremote.ui.theme.TruckRemoteTheme
import kotlin.math.roundToInt

// Open dialog is kept in saved state, so it survives rotation together with the typed text
private enum class SettingsDialog {
    None,
    Port,
    ServerIp,
    About,
}

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onServerPortChange: (Int) -> Unit = {},
    val onUseSpecifiedServerChange: (Boolean) -> Unit = {},
    val onServerIpChange: (String) -> Unit = {},
    val onForceFeedbackChange: (Boolean) -> Unit = {},
    val onPneumaticHornChange: (Boolean) -> Unit = {},
    val onSteeringDeadZoneChange: (Int) -> Unit = {},
    val onSteeringMaxAngleChange: (Int) -> Unit = {},
    val onSteeringExponentChange: (Float) -> Unit = {},
    val onPedalModeChange: (PedalMode) -> Unit = {},
    val onThrottleLockChange: (Boolean) -> Unit = {},
    val onRemoveAds: () -> Unit = {},
    val onOpenGithub: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onRemoveAds: () -> Unit,
    onOpenGithub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.billingEvents.collect { event ->
            snackbarHostState.showSnackbar(resources.getString(event.messageRes))
        }
    }

    val actions = remember(viewModel, onBack, onRemoveAds, onOpenGithub) {
        SettingsActions(
            onBack = onBack,
            onServerPortChange = viewModel::setServerPort,
            onUseSpecifiedServerChange = viewModel::setUseSpecifiedServer,
            onServerIpChange = viewModel::setServerIp,
            onForceFeedbackChange = viewModel::setForceFeedback,
            onPneumaticHornChange = viewModel::setPneumaticHorn,
            onSteeringDeadZoneChange = viewModel::setSteeringDeadZone,
            onSteeringMaxAngleChange = viewModel::setSteeringMaxAngle,
            onSteeringExponentChange = viewModel::setSteeringExponent,
            onPedalModeChange = viewModel::setPedalMode,
            onThrottleLockChange = viewModel::setThrottleLock,
            onRemoveAds = onRemoveAds,
            onOpenGithub = onOpenGithub,
        )
    }

    SettingsContent(
        state = state,
        actions = actions,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@get:StringRes
private val BillingEvent.messageRes: Int
    get() = when (this) {
        BillingEvent.Purchased -> R.string.purchase_success
        BillingEvent.Restored -> R.string.purchase_restored
        BillingEvent.Returned -> R.string.purchase_returned
        BillingEvent.Cancelled -> R.string.purchase_cancelled
    }

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var dialog by rememberSaveable { mutableStateOf(SettingsDialog.None) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        SettingsList(
            state = state,
            actions = actions,
            onOpenDialog = { dialog = it },
            contentPadding = innerPadding,
        )

        when (dialog) {
            SettingsDialog.None -> Unit

            SettingsDialog.Port -> TextInputDialog(
                title = stringResource(R.string.server_port_dialog_title),
                initialValue = state.serverPort.toString(),
                keyboardType = KeyboardType.Number,
                errorRes = { if (SettingsViewModel.parsePort(it) == null) R.string.server_port_invalid else null },
                onConfirm = { text ->
                    SettingsViewModel.parsePort(text)?.let(actions.onServerPortChange)
                    dialog = SettingsDialog.None
                },
                onDismiss = { dialog = SettingsDialog.None },
            )

            SettingsDialog.ServerIp -> TextInputDialog(
                title = stringResource(R.string.def_server_ip_title),
                initialValue = state.serverIp,
                keyboardType = KeyboardType.Decimal,
                errorRes = { if (SettingsViewModel.isValidIp(it)) null else R.string.server_ip_invalid },
                onConfirm = { text ->
                    actions.onServerIpChange(text.trim())
                    dialog = SettingsDialog.None
                },
                onDismiss = { dialog = SettingsDialog.None },
            )

            SettingsDialog.About -> AboutDialog(onDismiss = { dialog = SettingsDialog.None })
        }
    }
}

@Composable
private fun SettingsList(
    state: SettingsUiState,
    actions: SettingsActions,
    onOpenDialog: (SettingsDialog) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { SectionHeader(R.string.connection) }
        item {
            ClickableItem(
                title = stringResource(R.string.server_port_title),
                summary = state.serverPort.toString(),
                onClick = { onOpenDialog(SettingsDialog.Port) },
            )
        }
        item {
            SwitchItem(
                title = stringResource(R.string.connect_to_default_on_startup_title),
                summary = stringResource(
                    if (state.useSpecifiedServer) {
                        R.string.connect_to_default_on_startup_summary_on
                    } else {
                        R.string.connect_to_default_on_startup_summary_off
                    },
                ),
                checked = state.useSpecifiedServer,
                onCheckedChange = actions.onUseSpecifiedServerChange,
            )
        }
        item {
            ClickableItem(
                title = stringResource(R.string.def_server_ip_title),
                summary = state.serverIp.ifEmpty { stringResource(R.string.def_server_ip_summary) },
                enabled = state.useSpecifiedServer,
                onClick = { onOpenDialog(SettingsDialog.ServerIp) },
            )
        }

        item { SectionHeader(R.string.control) }
        item {
            SwitchItem(
                title = stringResource(R.string.ffb_text),
                summary = stringResource(R.string.ffb_summary),
                checked = state.forceFeedback,
                onCheckedChange = actions.onForceFeedbackChange,
            )
        }
        item {
            SwitchItem(
                title = stringResource(R.string.use_pneumatic_signal_text),
                summary = stringResource(R.string.use_pneumatic_signal_summary),
                checked = state.pneumaticHorn,
                onCheckedChange = actions.onPneumaticHornChange,
            )
        }
        item {
            val range = AppSettings.STEERING_DEAD_ZONE_RANGE
            SliderItem(
                title = stringResource(R.string.steering_dead_zone_title),
                summary = { stringResource(R.string.steering_dead_zone_summary, it.roundToInt()) },
                value = state.steeringDeadZone.toFloat(),
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = range.last - range.first - 1,
                onValueChange = { actions.onSteeringDeadZoneChange(it.roundToInt()) },
            )
        }
        item {
            val range = AppSettings.STEERING_MAX_ANGLE_RANGE
            SliderItem(
                title = stringResource(R.string.steering_max_angle_title),
                summary = { stringResource(R.string.steering_max_angle_summary, it.roundToInt()) },
                value = state.steeringMaxAngle.toFloat(),
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first) / MAX_ANGLE_STEP - 1,
                onValueChange = { actions.onSteeringMaxAngleChange(it.roundToInt()) },
            )
        }
        item {
            val range = AppSettings.STEERING_EXPONENT_RANGE
            SliderItem(
                title = stringResource(R.string.steering_curve_title),
                summary = {
                    if (it <= range.start) {
                        stringResource(R.string.steering_curve_linear)
                    } else {
                        stringResource(R.string.steering_curve_summary, it)
                    }
                },
                value = state.steeringExponent,
                valueRange = range,
                steps = ((range.endInclusive - range.start) / EXPONENT_STEP).roundToInt() - 1,
                onValueChange = { actions.onSteeringExponentChange((it / EXPONENT_STEP).roundToInt() * EXPONENT_STEP) },
            )
        }

        item { SectionHeader(R.string.pedals) }
        item { PedalModeItem(mode = state.pedalMode, onModeChange = actions.onPedalModeChange) }
        item {
            SwitchItem(
                title = stringResource(R.string.throttle_lock_title),
                summary = stringResource(R.string.throttle_lock_summary),
                checked = state.throttleLock,
                onCheckedChange = actions.onThrottleLockChange,
            )
        }

        item { SectionHeader(R.string.additionally) }
        if (!state.adsRemoved) {
            item { ClickableItem(title = stringResource(R.string.remove_ads), onClick = actions.onRemoveAds) }
        }
        item { ClickableItem(title = stringResource(R.string.github_page), onClick = actions.onOpenGithub) }
        item {
            ClickableItem(
                title = stringResource(R.string.about_app),
                summary = "${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME}",
                onClick = { onOpenDialog(SettingsDialog.About) },
            )
        }
    }
}

@Composable
private fun SectionHeader(@StringRes titleRes: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 16.dp)) {
        HorizontalDivider()
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ClickableItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (summary != null) {
            { Text(summary) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(
            headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        ),
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun SwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

// The value is saved when dragging is finished, the summary follows the finger
@Composable
private fun SliderItem(
    title: String,
    summary: @Composable (Float) -> String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = modifier.padding(bottom = 8.dp)) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary(current)) },
        )
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onValueChange(current) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun PedalModeItem(mode: PedalMode, onModeChange: (PedalMode) -> Unit, modifier: Modifier = Modifier) {
    val labels = stringArrayResource(R.array.pedal_mode_entries)
    Column(modifier = modifier.padding(bottom = 8.dp)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.pedal_mode_title)) },
            supportingContent = { Text(stringResource(R.string.pedal_mode_summary)) },
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            PedalMode.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = entry == mode,
                    onClick = { onModeChange(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = PedalMode.entries.size),
                ) {
                    Text(labels[index])
                }
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initialValue: String,
    keyboardType: KeyboardType,
    errorRes: (String) -> Int?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Typed text survives rotation
    var text by rememberSaveable { mutableStateOf(initialValue) }
    val error = errorRes(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(stringResource(error)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = error == null) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_app)) },
        text = {
            Text(
                text = stringResource(R.string.about_app_text),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

private const val DISABLED_ALPHA = 0.38f
private const val MAX_ANGLE_STEP = 5
private const val EXPONENT_STEP = 0.1f

@Preview
@Composable
private fun SettingsPreview() {
    TruckRemoteTheme {
        SettingsContent(
            state = SettingsUiState(
                serverPort = 18250,
                useSpecifiedServer = true,
                serverIp = "192.168.1.10",
                forceFeedback = true,
                pneumaticHorn = false,
                steeringDeadZone = 3,
                steeringMaxAngle = 60,
                steeringExponent = 1.5f,
                pedalMode = PedalMode.Analog,
                throttleLock = true,
                adsRemoved = false,
            ),
            actions = SettingsActions(),
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

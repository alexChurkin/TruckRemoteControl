@file:OptIn(ExperimentalMaterial3Api::class)

package com.alexchurkin.truckremote.ui.settings

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexchurkin.truckremote.BuildConfig
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.data.settings.AppLanguage
import com.alexchurkin.truckremote.data.settings.AppSettings
import com.alexchurkin.truckremote.data.settings.PedalMode
import com.alexchurkin.truckremote.ui.theme.TruckRemoteTheme
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.style.m3VariantColors
import com.mikepenz.aboutlibraries.ui.compose.style.LicenseHueResolver
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryActionKind
import com.mikepenz.aboutlibraries.util.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

// Shown page and open dialog are kept in saved state, so they survive rotation together with the typed text
private enum class SettingsPage {
    Main,
    Licenses,
}

private enum class SettingsDialog {
    None,
    Port,
    ServerIp,
    Language,
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
    val onCalibrate: () -> Unit = {},
    val onResetCalibration: () -> Unit = {},
    val onPedalModeChange: (PedalMode) -> Unit = {},
    val onThrottleLockChange: (Boolean) -> Unit = {},
    val onScanQr: () -> Unit = {},
    val onOpenGuide: () -> Unit = {},
    val onRestorePurchase: () -> Unit = {},
    val onOpenGithub: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenGithub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The tilt sensor works only while the screen is shown
    val steering by viewModel.steeringPreview.collectAsStateWithLifecycle(initialValue = null)
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message.format(resources)) }
    }

    val actions = remember(viewModel, onBack, onOpenGuide, onOpenGithub, context) {
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
            onCalibrate = viewModel::calibrate,
            onResetCalibration = viewModel::resetCalibration,
            onPedalModeChange = viewModel::setPedalMode,
            onThrottleLockChange = viewModel::setThrottleLock,
            onScanQr = {
                QrScanner.scan(
                    context = context,
                    onResult = viewModel::applyScannedServer,
                    onLoading = viewModel::onScannerLoading,
                    onUnavailable = viewModel::onScannerUnavailable,
                )
            },
            onOpenGuide = onOpenGuide,
            onRestorePurchase = viewModel::restorePurchase,
            onOpenGithub = onOpenGithub,
        )
    }

    SettingsContent(
        state = state,
        steering = steering,
        actions = actions,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    steering: Float?,
    actions: SettingsActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.Main) }
    var dialog by rememberSaveable { mutableStateOf(SettingsDialog.None) }
    // Hoisted, so the position is kept while the licenses are shown
    val mainListState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val onNavigateBack = { if (page == SettingsPage.Licenses) page = SettingsPage.Main else actions.onBack() }

    BackHandler(enabled = page == SettingsPage.Licenses) { page = SettingsPage.Main }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Landscape: content must not go under the display cutout and the navigation bar on a side
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (page == SettingsPage.Licenses) R.string.third_party_title else R.string.settings,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        when (page) {
            SettingsPage.Main -> SettingsList(
                state = state,
                steering = steering,
                actions = actions,
                listState = mainListState,
                onOpenDialog = { dialog = it },
                onOpenLicenses = { page = SettingsPage.Licenses },
                contentPadding = innerPadding,
            )

            SettingsPage.Licenses -> LicensesList(contentPadding = innerPadding)
        }

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

            SettingsDialog.Language -> LanguageDialog(
                current = AppLanguage.current(),
                onSelect = { language ->
                    dialog = SettingsDialog.None
                    // Screens are recreated in the new language
                    language.apply()
                },
                onDismiss = { dialog = SettingsDialog.None },
            )

            SettingsDialog.About -> AboutDialog(onDismiss = { dialog = SettingsDialog.None })
        }
    }
}

/*
 * Sections go from the most tuned to the rarely changed ones: steering, pedals, buttons, connection,
 * the app and the information about it.
 */
@Composable
private fun SettingsList(
    state: SettingsUiState,
    steering: Float?,
    actions: SettingsActions,
    listState: LazyListState,
    onOpenDialog: (SettingsDialog) -> Unit,
    onOpenLicenses: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        steeringSection(state, steering, actions)

        centeredItem { SectionHeader(R.string.pedals) }
        centeredItem { PedalModeItem(mode = state.pedalMode, onModeChange = actions.onPedalModeChange) }
        centeredItem {
            SwitchItem(
                title = stringResource(R.string.throttle_lock_title),
                summary = stringResource(R.string.throttle_lock_summary),
                checked = state.throttleLock,
                onCheckedChange = actions.onThrottleLockChange,
            )
        }

        centeredItem { SectionHeader(R.string.section_buttons) }
        centeredItem {
            SwitchItem(
                title = stringResource(R.string.ffb_text),
                summary = stringResource(R.string.ffb_summary),
                checked = state.forceFeedback,
                onCheckedChange = actions.onForceFeedbackChange,
            )
        }
        centeredItem {
            SwitchItem(
                title = stringResource(R.string.use_pneumatic_signal_text),
                summary = stringResource(R.string.use_pneumatic_signal_summary),
                checked = state.pneumaticHorn,
                onCheckedChange = actions.onPneumaticHornChange,
            )
        }

        connectionSection(state, actions, onOpenDialog)

        centeredItem { SectionHeader(R.string.section_app) }
        centeredItem {
            ClickableItem(
                title = stringResource(R.string.language_title),
                summary = AppLanguage.current().label(),
                onClick = { onOpenDialog(SettingsDialog.Language) },
            )
        }
        centeredItem { ClickableItem(title = stringResource(R.string.start_guide), onClick = actions.onOpenGuide) }
        centeredItem {
            if (state.adsRemoved) {
                InfoItem(
                    title = stringResource(R.string.ads_removed_title),
                    summary = stringResource(R.string.ads_removed_summary),
                )
            } else {
                ClickableItem(
                    title = stringResource(R.string.restore_purchase_title),
                    summary = stringResource(R.string.restore_purchase_summary),
                    onClick = actions.onRestorePurchase,
                )
            }
        }

        centeredItem { SectionHeader(R.string.about_app) }
        centeredItem { ClickableItem(title = stringResource(R.string.github_page), onClick = actions.onOpenGithub) }
        centeredItem {
            ClickableItem(
                title = stringResource(R.string.third_party_title),
                summary = stringResource(R.string.third_party_summary),
                onClick = onOpenLicenses,
            )
        }
        centeredItem {
            ClickableItem(
                title = stringResource(R.string.about_app),
                summary = "${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME}",
                onClick = { onOpenDialog(SettingsDialog.About) },
            )
        }
    }
}

// The steering is set up on the phone (the server applies it as is), with a live preview
private fun LazyListScope.steeringSection(state: SettingsUiState, steering: Float?, actions: SettingsActions) {
    centeredItem { SectionHeader(R.string.section_steering) }
    centeredItem { SteeringPreview(steering) }
    centeredItem {
        val range = AppSettings.STEERING_MAX_ANGLE_RANGE
        SliderItem(
            title = stringResource(R.string.steering_sensitivity_title),
            summary = { stringResource(R.string.steering_max_angle_summary, it.roundToInt()) },
            value = state.steeringMaxAngle.toFloat(),
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / MAX_ANGLE_STEP - 1,
            onValueChange = { actions.onSteeringMaxAngleChange(it.roundToInt()) },
        )
    }
    centeredItem {
        val range = AppSettings.STEERING_DEAD_ZONE_RANGE
        SliderItem(
            title = stringResource(R.string.steering_dead_zone_title),
            summary = {
                val degrees = it.roundToInt()
                if (degrees == 0) {
                    stringResource(R.string.steering_dead_zone_off)
                } else {
                    stringResource(R.string.steering_dead_zone_summary, degrees)
                }
            },
            value = state.steeringDeadZone.toFloat(),
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            onValueChange = { actions.onSteeringDeadZoneChange(it.roundToInt()) },
        )
    }
    centeredItem {
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
    calibrationItems(state, actions)
}

private fun LazyListScope.calibrationItems(state: SettingsUiState, actions: SettingsActions) {
    centeredItem {
        ClickableItem(
            title = stringResource(R.string.calibrate_title),
            summary = stringResource(R.string.calibrate_summary),
            onClick = actions.onCalibrate,
        )
    }
    if (state.calibrated) {
        centeredItem {
            ClickableItem(
                title = stringResource(R.string.calibration_reset_title),
                onClick = actions.onResetCalibration,
            )
        }
    }
}

// Search or a fixed address; the address can be taken from the QR code of the server window
private fun LazyListScope.connectionSection(
    state: SettingsUiState,
    actions: SettingsActions,
    onOpenDialog: (SettingsDialog) -> Unit,
) {
    centeredItem { SectionHeader(R.string.connection) }
    centeredItem {
        SearchModeItem(useSpecifiedServer = state.useSpecifiedServer, onChange = actions.onUseSpecifiedServerChange)
    }
    if (state.useSpecifiedServer) {
        centeredItem {
            ClickableItem(
                title = stringResource(R.string.def_server_ip_title),
                summary = state.serverIp.ifEmpty { stringResource(R.string.def_server_ip_summary) },
                onClick = { onOpenDialog(SettingsDialog.ServerIp) },
            )
        }
    }
    centeredItem {
        ClickableItem(
            title = stringResource(R.string.scan_qr_title),
            summary = stringResource(R.string.scan_qr_summary),
            onClick = actions.onScanQr,
        )
    }
    centeredItem {
        ClickableItem(
            title = stringResource(R.string.server_port_title),
            summary = state.serverPort.toString(),
            onClick = { onOpenDialog(SettingsDialog.Port) },
        )
    }
}

// In landscape the list would be too wide to read, so its items have a limited width
private fun LazyListScope.centeredItem(content: @Composable () -> Unit) = item {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth()) { content() }
    }
}

// Licenses of the libraries are collected at build time (AboutLibraries plugin), icons are described in app/config
@Composable
private fun LicensesList(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Icons are shown first: they are what the app itself is made of, libraries follow alphabetically
    val libraries = remember {
        val libs = Libs.Builder().withContext(context).build()
        libs.copy(libraries = libs.libraries.sortedBy { it.uniqueId !in ICON_SETS })
    }
    // License text is shown in the app (works offline, survives rotation); a link is opened only without the text
    var dialogLibraryId by rememberSaveable { mutableStateOf<String?>(null) }
    val dialogLibrary = libraries.libraries.firstOrNull { it.uniqueId == dialogLibraryId }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LibrariesContainer(
            libraries = libraries,
            // The dialog of the library isn't Material 3 (no title, plain text wall), ours is shown instead
            dialogLibrary = null,
            sheetLibrary = null,
            onDialogLibraryChange = {},
            onSheetLibraryChange = {},
            modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize(),
            contentPadding = contentPadding,
            // Tonal license badges of the color scheme instead of a different hue for every license
            variantColors = LibraryDefaults.m3VariantColors(
                licenseHueResolver = LicenseHueResolver.None,
                licenseBadgeContainer = MaterialTheme.colorScheme.secondaryContainer,
                licenseBadgeContent = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            onActionClick = { library, kind ->
                val hasText = library.licenses.any { !it.licenseContent.isNullOrBlank() }
                if (kind == LibraryActionKind.License && hasText) dialogLibraryId = library.uniqueId
                kind == LibraryActionKind.License && hasText
            },
        )
    }

    dialogLibrary?.let { LicenseTextDialog(library = it, onDismiss = { dialogLibraryId = null }) }
}

@Composable
private fun LicenseTextDialog(library: Library, onDismiss: () -> Unit) {
    val licenses = library.licenses.filter { !it.licenseContent.isNullOrBlank() }
    val link = licenses.firstNotNullOfOrNull { it.url?.takeIf(String::isNotBlank) }
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(licenses.joinToString(" / ") { it.name }) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = listOfNotNull(library.name, library.artifactVersion).joinToString(" "),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                licenses.forEach { license ->
                    Text(
                        text = license.licenseContent.orEmpty().trim(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        dismissButton = if (link != null) {
            {
                TextButton(onClick = { runCatching { uriHandler.openUri(link) } }) {
                    Text(stringResource(R.string.license_open_link))
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun InfoItem(title: String, summary: String, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        modifier = modifier,
    )
}

@Composable
// Material 3 list subheader: groups are separated by space, not by dividers
private fun SectionHeader(@StringRes titleRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
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
    // Survives rotation while dragging
    var current by rememberSaveable(value) { mutableFloatStateOf(value) }
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
            // Material 3 shows tick marks only for a few values, here they would be a row of dots
            track = { SliderDefaults.Track(sliderState = it, drawTick = { _, _ -> }) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

// What the phone sends now: the wheel turns and the bar fills while the phone is tilted
@Composable
private fun SteeringPreview(steering: Float?, modifier: Modifier = Modifier) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val value = if (landscape) steering ?: 0f else 0f
    val percent = (value * PERCENT).roundToInt()
    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_action_camera_interior),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(40.dp)
                    .rotate(value * MAX_WHEEL_ROTATION),
            )
        },
        headlineContent = {
            Text(
                when {
                    !landscape -> stringResource(R.string.steering_preview_portrait)
                    percent < 0 -> stringResource(R.string.steering_preview_left, -percent)
                    percent > 0 -> stringResource(R.string.steering_preview_right, percent)
                    else -> stringResource(R.string.steering_preview_center)
                },
            )
        },
        supportingContent = if (landscape) {
            { SteeringBar(value, Modifier.padding(top = 8.dp)) }
        } else {
            null
        },
        modifier = modifier,
    )
}

// Filled from the center to the steering value
@Composable
private fun SteeringBar(value: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    val center = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp),
    ) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(color = track, cornerRadius = radius)
        val middle = size.width / 2
        val end = middle + value.coerceIn(-1f, 1f) * middle
        drawRoundRect(
            color = fill,
            topLeft = Offset(minOf(middle, end), 0f),
            size = Size(abs(end - middle), size.height),
            cornerRadius = radius,
        )
        drawRect(color = center, topLeft = Offset(middle - 1.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height))
    }
}

@Composable
private fun SearchModeItem(useSpecifiedServer: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val labels = listOf(stringResource(R.string.search_mode_auto), stringResource(R.string.search_mode_ip))
    Column(modifier = modifier.padding(bottom = 8.dp)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.search_mode_title)) },
            supportingContent = {
                Text(
                    stringResource(
                        if (useSpecifiedServer) R.string.search_mode_summary_ip else R.string.search_mode_summary_auto,
                    ),
                )
            },
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = (index == 1) == useSpecifiedServer,
                    onClick = { onChange(index == 1) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun PedalModeItem(mode: PedalMode, onModeChange: (PedalMode) -> Unit, modifier: Modifier = Modifier) {
    val labels = stringArrayResource(R.array.pedal_mode_entries)
    Column(modifier = modifier.padding(bottom = 8.dp)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.pedal_mode_title)) },
            // Describes the selected mode
            supportingContent = {
                Text(
                    stringResource(
                        when (mode) {
                            PedalMode.Digital -> R.string.pedal_mode_summary_digital
                            PedalMode.Analog -> R.string.pedal_mode_summary_analog
                        },
                    ),
                )
            },
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
    // Typed text and the cursor survive rotation; the cursor starts at the end to continue typing
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialValue, selection = TextRange(initialValue.length)))
    }
    val text = field.text
    val error = errorRes(text)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(stringResource(error)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (error == null) onConfirm(text) }),
                modifier = Modifier.focusRequester(focusRequester),
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
private fun AppLanguage.label(): String = nativeName ?: stringResource(R.string.language_system)

// Material 3 simple dialog: choosing an option applies it at once
@Composable
private fun LanguageDialog(current: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = language == current,
                                role = Role.RadioButton,
                                onClick = { onSelect(language) },
                            ),
                    ) {
                        RadioButton(selected = language == current, onClick = null)
                        Text(
                            text = language.label(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
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
private const val PERCENT = 100

// Degrees of the preview wheel at the full lock
private const val MAX_WHEEL_ROTATION = 120f
private const val MAX_ANGLE_STEP = 5
private const val EXPONENT_STEP = 0.1f
private val MAX_CONTENT_WIDTH = 640.dp

// Ids from app/config/libraries
private val ICON_SETS = setOf("io.tabler:tabler-icons", "com.google:material-design-icons", "com.icons8:icons")

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
                calibrated = true,
                pedalMode = PedalMode.Analog,
                throttleLock = true,
                adsRemoved = false,
            ),
            steering = 0.3f,
            actions = SettingsActions(),
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

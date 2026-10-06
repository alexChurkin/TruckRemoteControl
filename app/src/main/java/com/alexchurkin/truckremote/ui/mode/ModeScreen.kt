package com.alexchurkin.truckremote.ui.mode

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.data.settings.AppMode
import com.alexchurkin.truckremote.ui.theme.TruckRemoteTheme

@get:StringRes
val AppMode.title: Int
    get() = when (this) {
        AppMode.Controller -> R.string.app_mode_controller
        AppMode.Dashboard -> R.string.app_mode_dashboard
    }

@get:StringRes
private val AppMode.summary: Int
    get() = when (this) {
        AppMode.Controller -> R.string.app_mode_controller_summary
        AppMode.Dashboard -> R.string.app_mode_dashboard_summary
    }

@get:DrawableRes
private val AppMode.icon: Int
    get() = when (this) {
        AppMode.Controller -> R.drawable.ic_mode_controller
        AppMode.Dashboard -> R.drawable.ic_dashboard_mode
    }

// The question of the first start: the cards are side by side on a wide screen and one under another on a narrow one
@Composable
fun ModeScreen(onChoose: (AppMode) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_mode_question),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppMode.entries.forEach { mode -> ModeCard(mode, onClick = { onChoose(mode) }) }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_mode_question_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ModeCard(mode: AppMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier.width(300.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(mode.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(text = stringResource(mode.title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(mode.summary),
                // The cards are of the same height
                minLines = 3,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun ModePreview() {
    TruckRemoteTheme { ModeScreen(onChoose = {}) }
}

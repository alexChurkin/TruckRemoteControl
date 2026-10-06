package com.alexchurkin.truckremote.ui.guide

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.ui.theme.TruckRemoteTheme
import kotlinx.coroutines.launch

// A part of the second page: what a control of the screen does
private data class Topic(
    @param:DrawableRes val icon: Int,
    @param:StringRes val title: Int,
    @param:StringRes val text: Int,
)

// What is done on the computer and in the game, in this order
private val Steps = listOf(
    R.string.how_to_run_text,
    R.string.how_to_run_text_2,
    R.string.how_to_run_text_3,
    R.string.how_to_run_text_4,
)

private val Topics = listOf(
    Topic(R.drawable.ic_mode_controller, R.string.section_steering, R.string.how_to_use_text_1),
    Topic(R.drawable.ic_guide_pedals, R.string.how_to_use_text_2, R.string.how_to_use_text_3),
    Topic(R.drawable.ic_guide_actions, R.string.how_to_use_actions, R.string.how_to_use_actions_text),
    Topic(R.drawable.ic_guide_pause, R.string.how_to_use_pause, R.string.how_to_use_pause_text),
    Topic(R.drawable.ic_guide_vibration, R.string.how_to_use_vibration, R.string.how_to_use_vibration_text),
    Topic(R.drawable.ic_dashboard_mode, R.string.how_to_use_dashboard, R.string.how_to_use_dashboard_text),
)

private const val PAGES = 2

// Text isn't wider than it is comfortable to read, also on a tablet and in landscape
private val ContentWidth = 640.dp
private val CardShape = RoundedCornerShape(20.dp)

// The full stop after an address isn't a part of it
private val Link = Regex("https?://\\S*[^\\s.,;:!?)]")

/**
 * Two pages swiped sideways: the steps to start (numbered cards) and the controls of the screen (cards with icons).
 * The bar at the bottom shows the page and goes on; [onDone] is called from the last page.
 */
@Composable
fun GuideScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == PAGES - 1
    val goTo: (Int) -> Unit = { page -> scope.launch { pagerState.animateScrollToPage(page) } }
    // Back returns to the previous page first
    BackHandler(enabled = pagerState.currentPage > 0) { goTo(pagerState.currentPage - 1) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.safeDrawingPadding()) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                if (page == 0) StepsPage() else TopicsPage()
            }
            BottomBar(
                page = pagerState.currentPage,
                last = last,
                onBack = { goTo(pagerState.currentPage - 1) },
                onNext = { if (last) onDone() else goTo(pagerState.currentPage + 1) },
            )
        }
    }
}

@Composable
private fun StepsPage() = Page(title = R.string.how_to_run) {
    Steps.forEachIndexed { index, text ->
        GuideCard {
            Badge { Text(text = (index + 1).toString(), fontWeight = FontWeight.Bold) }
            Text(text = withLinks(stringResource(text)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TopicsPage() = Page(title = R.string.how_to_use) {
    Topics.forEach { topic ->
        GuideCard {
            Badge { Icon(painterResource(topic.icon), contentDescription = null, modifier = Modifier.size(22.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(topic.title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(topic.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.how_to_use_text_4),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
}

// A scrolled page: the title and the cards under it, in the middle of a wide screen
@Composable
private fun Page(@StringRes title: Int, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
                    .semantics { heading() },
            )
            content()
        }
    }
}

// A tonal card: a round badge (a number or an icon) and the text beside it
@Composable
private fun GuideCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { content() }
    }
}

@Composable
private fun Badge(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

// The dots of the pages in the middle, "Back" on the left (not on the first page) and the main button on the right
@Composable
private fun BottomBar(page: Int, last: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (page > 0) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Text(stringResource(R.string.back))
            }
        }
        Row(modifier = Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(PAGES) { PageDot(selected = it == page) }
        }
        Button(onClick = onNext, modifier = Modifier.align(Alignment.CenterEnd)) {
            Text(stringResource(if (last) R.string.guide_done else R.string.guide_next))
        }
    }
}

// Material 3 style: the current page is a wider pill
@Composable
private fun PageDot(selected: Boolean) {
    val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "width")
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "color",
    )
    Spacer(Modifier.size(width, 8.dp).background(color, CircleShape))
}

// Addresses in the text open in the browser
@Composable
private fun withLinks(text: String): AnnotatedString {
    val styles = TextLinkStyles(
        SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    return buildAnnotatedString {
        var from = 0
        Link.findAll(text).forEach { match ->
            append(text.substring(from, match.range.first))
            withLink(LinkAnnotation.Url(match.value, styles)) { append(match.value) }
            from = match.range.last + 1
        }
        append(text.substring(from))
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun GuidePreview() {
    TruckRemoteTheme { GuideScreen(onDone = {}) }
}

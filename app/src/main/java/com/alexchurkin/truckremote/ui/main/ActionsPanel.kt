package com.alexchurkin.truckremote.ui.main

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.data.controller.ControllerAction
import kotlinx.coroutines.launch

private const val COLUMNS = 4
private val ItemWidth = 78.dp
private val ItemHeight = 66.dp
private val ItemMargin = 3.dp
private val PageWidth = (ItemWidth + ItemMargin * 2) * COLUMNS

/**
 * The quick actions panel: pages of buttons (swiped sideways) with page indicators under them.
 * [onClick] and [onHold] return true if the action was sent (the button gives haptic feedback then),
 * [activeActions] are on in the game (e.g. the engine is running).
 */
@Composable
fun ActionsPanel(
    activeActions: Set<ControllerAction>,
    onClick: (ControllerAction) -> Boolean,
    onHold: (ControllerAction, Boolean) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { ActionPages.size }
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .background(colorResource(R.color.actionsPanel), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.width(PageWidth)) { page ->
            ActionGrid(ActionPages[page], activeActions, onClick, onHold)
        }
        PageIndicator(
            pageCount = ActionPages.size,
            currentPage = pagerState.currentPage,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun ActionGrid(
    buttons: List<ActionButton>,
    activeActions: Set<ControllerAction>,
    onClick: (ControllerAction) -> Boolean,
    onHold: (ControllerAction, Boolean) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        buttons.chunked(COLUMNS).forEach { row ->
            Row {
                row.forEach { button ->
                    ActionItem(
                        button = button,
                        active = button.action in activeActions,
                        onClick = onClick,
                        onHold = onHold,
                        modifier = Modifier.padding(ItemMargin),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionItem(
    button: ActionButton,
    active: Boolean,
    onClick: (ControllerAction) -> Boolean,
    onHold: (ControllerAction, Boolean) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val clickPressed by interactionSource.collectIsPressedAsState()
    var held by remember { mutableStateOf(false) }
    val background = colorResource(
        when {
            held || clickPressed -> R.color.actionItemPressed
            active -> R.color.actionItemActive
            else -> R.color.actionItem
        },
    )
    val action = button.action
    val input = if (action.isHold) {
        // Held while pressed; a swipe to another page cancels the press and releases the action
        Modifier.pointerInput(action) {
            detectTapGestures(
                onPress = {
                    if (onHold(action, true)) {
                        held = true
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        tryAwaitRelease()
                        onHold(action, false)
                        held = false
                    }
                },
            )
        }
    } else {
        Modifier.clickable(interactionSource = interactionSource, indication = null, role = Role.Button) {
            if (onClick(action)) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
    Column(
        modifier = modifier
            .size(ItemWidth, ItemHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(animateColorAsState(background, label = "background").value)
            .then(input)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(button.icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(2.dp))
        // Long labels (e.g. in Russian) take two lines under the icon
        BasicText(
            text = stringResource(button.label),
            style = TextStyle(color = Color.White, textAlign = TextAlign.Center, lineHeight = 13.sp),
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
        )
    }
}

// Material 3 style: the current page is a wider pill, the others are dots
@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(pageCount) { page ->
            val selected = page == currentPage
            val width by animateDpAsState(if (selected) 18.dp else 6.dp, label = "width")
            val color by animateColorAsState(
                if (selected) Color.White else Color.White.copy(alpha = 0.38f),
                label = "color",
            )
            val description = stringResource(R.string.actions_page, page + 1, pageCount)
            // The touch target is bigger than the dot
            Box(
                modifier = Modifier
                    .clickable(role = Role.Tab) { onSelect(page) }
                    .padding(vertical = 4.dp)
                    .semantics { contentDescription = description },
            ) {
                Box(Modifier.size(width, 6.dp).background(color, RoundedCornerShape(3.dp)))
            }
        }
    }
}

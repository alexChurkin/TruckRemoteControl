package com.alexchurkin.truckremote.ui.main

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.data.controller.ControllerAction
import com.alexchurkin.truckremote.data.settings.ActionLayout
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COLUMNS = 4
private const val ROWS = ActionLayout.SLOTS / COLUMNS

// A bit wider than high: the panel has the place of the middle controls it hides, up to the blinkers
private val ItemWidth = 90.dp
private val ItemHeight = 76.dp
private val ItemMargin = 3.dp
private val CellWidth = ItemWidth + ItemMargin * 2
private val CellHeight = ItemHeight + ItemMargin * 2
private val PageWidth = CellWidth * COLUMNS
private val GridHeight = CellHeight * ROWS
private val ItemCorner = 10.dp
private val ItemShape = RoundedCornerShape(ItemCorner)

// The answer of a button to a press (see PressFeedback)
private const val PRESSED_SCALE = 0.9f
private const val SPRING_DAMPING = 0.4f
private const val HOLD_MS = 80
private const val FLASH_MS = 450
private const val FLASH_WHITE = 0.3f
private const val RING_ALPHA = 0.7f
private val RingGrow = 6.dp
private val RingWidth = 2.dp

// The pager clips its pages, so it is bigger than a page by this at every side: the ring of the buttons at the
// edges is drawn there, over the margin and the padding of the panel
private val PageBleed = RingGrow + RingWidth / 2
private const val SHAKE_MS = 300
private const val SHAKE_DP = 6f
private val SHAKE_STEPS_DP = listOf(SHAKE_DP, -SHAKE_DP, SHAKE_DP / 2)

// A dragged button held at a side of the panel for this long turns the page
private val PageEdge = 28.dp
private const val PAGE_TURN_DELAY_MS = 600L
private const val DRAGGED_SCALE = 1.08f

// A button lifted by a long press: it follows the finger, also over the pages, and the others make room for it
private class Drag(val action: ControllerAction, val from: Int, val grab: Offset, pointer: Offset) {
    var pointer by mutableStateOf(pointer)
}

// Moving the buttons by dragging: what is lifted, where it would be dropped and the layout shown meanwhile.
// Places are counted through all pages (see ActionLayout.moved), [cell] is the size of a place in pixels
private class Reorder(private val pagerState: PagerState, val cell: Size) {
    var drag by mutableStateOf<Drag?>(null)
        private set

    // The layout as it was dropped, shown until the saved one comes: the buttons don't jump back for a moment
    var dropped by mutableStateOf<ActionLayout?>(null)

    private val grid = Size(cell.width * COLUMNS, cell.height * ROWS)

    // The place under a point of the shown page
    fun placeAt(point: Offset): Int {
        val column = (point.x / cell.width).toInt().coerceIn(0, COLUMNS - 1)
        val row = (point.y / cell.height).toInt().coerceIn(0, ROWS - 1)
        return pagerState.currentPage * ActionLayout.SLOTS + row * COLUMNS + column
    }

    // While a button is dragged, the layout is shown as if it were dropped where it is
    fun shown(layout: ActionLayout): ActionLayout =
        drag?.let { layout.moved(it.from, placeAt(it.pointer)) } ?: dropped ?: layout

    // Returns false if the place is empty: nothing is lifted
    fun lift(layout: ActionLayout, point: Offset): Boolean {
        val from = placeAt(point)
        val slot = from % ActionLayout.SLOTS
        val corner = Offset(slot % COLUMNS * cell.width, slot / COLUMNS * cell.height)
        drag = layout.pages.flatten()[from]?.let { Drag(it, from, grab = point - corner, pointer = point) }
        return drag != null
    }

    fun drop(layout: ActionLayout, onLayoutChange: (ActionLayout) -> Unit) {
        val lifted = drag ?: return
        val moved = layout.moved(lifted.from, placeAt(lifted.pointer))
        if (moved != layout) {
            dropped = moved
            onLayoutChange(moved)
        }
    }

    fun end() {
        drag = null
    }

    // Where the pages turn while the dragged button is held at a side of the panel: -1, 1 or 0
    fun turn(edge: Float): Int {
        val x = drag?.pointer?.x ?: return 0
        return if (x < edge) {
            -1
        } else if (x > grid.width - edge) {
            1
        } else {
            0
        }
    }

    // The top left corner of the dragged button, kept inside the pages
    fun corner(lifted: Drag): IntOffset {
        val corner = lifted.pointer - lifted.grab
        return IntOffset(
            corner.x.coerceIn(0f, grid.width - cell.width).roundToInt(),
            corner.y.coerceIn(0f, grid.height - cell.height).roundToInt(),
        )
    }
}

/**
 * The quick actions panel: pages of buttons (swiped sideways) with page indicators under them.
 * [onClick] and [onHold] return true if the action was sent (the button gives haptic feedback then),
 * [activeActions] are on in the game (e.g. the engine is running), [badges] are small texts on buttons (e.g. "2/4").
 * A long press on a button (or on an empty place) starts editing the [layout]. A button held so is lifted and can be
 * dragged to another place as an icon of a launcher: the other buttons make room for it and a side of the panel
 * turns the page. A tapped place shows all actions to choose from. Every change goes to [onLayoutChange].
 */
@Composable
fun ActionsPanel(
    layout: ActionLayout,
    activeActions: Set<ControllerAction>,
    badges: Map<ControllerAction, String>,
    onClick: (ControllerAction) -> Boolean,
    onHold: (ControllerAction, Boolean) -> Boolean,
    onLayoutChange: (ActionLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { ActionLayout.PAGES }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Place?>(null) }
    val target = picking
    val stopPicking = {
        picking = null
        if (target != null) scope.launch { pagerState.scrollToPage(target.page) }
    }

    Column(
        modifier = modifier
            .background(colorResource(R.color.actionsPanel), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (editing) {
            BasicText(
                text = stringResource(if (target != null) R.string.actions_pick_hint else R.string.actions_edit_hint),
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.width(PageWidth).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (target != null) {
            // All actions in the default order, the empty place among them
            HorizontalPager(state = pagerState, modifier = Modifier.size(PageWidth, GridHeight)) { page ->
                PickerGrid(
                    choices = ActionLayout.Default.pages[page],
                    current = layout.pages[target.page][target.slot],
                    onPick = { action ->
                        onLayoutChange(layout.with(target.page, target.slot, action))
                        stopPicking()
                    },
                )
            }
        } else {
            ReorderablePages(
                pagerState = pagerState,
                layout = layout,
                editing = editing,
                onEditingStart = { editing = true },
                onLayoutChange = onLayoutChange,
            ) { page, slots, dragged ->
                ActionGrid(
                    slots = slots,
                    dragged = dragged,
                    activeActions = activeActions,
                    badges = badges,
                    editing = editing,
                    onClick = onClick,
                    onHold = onHold,
                    onEdit = { slot -> picking = Place(page, slot) },
                )
            }
        }
        Box(modifier = Modifier.width(PageWidth), contentAlignment = Alignment.Center) {
            PageIndicator(
                pageCount = ActionLayout.PAGES,
                currentPage = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
            if (editing && target != null) {
                PanelTextButton(
                    text = stringResource(R.string.actions_pick_cancel),
                    onClick = { stopPicking() },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            } else if (editing) {
                PanelTextButton(
                    text = stringResource(R.string.actions_edit_reset),
                    onClick = { onLayoutChange(ActionLayout.Default) },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                PanelTextButton(
                    text = stringResource(R.string.actions_edit_done),
                    onClick = { editing = false },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

/**
 * The pages of the panel whose buttons can be moved by dragging. [page] draws the buttons of a page: it gets
 * the number of the page, what to show in its places (while a button is dragged, the others have already made room
 * for it) and the dragged action, whose place is drawn empty: the button itself is drawn here, under the finger.
 * A long press calls [onEditingStart].
 */
@Composable
private fun ReorderablePages(
    pagerState: PagerState,
    layout: ActionLayout,
    editing: Boolean,
    onEditingStart: () -> Unit,
    onLayoutChange: (ActionLayout) -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable (page: Int, slots: List<ControllerAction?>, dragged: ControllerAction?) -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val reorder = remember(pagerState, density) {
        Reorder(pagerState, with(density) { Size(CellWidth.toPx(), CellHeight.toPx()) })
    }
    LaunchedEffect(layout) { reorder.dropped = null }

    // A dragged button held at a side of the panel turns the pages one by one
    val turn = reorder.turn(with(density) { PageEdge.toPx() })
    LaunchedEffect(turn) {
        var next = pagerState.currentPage + turn
        while (turn != 0 && next in 0 until ActionLayout.PAGES) {
            delay(PAGE_TURN_DELAY_MS)
            pagerState.animateScrollToPage(next)
            next += turn
        }
    }

    // The gesture outlives the recompositions it causes, so it reads the current values through these
    val currentLayout by rememberUpdatedState(reorder.dropped ?: layout)
    val currentEditing by rememberUpdatedState(editing)
    val currentOnEditingStart by rememberUpdatedState(onEditingStart)
    val currentOnLayoutChange by rememberUpdatedState(onLayoutChange)

    val dragged = reorder.drag
    Box(
        modifier = modifier
            .size(PageWidth, GridHeight)
            .pointerInput(reorder) {
                detectLiftAndDrag(
                    // A hold action is held while it's pressed, so a long press doesn't lift it
                    canLift = { point ->
                        currentEditing || currentLayout.pages.flatten()[reorder.placeAt(point)]?.isHold != true
                    },
                    onLift = { point ->
                        currentOnEditingStart()
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        reorder.lift(currentLayout, point)
                    },
                    onDrag = { point -> reorder.drag?.pointer = point },
                    onDrop = { reorder.drop(currentLayout, currentOnLayoutChange) },
                    onEnd = { reorder.end() },
                )
            },
    ) {
        val shown = reorder.shown(layout)
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = dragged == null,
            modifier = Modifier.requiredSize(PageWidth + PageBleed * 2, GridHeight + PageBleed * 2),
        ) { number ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                page(number, shown.pages[number], dragged?.action)
            }
        }
        if (dragged != null) {
            // Over the pages: it stays under the finger while a page is turned
            ActionTile(
                button = dragged.action.button(),
                background = colorResource(R.color.actionItemPressed),
                modifier = Modifier
                    .offset { reorder.corner(dragged) }
                    .padding(ItemMargin)
                    .scale(DRAGGED_SCALE)
                    .clip(ItemShape),
            )
        }
    }
}

private data class Place(val page: Int, val slot: Int)

/**
 * A long press lifts what is under the finger, then the finger drags it: as icons are moved in a launcher.
 * [canLift] tells if the gesture may start at the point at all, [onLift] is called after the long press and returns
 * false if nothing is lifted there; then [onDrag] gets the points of the finger, [onDrop] is called when it's
 * released, and [onEnd] in any case (also if the gesture was broken).
 * A swipe isn't a long press, so the pager under the finger still turns its pages.
 */
private suspend fun PointerInputScope.detectLiftAndDrag(
    canLift: (Offset) -> Boolean,
    onLift: (Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onEnd: () -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (!canLift(down.position)) return@awaitEachGesture
    val pressed = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
    if (!onLift(pressed.position)) return@awaitEachGesture
    try {
        // Before the pager and the buttons see the moves and the release (they would scroll and click)
        var change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
        while (change != null && change.pressed) {
            change.consume()
            onDrag(change.position)
            change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
        }
        if (change != null) {
            change.consume()
            onDrop()
        }
    } finally {
        onEnd()
    }
}

// The buttons of a page at their places. A button that gets another place (the layout was changed, or the others
// make room for the dragged one) slides there; the place of the [dragged] button is only outlined
@Composable
private fun ActionGrid(
    slots: List<ControllerAction?>,
    dragged: ControllerAction?,
    activeActions: Set<ControllerAction>,
    badges: Map<ControllerAction, String>,
    editing: Boolean,
    onClick: (ControllerAction) -> Boolean,
    onHold: (ControllerAction, Boolean) -> Boolean,
    onEdit: (slot: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cell = with(LocalDensity.current) { IntSize(CellWidth.roundToPx(), CellHeight.roundToPx()) }
    Box(modifier.size(PageWidth, GridHeight)) {
        slots.forEachIndexed { slot, action ->
            val place = IntOffset(slot % COLUMNS * cell.width, slot / COLUMNS * cell.height)
            val itemModifier = Modifier.padding(ItemMargin)
            if (action == null) {
                // An empty place is invisible until the layout is edited
                if (editing) {
                    Box(
                        Modifier.offset {
                            place
                        },
                    ) { EditItem(null, onEdit = { onEdit(slot) }, modifier = itemModifier) }
                }
            } else {
                key(action) {
                    val offset by animateIntOffsetAsState(place, label = "place")
                    Box(Modifier.offset { offset }) {
                        when {
                            action == dragged -> DropPlace(itemModifier)

                            editing -> EditItem(action, onEdit = { onEdit(slot) }, modifier = itemModifier)

                            else -> ActionItem(
                                button = action.button(),
                                active = action in activeActions,
                                badge = badges[action],
                                onClick = onClick,
                                onHold = onHold,
                                modifier = itemModifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

// Where the dragged button will be dropped
@Composable
private fun DropPlace(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(ItemWidth, ItemHeight)
            .border(1.dp, Color.White.copy(alpha = 0.6f), ItemShape),
    )
}

@Composable
private fun PickerGrid(
    choices: List<ControllerAction?>,
    current: ControllerAction?,
    onPick: (ControllerAction?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        choices.chunked(COLUMNS).forEach { row ->
            Row {
                row.forEach { action ->
                    val description = action?.let { stringResource(it.button().label) }
                        ?: stringResource(R.string.actions_empty)
                    ActionTile(
                        button = action?.button(),
                        background = colorResource(
                            if (action ==
                                current
                            ) {
                                R.color.actionItemActive
                            } else {
                                R.color.actionItem
                            },
                        ),
                        modifier = Modifier
                            .padding(ItemMargin)
                            .clip(ItemShape)
                            .clickable(role = Role.Button) { onPick(action) }
                            .semantics { contentDescription = description },
                    )
                }
            }
        }
    }
}

/*
 * A button of the panel. Most actions show nothing in the game state (a camera, the map, a gear), so every press
 * that reached the server answers on the button itself: it springs, flashes and sends a ring outwards. A press
 * that couldn't be sent (no connection, paused) shakes the button instead. A hold action stays pushed in while held.
 */
@Composable
private fun ActionItem(
    button: ActionButton,
    active: Boolean,
    badge: String?,
    onClick: (ControllerAction) -> Boolean,
    onHold: (ControllerAction, Boolean) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val feedback = rememberPressFeedback()
    var held by remember { mutableStateOf(false) }
    val base = colorResource(if (active) R.color.actionItemActive else R.color.actionItem)
    val pressed = colorResource(R.color.actionItemPressed)
    val background by animateColorAsState(if (held) pressed else base, label = "background")
    val action = button.action
    val input = if (action.isHold) {
        // Held while pressed (so a long press doesn't lift it); a swipe to another page releases the action
        Modifier.pointerInput(action) {
            detectTapGestures(
                onPress = {
                    if (onHold(action, true)) {
                        held = true
                        feedback.hold()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        tryAwaitRelease()
                        onHold(action, false)
                        held = false
                        feedback.release()
                    } else {
                        feedback.refuse()
                    }
                },
            )
        }
    } else {
        // A long press is taken by the panel: it lifts the button to move it
        Modifier.clickable(interactionSource = null, indication = null, role = Role.Button) {
            if (onClick(action)) {
                feedback.confirm()
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            } else {
                feedback.refuse()
            }
        }
    }
    Box(modifier.pressFeedback(feedback)) {
        ActionTile(
            button = button,
            background = lerp(background, Color.White, feedback.flash.value * FLASH_WHITE),
            modifier = Modifier.clip(ItemShape).then(input),
        )
        if (badge != null) {
            BasicText(
                text = badge,
                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 6.dp),
            )
        }
    }
}

// The animations of a button's answer to a press
@Stable
private class PressFeedback(private val scope: CoroutineScope) {
    val scale = Animatable(1f)

    // 1 right after the press, fades to 0
    val flash = Animatable(0f)

    // Horizontal shift of a refused press, dp
    val shake = Animatable(0f)

    fun confirm() {
        scope.launch {
            scale.snapTo(PRESSED_SCALE)
            scale.animateTo(1f, spring(dampingRatio = SPRING_DAMPING, stiffness = Spring.StiffnessMedium))
        }
        flashOnce()
    }

    fun hold() {
        scope.launch { scale.animateTo(PRESSED_SCALE, tween(HOLD_MS)) }
        flashOnce()
    }

    fun release() {
        scope.launch { scale.animateTo(1f, spring(dampingRatio = SPRING_DAMPING, stiffness = Spring.StiffnessMedium)) }
    }

    fun refuse() {
        scope.launch {
            shake.snapTo(0f)
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = SHAKE_MS
                    // Right, left, a smaller right and back
                    val step = SHAKE_MS / (SHAKE_STEPS_DP.size + 1)
                    SHAKE_STEPS_DP.forEachIndexed { index, dp -> dp at step * (index + 1) }
                },
            )
        }
    }

    private fun flashOnce() {
        scope.launch {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(FLASH_MS, easing = LinearOutSlowInEasing))
        }
    }
}

@Composable
private fun rememberPressFeedback(): PressFeedback {
    val scope = rememberCoroutineScope()
    return remember(scope) { PressFeedback(scope) }
}

// The spring and the shake move the button, the ring goes outwards from its edge while the flash fades
private fun Modifier.pressFeedback(feedback: PressFeedback) = this
    .graphicsLayer {
        scaleX = feedback.scale.value
        scaleY = feedback.scale.value
        translationX = feedback.shake.value.dp.toPx()
    }
    .drawWithContent {
        drawContent()
        val flash = feedback.flash.value
        if (flash <= 0f) return@drawWithContent
        val grow = RingGrow.toPx() * (1 - flash)
        val corner = ItemCorner.toPx() + grow
        drawRoundRect(
            color = Color.White.copy(alpha = flash * RING_ALPHA),
            topLeft = Offset(-grow, -grow),
            size = Size(size.width + grow * 2, size.height + grow * 2),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = RingWidth.toPx()),
        )
    }

@Composable
private fun EditItem(action: ControllerAction?, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val label = action?.let { stringResource(it.button().label) } ?: stringResource(R.string.actions_empty)
    val description = stringResource(R.string.actions_edit_place, label)
    ActionTile(
        button = action?.button(),
        background = if (action != null) colorResource(R.color.actionItem) else Color.Transparent,
        modifier = modifier
            .clip(ItemShape)
            .border(1.dp, Color.White.copy(alpha = 0.6f), ItemShape)
            .clickable(role = Role.Button, onClick = onEdit)
            .semantics { contentDescription = description },
    )
}

// The icon with the label under it; null button: an empty place
@Composable
private fun ActionTile(button: ActionButton?, background: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .size(ItemWidth, ItemHeight)
            .background(background)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (button == null) {
            BasicText(text = "+", style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 24.sp))
            return@Column
        }
        Image(
            painter = painterResource(button.icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(4.dp))
        // Long labels (e.g. in Russian) take two lines under the icon
        BasicText(
            text = stringResource(button.label),
            style = TextStyle(color = Color.White, textAlign = TextAlign.Center, lineHeight = 14.sp),
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp, stepSize = 0.5.sp),
        )
    }
}

@Composable
private fun PanelTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
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

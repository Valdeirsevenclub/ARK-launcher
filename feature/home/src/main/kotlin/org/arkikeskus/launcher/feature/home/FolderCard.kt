package org.arkikeskus.launcher.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.AppActionPopup
import org.arkikeskus.launcher.ui.AppActions
import org.arkikeskus.launcher.ui.AppShortcuts
import org.arkikeskus.launcher.ui.DragSource
import org.arkikeskus.launcher.ui.HomeDragController
import org.arkikeskus.launcher.ui.IconPress
import org.arkikeskus.launcher.ui.IconPressOutcome
import org.arkikeskus.launcher.ui.LauncherIcons
import org.arkikeskus.launcher.ui.PopupAction
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.LocalAppLabelScale
import org.arkikeskus.launcher.ui.component.LocalIconScale
import org.arkikeskus.launcher.ui.component.iconSizeForCell
import org.arkikeskus.launcher.ui.component.labelBlockHeight
import org.arkikeskus.launcher.ui.component.labelFontFactor
import kotlin.math.abs
import kotlin.math.roundToInt

private const val FOLDER_COLUMNS = 4

/**
 * The open folder: a card in the home screen's own window (not a sheet in a window of its own, so a
 * drag can continue onto the home surfaces) over a scrim that closes it when tapped. Inside: the
 * name, editable in place, and the apps in a four-column grid.
 *
 * One gesture detector on the grid's container owns every icon touch — the icons publish their
 * bounds and the container hit-tests the DOWN. A tap launches the app and closes the folder; a
 * still hold lifts the icon into the shared [HomeDragController] (haptic) and, released still, opens
 * the same menu as on the home screen. On a locked desktop a hold does nothing, as on every other
 * surface. The press phase follows the drawer's rules: a vertical pull before the hold completes is
 * the grid's scroll and is never consumed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderCard(
    folder: PlacedFolder,
    badges: Map<String, Int>,
    badgeShowCount: Boolean,
    badgeScale: Float,
    locked: Boolean,
    dragController: HomeDragController,
    onRename: (String) -> Unit,
    onAppClick: (AppItem) -> Unit,
    onRenameApp: (AppItem) -> Unit,
    onRemoveFromFolder: (AppItem) -> Unit,
    onPinShortcut: (AppShortcuts.Item) -> Unit,
    /** Release inside the grid at a new slot: [before] is the visible app that will follow, null = last. */
    onReorder: (app: AppItem, before: AppItem?) -> Unit,
    onDropOnHome: (app: AppItem, page: Int, cellX: Int, cellY: Int) -> Unit,
    onDropOnDock: (AppItem) -> Unit,
    onDropOnRemove: (AppItem) -> Unit,
    /** The gesture that left the card has ended (whatever the drop was): the host closes the folder. */
    onDragOutEnd: () -> Unit,
    onDismiss: () -> Unit,
    /** The menu's Widgets row (an app with home-screen widgets); null hides it, e.g. a locked desktop. */
    onWidgets: ((AppItem) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val iconScale = LocalIconScale.current
    val labelScale = LocalAppLabelScale.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height

    val currentOnAppClick by rememberUpdatedState(onAppClick)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    // The long-pressed app and its icon's root bounds, for the menu's anchor.
    var menu by remember { mutableStateOf<Pair<AppItem, Rect>?>(null) }
    // Root-space bounds of each icon (by app key) and of the grid container, for the DOWN hit-test.
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var gridWidthPx by remember { mutableIntStateOf(0) }
    // An icon is lifted: the grid stops scrolling until the gesture ends.
    var lifted by remember { mutableStateOf(false) }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("folderScrim")
            .background(Color.Black.copy(alpha = 0.5f))
            // The scrim swallows every touch under it: nothing reaches the home grid behind.
            .pointerInput(Unit) { detectTapGestures { currentOnDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .testTag("folderCard")
                .semantics { dismiss { currentOnDismiss(); true } }
                // A tap in the card's own padding closes nothing (and the consumed release keeps
                // the scrim's tap from firing for a press that began inside the card).
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                var name by remember(folder.id) { mutableStateOf(folder.name) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; onRename(it) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.folder_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                // The icon follows the user's icon-size setting, capped by the slot like the drawer.
                val nominalIconSize = 56.dp * iconScale
                val iconSize = if (gridWidthPx > 0) {
                    iconSizeForCell(with(density) { (gridWidthPx.toFloat() / FOLDER_COLUMNS).toDp() }, nominalIconSize)
                } else {
                    nominalIconSize
                }
                val rowHeight = iconSize +
                    labelBlockHeight(showLabel = true, labelScale = labelScale, fontFactor = labelFontFactor(density.fontScale), lines = 2) +
                    20.dp
                // Up to four rows without scrolling when the screen allows; taller folders scroll.
                val gridMaxHeight: Dp = min(rowHeight * 4, with(density) { windowHeightPx.toDp() } * 0.6f)
                val gridState = rememberLazyGridState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folderGrid")
                        .onGloballyPositioned { gridCoords = it; gridWidthPx = it.size.width }
                        .pointerInput(locked) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val coords = gridCoords ?: return@awaitEachGesture
                                val rootDown = coords.localToRoot(down.position)
                                val hit = itemBounds.entries.firstOrNull { it.value.contains(rootDown) } ?: return@awaitEachGesture
                                val app = folder.apps.firstOrNull { it.key == hit.key } ?: return@awaitEachGesture
                                val itemRect = hit.value
                                val outcome = awaitFolderPress(down, itemRect) { coords.localToRoot(it) }
                                val holdPos = when (outcome) {
                                    IconPressOutcome.Tap -> {
                                        currentOnAppClick(app)
                                        currentOnDismiss()
                                        return@awaitEachGesture
                                    }
                                    IconPressOutcome.Cancelled -> return@awaitEachGesture
                                    is IconPressOutcome.Held -> outcome.position
                                }
                                // Desktop locked: a hold does nothing (no lift, no menu); a tap still launches.
                                if (locked) return@awaitEachGesture
                                val token = Any()
                                if (!dragController.start(app, DragSource.Folder, coords.localToRoot(holdPos), token)) {
                                    return@awaitEachGesture
                                }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                lifted = true
                                try {
                                    val completed = drag(down.id) { change -> change.consume() }
                                    // Nothing on release once HOME cancelled the lift.
                                    val live = completed && dragController.owns(token)
                                    if (live && !dragController.moving) menu = app to itemRect
                                } finally {
                                    dragController.stop(token)
                                    lifted = false
                                }
                            }
                        },
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(FOLDER_COLUMNS),
                        state = gridState,
                        userScrollEnabled = !lifted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = gridMaxHeight),
                    ) {
                        items(items = folder.apps, key = { it.key }) { app ->
                            AppIcon(
                                appItem = app,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                iconSize = iconSize,
                                showLabel = true,
                                maxLabelLines = 2,
                                badgeCount = badges[app.badgeKey] ?: 0,
                                badgeShowCount = badgeShowCount,
                                badgeScale = badgeScale,
                                modifier = Modifier
                                    .onGloballyPositioned { itemBounds[app.key] = it.boundsInRoot() }
                                    // The pointer gesture lives on the container; these keep the
                                    // accessibility actions a clickable icon would have had.
                                    .semantics(mergeDescendants = true) {
                                        onClick { currentOnAppClick(app); currentOnDismiss(); true }
                                        if (!locked) onLongClick { menu = app to (itemBounds[app.key] ?: Rect.Zero); true }
                                    }
                                    .animateItem()
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    menu?.let { (app, bounds) ->
        // Anchored like the drawer's menu: below an icon in the upper half of the window, above one
        // lower down, at the icon's edge so the card clears it with a small gap.
        val preferAbove = bounds.center.y > windowHeightPx / 2
        val anchorY = (if (preferAbove) bounds.top else bounds.bottom).roundToInt()
        AppActionPopup(
            app = app,
            anchor = IntOffset(bounds.center.x.roundToInt(), anchorY),
            preferAbove = preferAbove,
            actions = listOf(
                PopupAction(stringResource(R.string.app_info), LauncherIcons.Info) { AppActions.openAppInfo(context, app) },
                PopupAction(stringResource(R.string.rename), LauncherIcons.Edit) { onRenameApp(app) },
                PopupAction(stringResource(R.string.folder_remove_app), LauncherIcons.Close) { onRemoveFromFolder(app) },
                PopupAction(stringResource(R.string.uninstall), LauncherIcons.Delete) { AppActions.uninstall(context, app) },
            ),
            onDismiss = { menu = null },
            onPinShortcut = onPinShortcut,
            onWidgets = onWidgets?.let { { it(app) } },
        )
    }
}

/**
 * The press phase of a folder icon after its [down], run by the grid's container: the drawer's
 * rules. The moves are never consumed, so a vertical pull past the slop before the hold completes
 * is the grid's scroll (the grid consumes it and the press ends as [IconPressOutcome.Cancelled]);
 * a sideways roll only ends the hold once the finger leaves the icon ([itemRect], root space, plus
 * the slop). A clean release is a [IconPressOutcome.Tap]; the timeout a [IconPressOutcome.Held].
 */
private suspend fun AwaitPointerEventScope.awaitFolderPress(
    down: PointerInputChange,
    itemRect: Rect,
    toRoot: (Offset) -> Offset,
): IconPressOutcome {
    val slop = viewConfiguration.touchSlop
    val itemSize = IntSize(itemRect.width.roundToInt(), itemRect.height.roundToInt())
    var holdPos = down.position
    val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var ended: IconPressOutcome? = null
        while (ended == null) {
            val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
            ended = when {
                c == null || c.isConsumed ||
                    abs(c.position.y - down.position.y) > slop ||
                    !IconPress.staysOnIcon(toRoot(c.position) - itemRect.topLeft, itemSize, slop) ->
                    IconPressOutcome.Cancelled
                !c.pressed -> {
                    c.consume()
                    IconPressOutcome.Tap
                }
                else -> {
                    holdPos = c.position
                    null
                }
            }
        }
        ended
    }
    return ended ?: IconPressOutcome.Held(holdPos)
}

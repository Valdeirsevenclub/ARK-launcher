package org.arkikeskus.launcher.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.AppActionPopup
import org.arkikeskus.launcher.ui.AppActions
import org.arkikeskus.launcher.ui.AppShortcuts
import org.arkikeskus.launcher.ui.LauncherIcons
import org.arkikeskus.launcher.ui.PopupAction
import org.arkikeskus.launcher.ui.component.AppIcon
import kotlin.math.roundToInt

/**
 * The open folder: its name, editable in place, and its apps. A tap launches the app and closes
 * the folder; a long-press opens the same menu as on the home screen — app info, rename, remove
 * from folder, uninstall, plus the app's shortcuts — and leaves the app where it is. Taking an app
 * out is one of the menu's actions, no longer the long-press itself: that made the app jump out of
 * the folder on every long-press and come back at the end of it.
 *
 * The menu is composed inside the sheet: the sheet is a window above the home screen, so a popup
 * from the home screen's composition could never show over it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun FolderSheet(
    folder: PlacedFolder,
    badges: Map<String, Int>,
    badgeShowCount: Boolean,
    badgeScale: Float,
    onRename: (String) -> Unit,
    onAppClick: (AppItem) -> Unit,
    onRenameApp: (AppItem) -> Unit,
    onRemoveFromFolder: (AppItem) -> Unit,
    onPinShortcut: (AppShortcuts.Item) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // The long-pressed app and its icon's bounds in the sheet's window, for the menu's anchor.
    var menu by remember { mutableStateOf<Pair<AppItem, Rect>?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            var name by remember(folder.id) { mutableStateOf(folder.name) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; onRename(it) },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                items(items = folder.apps, key = { it.key }) { app ->
                    var bounds by remember { mutableStateOf(Rect.Zero) }
                    AppIcon(
                        appItem = app,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        showLabel = true,
                        maxLabelLines = 2,
                        badgeCount = badges[app.badgeKey] ?: 0,
                        badgeShowCount = badgeShowCount,
                        badgeScale = badgeScale,
                        modifier = Modifier
                            .onGloballyPositioned { bounds = it.boundsInRoot() }
                            .combinedClickable(
                                onClick = {
                                    onAppClick(app)
                                    onDismiss()
                                },
                                onLongClick = { menu = app to bounds },
                            )
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                }
            }
        }
        menu?.let { (app, bounds) ->
            // Anchored like the drawer's menu: below an icon in the upper half of the window, above
            // one lower down, at the icon's edge so the card clears it with a small gap.
            val windowHeightPx = LocalWindowInfo.current.containerSize.height
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
            )
        }
    }
}

package org.arkikeskus.launcher.feature.home

import android.app.Application
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import org.arkikeskus.launcher.designsystem.theme.LauncherTheme
import org.arkikeskus.launcher.model.AppItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The open folder. A long-press on an app opens the same menu as on the home screen — app info,
 * rename, remove from folder, uninstall — and leaves the app where it is; taking it out of the
 * folder is one of the menu's actions, no longer the long-press itself (a report: the app jumped
 * out of the folder on every long-press and came back at the end).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class FolderSheetTest {
    @get:Rule val compose = createComposeRule()

    private val user = Process.myUserHandle()
    private val mail = AppItem(RuntimeEnvironment.getApplication().packageName, "Mail", user, 0L, "Mail")
    private val maps = AppItem("com.maps", "Main", user, 0L, "Maps")
    private val folder = PlacedFolder(id = 7, name = "Tools", apps = listOf(mail, maps), page = 0, cellX = 0, cellY = 0)

    private val launched = mutableListOf<AppItem>()
    private val renamed = mutableListOf<AppItem>()
    private val removed = mutableListOf<AppItem>()
    private var dismissed = 0

    private fun show() {
        compose.setContent {
            LauncherTheme {
                FolderSheet(
                    folder = folder,
                    badges = emptyMap(),
                    badgeShowCount = false,
                    badgeScale = 1f,
                    onRename = {},
                    onAppClick = { launched += it },
                    onRenameApp = { renamed += it },
                    onRemoveFromFolder = { removed += it },
                    onPinShortcut = {},
                    onDismiss = { dismissed++ },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun longPress(label: String) {
        compose.onNodeWithText(label).performTouchInput { longClick() }
        compose.waitForIdle()
    }

    @Test fun aLongPressOpensTheAppsMenuAndLeavesItInTheFolder() {
        show()

        longPress("Mail")

        compose.onNodeWithText("App info").assertIsDisplayed()
        compose.onNodeWithText("Rename").assertIsDisplayed()
        compose.onNodeWithText("Remove from folder").assertIsDisplayed()
        compose.onNodeWithText("Uninstall").assertIsDisplayed()
        assertThat(removed).isEmpty()
        assertThat(launched).isEmpty()
        assertThat(dismissed).isEqualTo(0)
    }

    @Test fun removeFromFolderIsAMenuAction() {
        show()
        longPress("Mail")

        compose.onNodeWithText("Remove from folder").performClick()
        compose.waitForIdle()

        assertThat(removed).containsExactly(mail)
    }

    @Test fun renameIsAMenuAction() {
        show()
        longPress("Maps")

        compose.onNodeWithText("Rename").performClick()
        compose.waitForIdle()

        assertThat(renamed).containsExactly(maps)
        assertThat(removed).isEmpty()
    }

    @Test fun aTapStillLaunchesTheAppAndClosesTheFolder() {
        show()

        compose.onNodeWithText("Maps").performClick()
        compose.waitForIdle()

        assertThat(launched).containsExactly(maps)
        assertThat(dismissed).isEqualTo(1)
    }
}

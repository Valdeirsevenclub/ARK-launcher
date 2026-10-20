package org.arkikeskus.launcher.feature.home

import android.app.Application
import android.os.Process
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import com.google.common.truth.Truth.assertThat
import org.arkikeskus.launcher.designsystem.theme.LauncherTheme
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.HomeDragController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The open folder as a card in the home window. A tap launches and closes; a still long-press lifts
 * the icon and, released still, opens the same menu as on the home screen; a locked desktop ignores
 * the long-press altogether; the scrim and Back close the card; the semantics actions keep working
 * for users who cannot make the pointer gesture.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class FolderCardTest {
    @get:Rule val compose = createComposeRule()

    private val user = Process.myUserHandle()
    private val mail = AppItem(RuntimeEnvironment.getApplication().packageName, "Mail", user, 0L, "Mail")
    private val maps = AppItem("com.maps", "Main", user, 0L, "Maps")
    private val notes = AppItem("com.notes", "Main", user, 0L, "Notes")
    private var folderState by mutableStateOf(
        PlacedFolder(id = 7, name = "Tools", apps = listOf(mail, maps, notes), page = 0, cellX = 0, cellY = 0),
    )
    private val drag = HomeDragController()

    private val launched = mutableListOf<AppItem>()
    private val renamed = mutableListOf<AppItem>()
    private val removed = mutableListOf<AppItem>()
    private val reorders = mutableListOf<Pair<AppItem, AppItem?>>()
    private val homeDrops = mutableListOf<Pair<AppItem, Triple<Int, Int, Int>>>()
    private val dockDrops = mutableListOf<AppItem>()
    private val removeDrops = mutableListOf<AppItem>()
    private var dragOutEnds = 0
    private var dismissed = 0
    private lateinit var backDispatcher: OnBackPressedDispatcher
    private var longPress = 0L

    private fun show(locked: Boolean = false) {
        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            longPress = LocalViewConfiguration.current.longPressTimeoutMillis
            LauncherTheme {
                Box(Modifier.fillMaxSize()) {
                    FolderCard(
                        folder = folderState,
                        badges = emptyMap(),
                        badgeShowCount = false,
                        badgeScale = 1f,
                        locked = locked,
                        dragController = drag,
                        onRename = {},
                        onAppClick = { launched += it },
                        onRenameApp = { renamed += it },
                        onRemoveFromFolder = { removed += it },
                        onPinShortcut = {},
                        onReorder = { app, before -> reorders += app to before },
                        onDropOnHome = { app, page, cellX, cellY -> homeDrops += app to Triple(page, cellX, cellY) },
                        onDropOnDock = { dockDrops += it },
                        onDropOnRemove = { removeDrops += it },
                        onDragOutEnd = { dragOutEnds++ },
                        onDismiss = { dismissed++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** Finger down on the icon, held past the long-press timeout (the lift). */
    private fun hold(label: String) {
        compose.onNodeWithText(label).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(longPress + 50)
        compose.waitForIdle()
    }

    private fun release(label: String) {
        compose.onNodeWithText(label).performTouchInput { up() }
        compose.waitForIdle()
    }

    private fun assertNothingDropped() {
        assertThat(reorders).isEmpty()
        assertThat(homeDrops).isEmpty()
        assertThat(dockDrops).isEmpty()
        assertThat(removeDrops).isEmpty()
    }

    @Test fun aTapLaunchesTheAppAndClosesTheFolder() {
        show()

        compose.onNodeWithText("Maps").performClick()
        compose.waitForIdle()

        assertThat(launched).containsExactly(maps)
        assertThat(dismissed).isEqualTo(1)
    }

    @Test fun aStillLongPressOpensTheMenuAndLeavesTheApp() {
        show()

        hold("Mail")
        release("Mail")

        compose.onNodeWithText("App info").assertIsDisplayed()
        compose.onNodeWithText("Rename").assertIsDisplayed()
        compose.onNodeWithText("Remove from folder").assertIsDisplayed()
        compose.onNodeWithText("Uninstall").assertIsDisplayed()
        assertThat(removed).isEmpty()
        assertThat(launched).isEmpty()
        assertThat(dismissed).isEqualTo(0)
        assertThat(drag.isDragging).isFalse()
        assertNothingDropped()
    }

    @Test fun removeFromFolderAndRenameAreMenuActions() {
        show()

        hold("Mail")
        release("Mail")
        compose.onNodeWithText("Remove from folder").performClick()
        compose.waitForIdle()
        assertThat(removed).containsExactly(mail)

        hold("Maps")
        release("Maps")
        compose.onNodeWithText("Rename").performClick()
        compose.waitForIdle()
        assertThat(renamed).containsExactly(maps)
    }

    @Test fun aLockedDesktopIgnoresTheLongPressButStillLaunchesOnTap() {
        show(locked = true)

        hold("Mail")
        assertThat(drag.isDragging).isFalse()
        release("Mail")
        compose.onNodeWithText("App info").assertDoesNotExist()

        compose.onNodeWithText("Maps").performClick()
        compose.waitForIdle()
        assertThat(launched).containsExactly(maps)
        assertNothingDropped()
    }

    @Test fun aScrimTapCloses() {
        show()

        compose.onNodeWithTag("folderScrim").performTouchInput { click(Offset(10f, 10f)) }
        compose.waitForIdle()

        assertThat(dismissed).isEqualTo(1)
        assertThat(launched).isEmpty()
    }

    @Test fun backCloses() {
        show()

        compose.runOnUiThread { backDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertThat(dismissed).isEqualTo(1)
    }

    @Test fun aSecondFingerIsRefusedWhileOneIconIsLifted() {
        show()

        hold("Mail")
        compose.onNodeWithText("Maps").performTouchInput { down(1, center) }
        compose.mainClock.advanceTimeBy(longPress + 50)
        compose.waitForIdle()
        assertThat(drag.draggedApp).isEqualTo(mail)

        compose.onNodeWithText("Maps").performTouchInput { up(1) }
        release("Mail")

        compose.onNodeWithText("App info").assertIsDisplayed()
        assertThat(launched).isEmpty()
        assertNothingDropped()
    }

    @Test fun theSemanticsClickLaunchesWithoutThePointerGesture() {
        show()

        compose.onNodeWithText("Maps").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertThat(launched).containsExactly(maps)
        assertThat(dismissed).isEqualTo(1)
    }

    @Test fun theSemanticsLongClickOpensTheMenuWithoutThePointerGesture() {
        show()

        compose.onNodeWithText("Mail").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitForIdle()

        compose.onNodeWithText("App info").assertIsDisplayed()
        assertThat(launched).isEmpty()
    }

    @Test fun aLockedDesktopExposesNoLongClickAction() {
        show(locked = true)

        compose.onNodeWithText("Mail").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        compose.onNodeWithText("Mail").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
    }
}

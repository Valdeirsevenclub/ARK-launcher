package org.arkikeskus.launcher.feature.appdrawer

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.arkikeskus.launcher.ui.expressive.ExpressiveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Work tab's own pieces: the Personal / Work tab row, the floating pause button, the paused card. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class WorkProfileUiTest {
    @get:Rule val compose = createComposeRule()

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test fun tabRowShowsPersonalAndWorkWithTabSemantics() {
        compose.setContent { ExpressiveTheme { DrawerTabRow(selected = DrawerTab.Personal, onSelect = {}) } }

        compose.onNodeWithText("Personal").assert(hasRole(Role.Tab)).assertIsSelected()
        compose.onNodeWithText("Work").assert(hasRole(Role.Tab))
    }

    @Test fun tappingWorkSelectsIt() {
        var selected: DrawerTab? = null
        compose.setContent { ExpressiveTheme { DrawerTabRow(selected = DrawerTab.Personal, onSelect = { selected = it }) } }

        compose.onNodeWithText("Work").performClick()

        assertThat(selected).isEqualTo(DrawerTab.Work)
    }

    @Test fun pauseButtonInvokesWhenEnabledOnly() {
        var clicks = 0
        compose.setContent { ExpressiveTheme { PauseWorkAppsButton(enabled = false, onClick = { clicks++ }) } }

        compose.onNodeWithText("Pause work apps").assertIsNotEnabled().performClick()

        assertThat(clicks).isEqualTo(0)
    }

    @Test fun pauseButtonInvokesWhenEnabled() {
        var clicks = 0
        compose.setContent { ExpressiveTheme { PauseWorkAppsButton(enabled = true, onClick = { clicks++ }) } }

        compose.onNodeWithText("Pause work apps").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test fun pausedCardShowsTitleBodyAndUnpause() {
        var unpauses = 0
        compose.setContent {
            ExpressiveTheme { WorkPausedCard(canPause = true, enabled = true, onUnpause = { unpauses++ }) }
        }

        compose.onNodeWithText("Work apps are paused").assertExists()
        compose.onNodeWithText("Work apps can't send notifications, use the battery or access your location.")
            .assertExists()
        compose.onNodeWithText("Unpause").performClick()

        assertThat(unpauses).isEqualTo(1)
    }

    @Test fun pausedCardHidesUnpauseWhenItCannotPause() {
        compose.setContent {
            ExpressiveTheme { WorkPausedCard(canPause = false, enabled = true, onUnpause = {}) }
        }

        compose.onNodeWithText("Work apps are paused").assertExists()
        compose.onNodeWithText("Unpause").assertDoesNotExist()
    }
}

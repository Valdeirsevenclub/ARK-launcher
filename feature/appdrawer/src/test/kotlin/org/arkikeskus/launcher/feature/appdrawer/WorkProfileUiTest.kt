package org.arkikeskus.launcher.feature.appdrawer

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import org.robolectric.annotation.GraphicsMode
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
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
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real text layout: the wrapping tests measure it
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class WorkProfileUiTest {
    @get:Rule val compose = createComposeRule()

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test fun tabRowShowsPersonalAndWorkWithTabSemantics() {
        compose.setContent { ExpressiveTheme { DrawerTabRow(selected = DrawerTab.Personal, onSelect = {}) } }

        compose.onNodeWithText("Personal").assert(hasRole(Role.Tab)).assertIsSelected()
        compose.onNodeWithText("Work").assert(hasRole(Role.Tab))
    }

    @Test
    @Config(qualifiers = "fr-rFR-w320dp-h640dp")
    fun tabRowGrowsWhenALabelWrapsAtALargeFontSize() {
        // "Professionnel" at 200 % on a 320 dp screen takes two lines: a fixed 40 dp row cut the
        // second one off. The same label on its own at the segment's width is the floor.
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                ExpressiveTheme {
                    Column(Modifier.width(320.dp)) {
                        DrawerTabRow(selected = DrawerTab.Personal, onSelect = {})
                        Box(Modifier.width(148.dp).testTag("probe")) {
                            Text("Professionnel", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        val labelHeight = compose.onNodeWithTag("probe").getBoundsInRoot().height.value
        val rowHeight = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            .getBoundsInRoot().height.value
        assertThat(labelHeight).isGreaterThan(40f) // the premise: the label really wraps here
        assertThat(rowHeight).isAtLeast(labelHeight)
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

    @Test fun pausedCardHandsAPullDownToTheNestedScrollParent() {
        // The drawer's pull-to-close lives in a NestedScrollConnection above the page: the paused
        // page must dispatch a downward drag to it like the grids do, or the drawer cannot be
        // pulled shut while the profile is paused.
        var pulled = 0f
        val connection = object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) pulled += available.y
                return Offset.Zero
            }
        }
        compose.setContent {
            ExpressiveTheme {
                Box(Modifier.size(300.dp).nestedScroll(connection).testTag("host")) {
                    WorkPausedCard(canPause = true, enabled = true, onUnpause = {})
                }
            }
        }

        compose.onNodeWithTag("host").performTouchInput {
            swipe(start = Offset(150f, 40f), end = Offset(150f, 260f), durationMillis = 120)
        }
        compose.waitForIdle()

        assertThat(pulled).isGreaterThan(0f)
    }

    @Test fun pausedCardHidesUnpauseWhenItCannotPause() {
        compose.setContent {
            ExpressiveTheme { WorkPausedCard(canPause = false, enabled = true, onUnpause = {}) }
        }

        compose.onNodeWithText("Work apps are paused").assertExists()
        compose.onNodeWithText("Unpause").assertDoesNotExist()
    }
}

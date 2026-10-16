package org.arkikeskus.launcher.feature.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class LastPageHintTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun staysHiddenUntilTheFirstBounce() {
        compose.setContent { LastPageHint(showCount = 0) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Last page").assertDoesNotExist()
    }

    @Test
    fun eachBounceShowsTheHintBrieflyAndThenItGoes() {
        var bounces by mutableIntStateOf(0)
        compose.setContent { LastPageHint(showCount = bounces) }

        bounces = 1
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Last page").assertExists()
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithText("Last page").assertDoesNotExist()

        bounces = 2
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Last page").assertExists()
    }
}

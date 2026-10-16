package org.arkikeskus.launcher.feature.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
class PageEndHintTest {
    @get:Rule val compose = createComposeRule()

    private var bounce by mutableStateOf(PageEndBounce(serial = 0, atStart = false))

    @Test
    fun staysHiddenUntilTheFirstBounce() {
        compose.setContent { PageEndHint(bounce = { bounce }) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Last page").assertDoesNotExist()
        compose.onNodeWithText("First page").assertDoesNotExist()
    }

    @Test
    fun eachBounceOffTheEndSaysLastPageBrieflyAndThenItGoes() {
        compose.setContent { PageEndHint(bounce = { bounce }) }

        bounce = PageEndBounce(serial = 1, atStart = false)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Last page").assertExists()
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithText("Last page").assertDoesNotExist()

        bounce = PageEndBounce(serial = 2, atStart = false)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Last page").assertExists()
    }

    @Test
    fun aBounceOffTheStartSaysFirstPage() {
        compose.setContent { PageEndHint(bounce = { bounce }) }

        bounce = PageEndBounce(serial = 1, atStart = true)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("First page").assertExists()
        compose.onNodeWithText("Last page").assertDoesNotExist()
    }
}

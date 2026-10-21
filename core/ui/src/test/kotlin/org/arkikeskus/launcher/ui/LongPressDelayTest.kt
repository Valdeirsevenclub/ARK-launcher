package org.arkikeskus.launcher.ui

import android.app.Application
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The user's long-press delay setting, applied on top of the system's long-press timeout. */
class ScaledLongPressTest {
    private val system = object : ViewConfiguration {
        override val longPressTimeoutMillis = 400L
        override val doubleTapTimeoutMillis = 300L
        override val doubleTapMinTimeMillis = 40L
        override val touchSlop = 24f
    }

    @Test fun theDefaultScaleLeavesTheSystemTimeoutAlone() {
        assertThat(system.withLongPressScale(1f).longPressTimeoutMillis).isEqualTo(400L)
    }

    @Test fun theScaleStretchesOrShortensTheSystemTimeout() {
        assertThat(system.withLongPressScale(1.5f).longPressTimeoutMillis).isEqualTo(600L)
        assertThat(system.withLongPressScale(0.5f).longPressTimeoutMillis).isEqualTo(200L)
        assertThat(system.withLongPressScale(2f).longPressTimeoutMillis).isEqualTo(800L)
    }

    @Test fun everyOtherTolerancePassesThroughUnchanged() {
        val scaled = system.withLongPressScale(1.5f)
        assertThat(scaled.touchSlop).isEqualTo(24f)
        assertThat(scaled.doubleTapTimeoutMillis).isEqualTo(300L)
        assertThat(scaled.doubleTapMinTimeMillis).isEqualTo(40L)
    }

    @Test fun anOutOfRangeScaleIsClampedToTheSettingsRange() {
        assertThat(system.withLongPressScale(0f).longPressTimeoutMillis).isEqualTo(200L)
        assertThat(system.withLongPressScale(9f).longPressTimeoutMillis).isEqualTo(800L)
    }
}

/** The delay setting reaches every icon press through [LocalViewConfiguration]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class LongPressDelayProviderTest {
    @get:Rule val compose = createComposeRule()

    private val outcomes = mutableListOf<IconPressOutcome>()
    private var systemTimeout = 0L

    private fun setIconUnder(scale: Float) = compose.setContent {
        systemTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
        LongPressDelay(scale) {
            Box(
                Modifier
                    .size(100.dp)
                    .testTag("icon")
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            outcomes += awaitIconPress(down)
                        }
                    },
            )
        }
    }

    @Test fun aLongerDelayMakesAnIconHoldWaitPastTheSystemTimeout() {
        setIconUnder(scale = 1.5f)
        compose.onNodeWithTag("icon").performTouchInput { down(Offset(30f, 40f)) }
        compose.mainClock.advanceTimeBy(systemTimeout + 50)
        compose.waitForIdle()
        assertThat(outcomes).isEmpty()

        compose.mainClock.advanceTimeBy(systemTimeout / 2)
        compose.waitForIdle()
        assertThat(outcomes).containsExactly(IconPressOutcome.Held(Offset(30f, 40f)))
    }

    @Test fun theDefaultDelayHoldsAtTheSystemTimeout() {
        setIconUnder(scale = 1f)
        compose.onNodeWithTag("icon").performTouchInput { down(Offset(30f, 40f)) }
        compose.mainClock.advanceTimeBy(systemTimeout + 50)
        compose.waitForIdle()
        assertThat(outcomes).containsExactly(IconPressOutcome.Held(Offset(30f, 40f)))
    }
}

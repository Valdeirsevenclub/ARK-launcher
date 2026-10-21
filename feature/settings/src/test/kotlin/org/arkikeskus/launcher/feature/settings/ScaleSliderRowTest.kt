package org.arkikeskus.launcher.feature.settings

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tick counts that put the thumb on round steps. */
class ScaleSliderStepsTest {
    @Test fun fivePercentStepsAcrossEachSizeRange() {
        assertThat(scaleSliderSteps(0.8f..1.3f, 0.05f)).isEqualTo(9)
        assertThat(scaleSliderSteps(0.8f..1.6f, 0.05f)).isEqualTo(15)
        assertThat(scaleSliderSteps(0.6f..1.8f, 0.05f)).isEqualTo(23)
    }

    @Test fun tenPercentStepsAcrossTheLongPressRange() {
        assertThat(scaleSliderSteps(0.5f..2.0f, 0.1f)).isEqualTo(14)
    }

    @Test fun aMultiplierReadsAsAWholePercent() {
        assertThat(scalePercent(1.149f)).isEqualTo(115)
        assertThat(scalePercent(0.8f)).isEqualTo(80)
        assertThat(scalePercent(1f)).isEqualTo(100)
    }
}

/** The slider row: readout, snapping with a felt tick, and the way back to the default. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class ScaleSliderRowTest {
    @get:Rule val compose = createComposeRule()

    private val changes = mutableListOf<Float>()
    private val felt = mutableListOf<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            felt += hapticFeedbackType
        }
    }

    private fun setRow(value: Float) = compose.setContent {
        CompositionLocalProvider(LocalHapticFeedback provides haptics) {
            ScaleSliderRow(
                label = "Icon size",
                value = value,
                onValueChange = { changes += it },
                valueRange = 0.8f..1.3f,
                valueText = { "${scalePercent(it)}%" },
            )
        }
    }

    private val slider = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)

    @Test fun theValueShowsAsAWholePercentBesideTheLabel() {
        setRow(1.149f)
        compose.onNodeWithText("115%").assertIsDisplayed()
    }

    @Test fun atTheDefaultThereIsNothingToReset() {
        setRow(1f)
        compose.onNodeWithText("100%").assertIsDisplayed()
        compose.onNodeWithText("Default 100%").assertDoesNotExist()
    }

    @Test fun theDefaultChipBringsTheValueStraightBack() {
        setRow(1.15f)
        compose.onNodeWithText("Default 100%").performClick()
        assertThat(changes).containsExactly(1f)
        assertThat(felt).containsExactly(HapticFeedbackType.SegmentTick)
    }

    @Test fun theSliderSnapsToFivePercentAndTicksOnce() {
        setRow(1f)
        compose.onNode(slider).performSemanticsAction(SemanticsActions.SetProgress) { it(1.13f) }
        compose.waitForIdle()
        assertThat(changes).hasSize(1)
        assertThat(changes.single()).isWithin(1e-4f).of(1.15f)
        assertThat(felt).containsExactly(HapticFeedbackType.SegmentTick)
    }

    @Test fun repeatingTheSameSnappedValueDoesNotTickAgain() {
        setRow(1f)
        compose.onNode(slider).performSemanticsAction(SemanticsActions.SetProgress) { it(1.13f) }
        compose.onNode(slider).performSemanticsAction(SemanticsActions.SetProgress) { it(1.16f) }
        compose.waitForIdle()
        assertThat(changes).hasSize(2)
        assertThat(felt).containsExactly(HapticFeedbackType.SegmentTick)
    }
}

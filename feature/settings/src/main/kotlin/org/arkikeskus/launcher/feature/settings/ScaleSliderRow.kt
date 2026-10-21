package org.arkikeskus.launcher.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import kotlin.math.roundToInt

/** Slider tick count that lands the thumb on multiples of [step] across [range] (Material counts
 *  only the ticks strictly between the two ends). */
internal fun scaleSliderSteps(range: ClosedFloatingPointRange<Float>, step: Float): Int =
    (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0)

/** A size multiplier as a whole percent (1.149 → 115). */
internal fun scalePercent(value: Float): Int = (value * 100f).roundToInt()

/**
 * A settings slider for a multiplier setting (icon size, label size, long-press delay): the current
 * value is shown next to the label in the words of [valueText], the thumb snaps to [step]
 * increments with a tick of haptic feedback on every change so a move is felt, and a "Default …"
 * chip appears whenever the value is off [default] to bring it straight back.
 */
@Composable
internal fun ScaleSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    description: String? = null,
    step: Float = 0.05f,
    default: Float = 1f,
) {
    val p = LocalExpressivePalette.current
    val haptics = LocalHapticFeedback.current
    val shown = valueText(value)
    // The readout the last tick was felt at: one tick per visible change, however many times the
    // slider reports the same snapped value while the stored setting catches up.
    var lastFelt by remember { mutableStateOf(shown) }
    val change: (Float) -> Unit = { next ->
        val text = valueText(next)
        if (text != lastFelt) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            lastFelt = text
        }
        onValueChange(next)
    }
    Surface(
        color = p.surfaceHi,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = p.shadow,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (shown != valueText(default)) {
                    TextButton(
                        onClick = { change(default) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.padding(end = 6.dp),
                    ) {
                        Text(stringResource(R.string.settings_reset_default, valueText(default)), color = Accent, fontSize = 13.sp)
                    }
                }
                Text(shown, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            if (description != null) {
                Text(description, color = p.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Slider(
                value = value,
                onValueChange = change,
                valueRange = valueRange,
                steps = scaleSliderSteps(valueRange, step),
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = p.trackOff,
                    activeTickColor = Accent,
                    inactiveTickColor = p.trackOff,
                ),
            )
        }
    }
}

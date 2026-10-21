package org.arkikeskus.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import kotlin.math.roundToLong

/** Range of the long-press delay setting, as a multiplier on the system's long-press timeout. */
object LongPressScale {
    const val MIN = 0.5f
    const val MAX = 2.0f
    const val DEFAULT = 1.0f
}

/**
 * The long-press delay setting as the shell root provided it. A dialog, bottom sheet or popup is its own
 * window whose root re-provides the system [LocalViewConfiguration], but this plain local crosses that
 * boundary, so the window's content can re-apply the setting with the no-argument [LongPressDelay].
 */
val LocalLongPressScale = compositionLocalOf { LongPressScale.DEFAULT }

/**
 * The system tolerances with the long-press timeout multiplied by the user's delay setting; every
 * other value (touch slop, double-tap timing) passes through. Phones differ a lot in how eagerly
 * a resting finger reads as a hold, so the setting lets the user stretch or shorten the wait
 * before an icon's menu opens or it can be moved. The system timeout is read on every access: it is
 * the accessibility "Touch & hold delay", which the user can change while the launcher is alive.
 */
fun ViewConfiguration.withLongPressScale(scale: Float): ViewConfiguration {
    val base = this
    val clamped = scale.coerceIn(LongPressScale.MIN, LongPressScale.MAX)
    return object : ViewConfiguration by base {
        override val longPressTimeoutMillis: Long
            get() = (base.longPressTimeoutMillis * clamped).roundToLong()
    }
}

/**
 * Applies the long-press delay setting to everything in [content]: each press site (home icons,
 * dock, folders, drawer, widgets, Compose's own long-press detectors) reads its timeout from
 * [LocalViewConfiguration], so one provider at the root covers them all — except the content of a
 * dialog, bottom sheet or popup, which re-applies it with the no-argument overload.
 */
@Composable
fun LongPressDelay(scale: Float, content: @Composable () -> Unit) {
    val system = LocalViewConfiguration.current
    val scaled = remember(system, scale) { system.withLongPressScale(scale) }
    CompositionLocalProvider(
        LocalLongPressScale provides scale,
        LocalViewConfiguration provides scaled,
        content = content,
    )
}

/** Re-applies the inherited delay setting inside a window of its own (a dialog, bottom sheet or popup). */
@Composable
fun LongPressDelay(content: @Composable () -> Unit) = LongPressDelay(LocalLongPressScale.current, content)

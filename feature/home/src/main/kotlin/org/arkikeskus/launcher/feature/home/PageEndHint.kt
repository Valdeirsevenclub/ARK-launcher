package org.arkikeskus.launcher.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

/** A swipe that ran into an end of the pages: [serial] grows with each one (0 = none yet), and
 *  [atStart] tells the first page from the last. */
@Immutable
internal data class PageEndBounce(val serial: Int, val atStart: Boolean)

/**
 * "First page" / "Last page", shown briefly whenever a swipe runs into that end of the workspace.
 * Read through [bounce] so only this hint recomposes when it changes. White with the widgets' text
 * shadow, so it reads on a light wallpaper too.
 */
@Composable
internal fun PageEndHint(bounce: () -> PageEndBounce, modifier: Modifier = Modifier) {
    val current = bounce()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(current.serial) {
        if (current.serial == 0) return@LaunchedEffect
        visible = true
        delay(PAGE_END_HINT_MS)
        visible = false
    }
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Text(
            text = stringResource(if (current.atStart) R.string.home_first_page_hint else R.string.home_last_page_hint),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(
                shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), blurRadius = 8f),
            ),
        )
    }
}

private const val PAGE_END_HINT_MS = 1_500L

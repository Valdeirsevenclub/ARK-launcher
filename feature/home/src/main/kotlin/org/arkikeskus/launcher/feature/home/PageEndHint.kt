package org.arkikeskus.launcher.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * "No more pages", shown briefly whenever a swipe runs into either end of the workspace — the same
 * words at both ends, since the home page need not be the first one. [bounces] counts those swipes
 * (0 = none yet) and is read through a lambda so only this hint recomposes. White with the widgets'
 * text shadow, so it reads on a light wallpaper too.
 */
@Composable
internal fun PageEndHint(bounces: () -> Int, modifier: Modifier = Modifier) {
    val count = bounces()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(count) {
        if (count == 0) return@LaunchedEffect
        visible = true
        delay(PAGE_END_HINT_MS)
        visible = false
    }
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Text(
            text = stringResource(R.string.home_page_end_hint),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(
                shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), blurRadius = 8f),
            ),
        )
    }
}

private const val PAGE_END_HINT_MS = 1_500L

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
 * "Last page", shown briefly whenever the workspace bounces back from the empty page past the last
 * one ([showCount] counts the bounces; 0 = never). White with the widgets' text shadow, so it reads
 * on a light wallpaper too.
 */
@Composable
internal fun LastPageHint(showCount: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(showCount) {
        if (showCount == 0) return@LaunchedEffect
        visible = true
        delay(LAST_PAGE_HINT_MS)
        visible = false
    }
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Text(
            text = stringResource(R.string.home_last_page_hint),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(
                shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), blurRadius = 8f),
            ),
        )
    }
}

private const val LAST_PAGE_HINT_MS = 1_500L

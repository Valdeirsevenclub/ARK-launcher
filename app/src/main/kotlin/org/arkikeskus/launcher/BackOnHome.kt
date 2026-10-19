package org.arkikeskus.launcher

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * BACK on the bare home screen does nothing, like Pixel Launcher. Without this the activity's
 * default BACK finishes it and the system re-creates the home app a moment later — a flash of the
 * splash screen and the workspace reloading, for nothing.
 *
 * Call it before `setContent`: the dispatcher asks the most recently added enabled callback first,
 * so every overlay that handles BACK itself — the drawer, the widget picker, a widget's edit frame,
 * the intro, and the sheets and popups in their own windows — still goes first. This one only
 * catches a BACK that nothing else took. Registered as an always-enabled callback it also covers
 * the predictive back gesture: with no callback the system would animate the activity away.
 */
fun ComponentActivity.keepHomeOnBack() {
    onBackPressedDispatcher.addCallback(
        this,
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        },
    )
}

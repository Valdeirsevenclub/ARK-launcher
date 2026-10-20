package org.arkikeskus.launcher.feature.home

import android.content.Context
import android.view.View
import android.widget.Toast

/**
 * Tells the user how another app's "Add to home screen" ended, said before the window goes: the
 * user stays in the requesting app and would otherwise see nothing of the outcome. A success names
 * the item in a toast and, as Launcher3's AddItemActivity does, to accessibility; a failure says
 * so; a cancel says nothing.
 */
internal fun announcePinOutcome(context: Context, view: View, state: PinRequestUiState.Finished) {
    when {
        state.failed -> Toast.makeText(context, R.string.pin_request_failed, Toast.LENGTH_SHORT).show()
        state.addedLabel != null -> {
            val text = context.getString(R.string.pin_request_added, state.addedLabel)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            @Suppress("DEPRECATION")
            view.announceForAccessibility(text)
        }
    }
}

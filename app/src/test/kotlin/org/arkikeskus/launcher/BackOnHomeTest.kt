package org.arkikeskus.launcher

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BACK on the bare home screen does nothing, like Pixel Launcher: without [keepHomeOnBack] the
 * activity finishes and the system re-creates the home app a moment later. Anything that handles
 * BACK itself and registers after it — the drawer, a widget picker, an edit frame — still goes
 * first, and once it has closed the next BACK is swallowed again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackOnHomeTest {

    private fun activity(): ComponentActivity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    @Test fun backWithNothingOpenLeavesTheHomeActivityRunning() {
        val activity = activity()
        activity.keepHomeOnBack()

        activity.onBackPressedDispatcher.onBackPressed()

        assertThat(activity.isFinishing).isFalse()
    }

    @Test fun withoutItBackFinishesTheActivity() {
        val activity = activity()

        activity.onBackPressedDispatcher.onBackPressed()

        assertThat(activity.isFinishing).isTrue()
    }

    @Test fun anOverlayThatHandlesBackItselfStillGoesFirst() {
        val activity = activity()
        activity.keepHomeOnBack()
        var overlayOpen = true
        activity.onBackPressedDispatcher.addCallback(
            activity,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    overlayOpen = false
                    isEnabled = false
                }
            },
        )

        activity.onBackPressedDispatcher.onBackPressed()
        assertThat(overlayOpen).isFalse()
        activity.onBackPressedDispatcher.onBackPressed()

        assertThat(activity.isFinishing).isFalse()
    }
}

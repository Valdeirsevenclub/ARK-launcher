package org.arkikeskus.launcher.feature.home

import android.app.Application
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** What the user hears of a pin request's end, while still in the requesting app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class PinRequestOutcomeTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val view = View(context)

    @Test fun anAddedItemIsNamedInAToast() {
        announcePinOutcome(context, view, PinRequestUiState.Finished(addedLabel = "Daily News"))

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Daily News added to home screen")
    }

    @Test fun aFailureSaysSo() {
        announcePinOutcome(context, view, PinRequestUiState.Finished(failed = true))

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Couldn't add to home screen")
    }

    @Test fun aCancelledRequestSaysNothing() {
        announcePinOutcome(context, view, PinRequestUiState.Finished())

        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    }
}

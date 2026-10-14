package org.arkikeskus.launcher.feature.home

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.arkikeskus.launcher.model.LauncherSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class PageDotsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ringMarkerDrawsEveryPageAsADot() {
        compose.setContent { PageDots(count = 3, current = 0, home = 1, homeMarker = LauncherSettings.MARKER_RING) }
        compose.onAllNodesWithTag(PAGE_DOT_TAG).assertCountEquals(3)
        compose.onNodeWithTag(PAGE_DOT_HOUSE_TAG).assertDoesNotExist()
    }

    @Test fun houseMarkerReplacesOnlyTheHomePageDot() {
        compose.setContent { PageDots(count = 3, current = 0, home = 1, homeMarker = LauncherSettings.MARKER_HOUSE) }
        compose.onAllNodesWithTag(PAGE_DOT_TAG).assertCountEquals(2)
        compose.onAllNodesWithTag(PAGE_DOT_HOUSE_TAG).assertCountEquals(1)
    }
}

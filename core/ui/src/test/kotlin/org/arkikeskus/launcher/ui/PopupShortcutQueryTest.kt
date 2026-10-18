package org.arkikeskus.launcher.ui

import android.app.Application
import android.content.ComponentName
import android.content.pm.LauncherApps.ShortcutQuery
import android.os.Process
import com.google.common.truth.Truth.assertThat
import org.arkikeskus.launcher.model.AppItem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The query behind the long-press popup. Robolectric's LauncherApps can't run a query without
 * FLAG_MATCH_PINNED, so the emitted query itself is checked (the fields Robolectric's own shadow reads).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PopupShortcutQueryTest {
    private val video = AppItem("com.video", "com.video.HomeActivity", Process.myUserHandle(), 0L, "Video")

    private fun ShortcutQuery.field(name: String): Any? =
        ShortcutQuery::class.java.getDeclaredField(name).apply { isAccessible = true }.get(this)

    @Test fun onlyThePressedIconsShortcutsAreAskedFor() {
        // A package can publish shortcuts for several (also hidden, alternative-icon) activities.
        val query = popupShortcutQuery(video)

        assertThat(query.field("mPackage")).isEqualTo("com.video")
        assertThat(query.field("mActivity")).isEqualTo(ComponentName("com.video", "com.video.HomeActivity"))
    }

    @Test fun aShortcutOnlyPinnedToHomeIsNotOfferedAgain() {
        assertThat(popupShortcutQuery(video).field("mQueryFlags"))
            .isEqualTo(ShortcutQuery.FLAG_MATCH_DYNAMIC or ShortcutQuery.FLAG_MATCH_MANIFEST)
    }
}

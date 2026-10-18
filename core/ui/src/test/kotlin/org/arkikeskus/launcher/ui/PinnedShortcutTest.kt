package org.arkikeskus.launcher.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** A shortcut pinned on home: what it resolves to, and what tapping it does when it can't start. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PinnedShortcutTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    /** Publishes a shortcut of this test package the way the system hands it to a launcher. */
    private fun publish(id: String, disabled: Boolean = false, disabledMessage: String? = null) {
        val info = ShortcutInfo.Builder(context, id)
            .setShortLabel("Mia")
            .setLongLabel("Chat with Mia")
            .setActivity(ComponentName(context.packageName, "${context.packageName}.Main"))
            .setIntent(Intent(Intent.ACTION_VIEW))
            .apply { if (disabledMessage != null) setDisabledMessage(disabledMessage) }
            .build()
        if (disabled) {
            // The system sets this flag when the app disables the shortcut; it has no public setter.
            val flag = ShortcutInfo::class.java.getField("FLAG_DISABLED").getInt(null)
            ShortcutInfo::class.java.getMethod("addFlags", Int::class.javaPrimitiveType).invoke(info, flag)
        }
        shadowOf(launcherApps).addDynamicShortcut(info)
    }

    @Test fun aShortcutTheAppDisabledStillResolvesWithTheAppsReason() {
        publish("chat-mia", disabled = true, disabledMessage = "This chat was deleted")

        val resolved = AppShortcuts.resolve(context, context.packageName, "chat-mia", userSerial = 0L)

        assertThat(resolved).isNotNull()
        assertThat(resolved!!.enabled).isFalse()
        assertThat(resolved.disabledMessage).isEqualTo("This chat was deleted")
    }

    @Test fun anEnabledShortcutResolvesAsEnabled() {
        publish("chat-mia")

        val resolved = AppShortcuts.resolve(context, context.packageName, "chat-mia", userSerial = 0L)

        assertThat(resolved?.label).isEqualTo("Chat with Mia")
        assertThat(resolved?.enabled).isTrue()
    }

    private val unavailable get() = context.getString(R.string.shortcut_unavailable)

    @Test fun tappingAShortcutTheAppDisabledShowsTheAppsReason() {
        AppShortcuts.startById(context, context.packageName, "chat-mia", 0L, enabled = false, disabledMessage = "This chat was deleted")

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("This chat was deleted")
    }

    @Test fun aDisabledShortcutWithoutAReasonSaysItIsNotAvailable() {
        AppShortcuts.startById(context, context.packageName, "chat-mia", 0L, enabled = false, disabledMessage = null)

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(unavailable)
    }

    // Robolectric's startShortcut always throws, like a real start of a shortcut that is gone.
    @Test fun aPinnedShortcutThatFailsToStartSaysItIsNotAvailable() {
        publish("chat-mia")

        AppShortcuts.startById(context, context.packageName, "chat-mia", 0L, enabled = true, disabledMessage = null)

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(unavailable)
    }

    @Test fun aPopupShortcutThatFailsToStartSaysItIsNotAvailable() {
        publish("chat-mia")
        val item = AppShortcuts.Item(context.packageName, "chat-mia", Process.myUserHandle(), 0L, "Chat with Mia", "Mia", null)

        AppShortcuts.start(context, item)

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(unavailable)
    }
}

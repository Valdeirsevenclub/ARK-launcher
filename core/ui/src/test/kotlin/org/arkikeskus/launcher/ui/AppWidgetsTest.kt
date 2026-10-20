package org.arkikeskus.launcher.ui

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.os.Process
import android.os.UserHandle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.arkikeskus.launcher.model.AppItem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Whether an app has widgets to offer the home screen — what decides the popup's Widgets row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppWidgetsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val app = AppItem("com.example", "Main", Process.myUserHandle(), 0L, "Example")

    private fun install(pkg: String, category: Int = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) {
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(pkg, "$pkg.Widget")
            widgetCategory = category
        }
        shadowOf(AppWidgetManager.getInstance(context)).addInstalledProvidersForProfile(Process.myUserHandle(), info)
    }

    @Test fun anAppWithAHomeScreenWidgetHasWidgets() {
        install("com.example")

        assertThat(AppWidgets.hasHomeScreenWidgets(context, app)).isTrue()
    }

    @Test fun anAppWithoutProvidersHasNone() {
        install("com.other")

        assertThat(AppWidgets.hasHomeScreenWidgets(context, app)).isFalse()
    }

    @Test fun aKeyguardOnlyWidgetDoesNotCount() {
        install("com.example", AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD)

        assertThat(AppWidgets.hasHomeScreenWidgets(context, app)).isFalse()
    }

    @Test fun aWorkProfileAppHasNoneEvenWithAProvider() {
        install("com.example")
        val workApp = AppItem("com.example", "Main", UserHandle.getUserHandleForUid(10 * 100_000 + 1_000), 10L, "Example")

        assertThat(AppWidgets.hasHomeScreenWidgets(context, workApp)).isFalse()
    }

    @Test fun aWidgetForBothSurfacesCounts() {
        install("com.example", AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN or AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD)

        assertThat(AppWidgets.hasHomeScreenWidgets(context, app)).isTrue()
    }
}

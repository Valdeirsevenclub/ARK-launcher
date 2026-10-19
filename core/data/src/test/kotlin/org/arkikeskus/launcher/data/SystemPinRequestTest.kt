package org.arkikeskus.launcher.data

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.widget.RemoteViews
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

/**
 * The confirmation is an exported activity, so any app can start it with a request of its own making
 * instead of the system's: what the request says about a widget is checked against the system.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemPinRequestTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    /** [label] and the profile come from the provider's activity info (a hidden field). */
    private fun provider(component: ComponentName, label: String, minWidth: Int = 0) = AppWidgetProviderInfo().also {
        it.provider = component
        it.minWidth = minWidth
        val activity = ActivityInfo().apply {
            packageName = component.packageName
            name = component.className
            nonLocalizedLabel = label
            applicationInfo = ApplicationInfo().apply {
                packageName = component.packageName
                uid = Process.myUid()
            }
        }
        AppWidgetProviderInfo::class.java.getField("providerInfo").set(it, activity)
    }

    private fun install(info: AppWidgetProviderInfo) =
        shadowOf(AppWidgetManager.getInstance(context)).addInstalledProvidersForProfile(Process.myUserHandle(), info)

    /** The system's own request class (its hidden constructor) around an inner object an app made up,
     *  describing [info] and handing over [preview]: what another app could start the confirmation with. */
    private fun madeUpWidgetRequest(info: AppWidgetProviderInfo, preview: RemoteViews? = null): Intent {
        val innerClass = Class.forName("android.content.pm.IPinItemRequest")
        val inner = Proxy.newProxyInstance(innerClass.classLoader, arrayOf(innerClass)) { _, method, _ ->
            when (method.name) {
                "isValid", "accept" -> true
                "getAppWidgetProviderInfo" -> info
                "getExtras" -> Bundle().apply { preview?.let { putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, it) } }
                else -> null
            }
        }
        val request = LauncherApps.PinItemRequest::class.java
            .getDeclaredConstructor(innerClass, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .newInstance(inner, LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET)
        return Intent(LauncherApps.ACTION_CONFIRM_PIN_APPWIDGET).putExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST, request)
    }

    private val clock = ComponentName("com.clock", "com.clock.Analog")

    @Test
    fun aWidgetRequestNamingNoInstalledProviderIsDismissed() {
        assertThat(systemPinRequest(context, madeUpWidgetRequest(provider(clock, "Analog clock")))).isNull()
    }

    @Test
    fun aWidgetIsDescribedByItsInstalledProviderNotByTheRequest() {
        install(provider(clock, "Analog clock", minWidth = 250))

        val request = systemPinRequest(context, madeUpWidgetRequest(provider(clock, "Your bank", minWidth = 1))) as WidgetPinRequest

        assertThat(request.item).isEqualTo(PinItem.Widget(clock.flattenToString(), "Analog clock", ownProfile = true))
        assertThat(request.info.minWidth).isEqualTo(250)
    }

    /** A request carries the requesting app's own preview; one made for another package is not shown
     *  (the provider's own preview stands in). */
    @Test
    fun aPreviewMadeForAnotherPackageThanTheProvidersIsNotShown() {
        val own = ComponentName(context.packageName, "Analog")
        install(provider(own, "Analog clock"))
        install(provider(clock, "Analog clock"))
        val preview = RemoteViews(context.packageName, android.R.layout.simple_list_item_1)

        val fromItsPackage = systemPinRequest(context, madeUpWidgetRequest(provider(own, "x"), preview)) as WidgetPinRequest
        val fromAnother = systemPinRequest(context, madeUpWidgetRequest(provider(clock, "x"), preview)) as WidgetPinRequest

        assertThat(fromItsPackage.remotePreview).isNotNull()
        assertThat(fromAnother.remotePreview).isNull()
    }
}

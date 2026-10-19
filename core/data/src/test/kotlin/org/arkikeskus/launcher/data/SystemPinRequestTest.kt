package org.arkikeskus.launcher.data

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Bundle
import android.os.Process
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
     *  describing [info]: what another app could start the confirmation with. */
    private fun madeUpWidgetRequest(info: AppWidgetProviderInfo): Intent {
        val innerClass = Class.forName("android.content.pm.IPinItemRequest")
        val inner = Proxy.newProxyInstance(innerClass.classLoader, arrayOf(innerClass)) { _, method, _ ->
            when (method.name) {
                "isValid", "accept" -> true
                "getAppWidgetProviderInfo" -> info
                "getExtras" -> Bundle()
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

    /** [shortcut] in a request of the same made-up kind. */
    private fun madeUpShortcutRequest(shortcut: ShortcutInfo): Intent {
        val innerClass = Class.forName("android.content.pm.IPinItemRequest")
        val inner = Proxy.newProxyInstance(innerClass.classLoader, arrayOf(innerClass)) { _, method, _ ->
            when (method.name) {
                "isValid", "accept" -> true
                "getShortcutInfo" -> shortcut
                else -> null
            }
        }
        val request = LauncherApps.PinItemRequest::class.java
            .getDeclaredConstructor(innerClass, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .newInstance(inner, LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT)
        return Intent(LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT).putExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST, request)
    }

    /** A shortcut the system has (published, or pinned already) shows as the system has it, whatever
     *  name a request gives it. */
    @Test
    fun aShortcutTheSystemHasIsShownAsTheSystemHasIt() {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        shadowOf(launcherApps).addDynamicShortcut(ShortcutInfo.Builder(context, "mia").setShortLabel("Mia").build())

        val request = systemPinRequest(context, madeUpShortcutRequest(ShortcutInfo.Builder(context, "mia").setShortLabel("Your bank").build()))

        assertThat((request!!.item as PinItem.Shortcut).label).isEqualTo("Mia")
    }

    /** One only the request carries (a browser's page, say) shows as requested: it is stored only if the
     *  system pins it (PinRequestStoreTest). */
    @Test
    fun aShortcutOnlyTheRequestCarriesShowsAsRequested() {
        val request = systemPinRequest(context, madeUpShortcutRequest(ShortcutInfo.Builder(context, "page").setShortLabel("A page").build()))

        assertThat((request!!.item as PinItem.Shortcut).label).isEqualTo("A page")
    }
}

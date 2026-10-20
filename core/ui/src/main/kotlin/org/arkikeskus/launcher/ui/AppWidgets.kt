package org.arkikeskus.launcher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Process
import org.arkikeskus.launcher.model.AppItem

/** The widgets an app offers the home screen — what decides the long-press popup's Widgets row. */
object AppWidgets {
    /** True when [appItem]'s package has at least one home-screen widget. A work-profile app never
     *  has: the picker lists the main profile's providers only, and a work-profile widget cannot be
     *  added yet (the pin-request path says so). Binder round-trips: call off the main thread. */
    fun hasHomeScreenWidgets(context: Context, appItem: AppItem): Boolean = runCatching {
        if (appItem.user != Process.myUserHandle()) return false
        AppWidgetManager.getInstance(context)
            .getInstalledProvidersForPackage(appItem.packageName, appItem.user)
            .any { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
    }.getOrDefault(false)
}

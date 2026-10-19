package org.arkikeskus.launcher.data

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.widget.RemoteViews
import androidx.core.os.BundleCompat

/**
 * The request behind a `LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT` or `ACTION_CONFIRM_PIN_APPWIDGET`
 * intent as a [PinRequest] (a [WidgetPinRequest] for a widget), or null when the intent carries
 * none, the request is of an unknown kind, or its profile or provider can't be resolved. The
 * shortcut's label is the one home will show ([PinItem.Shortcut.label]).
 */
fun systemPinRequest(context: Context, intent: Intent): PinRequest? {
    val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
    val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull() ?: return null
    return when (request.requestType) {
        LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> shortcutRequest(context, request)
        LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> widgetRequest(context, request)
        else -> null
    }
}

private fun shortcutRequest(context: Context, request: LauncherApps.PinItemRequest): PinRequest? {
    val info = runCatching { request.shortcutInfo }.getOrNull() ?: return null
    val userManager = context.getSystemService(UserManager::class.java) ?: return null
    val serial = runCatching { userManager.getSerialNumberForUser(info.userHandle) }.getOrNull()
        ?.takeIf { it >= 0 } ?: return null
    // Same rule as AppShortcuts.resolve, so the preview shows the name the home screen will.
    val label = (info.longLabel ?: info.shortLabel)?.toString() ?: info.id
    return SystemPinRequest(request, info, PinItem.Shortcut(info.`package`, info.id, serial, label))
}

private fun widgetRequest(context: Context, request: LauncherApps.PinItemRequest): PinRequest? {
    val info = runCatching { request.getAppWidgetProviderInfo(context) }.getOrNull() ?: return null
    val provider = info.provider ?: return null
    // The same name the widget picker shows for it.
    val label = runCatching { info.loadLabel(context.packageManager) }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: provider.packageName
    val ownProfile = info.profile == null || info.profile == Process.myUserHandle()
    return SystemWidgetPinRequest(request, info, PinItem.Widget(provider.flattenToString(), label, ownProfile))
}

/** The requesting app's name in [user]'s profile (a work app's badge-less label), or its package. */
private fun appLabel(context: Context, packageName: String, user: UserHandle): String {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    return runCatching {
        launcherApps?.getApplicationInfo(packageName, 0, user)?.loadLabel(context.packageManager)?.toString()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName
}

private class SystemPinRequest(
    private val request: LauncherApps.PinItemRequest,
    private val info: ShortcutInfo,
    override val item: PinItem.Shortcut,
) : PinRequest {
    override val isValid: Boolean get() = request.isValid

    override fun accept(): Boolean = request.accept()

    override fun loadPreview(context: Context): PinPreview {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val density = context.resources.displayMetrics.densityDpi
        val icon = runCatching {
            launcherApps?.getShortcutIconDrawable(info, density)?.toBitmap(PREVIEW_ICON_PX)
        }.getOrNull()
        return PinPreview(appLabel(context, info.`package`, info.userHandle), icon)
    }

    private fun Drawable.toBitmap(sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, sizePx, sizePx)
        draw(Canvas(bitmap))
        return bitmap
    }

    private companion object {
        /** The raster size the home screen uses for a pinned shortcut's icon (AppShortcuts.resolve). */
        const val PREVIEW_ICON_PX = 168
    }
}

private class SystemWidgetPinRequest(
    private val request: LauncherApps.PinItemRequest,
    override val info: AppWidgetProviderInfo,
    override val item: PinItem.Widget,
) : WidgetPinRequest {
    override val isValid: Boolean get() = request.isValid

    /** A widget is accepted with its id; the plain accept would leave it unbound. */
    override fun accept(): Boolean = false

    override fun accept(appWidgetId: Int): Boolean =
        request.accept(Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) })

    override val remotePreview: RemoteViews? by lazy {
        runCatching {
            request.extras?.let { BundleCompat.getParcelable(it, AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, RemoteViews::class.java) }
        }.getOrNull()
    }

    /** The widget has its own preview (the picker's); only the requesting app's name is read here. */
    override fun loadPreview(context: Context): PinPreview =
        PinPreview(appLabel(context, info.provider.packageName, info.profile ?: Process.myUserHandle()), icon = null)
}

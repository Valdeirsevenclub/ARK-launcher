package org.arkikeskus.launcher.data

import android.content.Context

/**
 * [PendingWidgetIds]' ids in the app's private preferences, as "id:allocatedAtMs" entries. Device
 * local by nature (host ids), and the app is excluded from backups anyway.
 */
class SharedPreferencesPendingIdStorage(context: Context) : PendingIdStorage {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): Map<Int, Long> =
        prefs.getStringSet(KEY, null).orEmpty().mapNotNull { entry ->
            val id = entry.substringBefore(':').toIntOrNull()
            val at = entry.substringAfter(':', "").toLongOrNull()
            if (id != null && at != null) id to at else null
        }.toMap()

    override fun save(ids: Map<Int, Long>) {
        prefs.edit().putStringSet(KEY, ids.map { (id, at) -> "$id:$at" }.toSet()).apply()
    }

    private companion object {
        const val FILE = "pending_widget_ids"
        const val KEY = "ids"
    }
}

package org.arkikeskus.launcher.feature.home

/**
 * Rows that other apps' pin requests added to home ([PinRequestViewModel]) and the workspace has
 * not shown yet, oldest first. The workspace takes them the next time it comes to the foreground at
 * rest — no drag, drawer closed — turns to the last one's page and pops each in, as Launcher3's
 * install queue does. Process-local on purpose: the rows themselves are stored, only the reveal is
 * lost with the process.
 */
class NewHomeItems {
    private val rowIds = ArrayList<Long>()

    @Synchronized
    fun add(rowId: Long) {
        rowIds += rowId
    }

    /** The ids added so far; they are forgotten with this call. */
    @Synchronized
    fun take(): List<Long> = rowIds.toList().also { rowIds.clear() }

    companion object {
        /** The one instance the confirmation and the home screen share. */
        val shared = NewHomeItems()
    }
}

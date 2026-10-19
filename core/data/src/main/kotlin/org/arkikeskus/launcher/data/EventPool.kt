package org.arkikeskus.launcher.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Events for ONE collector, delivered in batches. An item pools in a set while the collector is
 * busy (or not there yet) and reaches it together with the others once it is free: nothing is
 * dropped however long the wait, the backlog can never outgrow the number of distinct items
 * however often one repeats, and a collector that arrives late — a ViewModel re-created behind
 * Settings — gets what pooled before it. A shared flow with a fixed buffer dropped the event past
 * its capacity during such a wait, and that app's home rows then stayed stale.
 *
 * One collector at a time: the wake-ups are a conflated channel, so two collectors would split
 * the batches between them.
 */
internal class EventPool<T> {
    private val pending = LinkedHashSet<T>()
    private val wakeups = Channel<Unit>(Channel.CONFLATED)

    /** The pooled items, as one set per wake-up; an empty pool wakes nobody. */
    val batches: Flow<Set<T>> = flow {
        for (unused in wakeups) {
            val items = synchronized(pending) { pending.toSet().also { pending.clear() } }
            if (items.isNotEmpty()) emit(items)
        }
    }

    /** Pools [item] and wakes the collector; safe from any thread. */
    fun add(item: T) {
        synchronized(pending) { pending += item }
        wakeups.trySend(Unit)
    }
}

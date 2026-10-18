package org.arkikeskus.launcher.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Which of an icon's published shortcuts the long-press popup shows, and in what order (Launcher3's rule). */
class PopupShortcutOrderTest {
    private data class Shortcut(val id: String, val manifest: Boolean, val rank: Int)

    private fun m(rank: Int) = Shortcut("m$rank", manifest = true, rank = rank)
    private fun d(rank: Int) = Shortcut("d$rank", manifest = false, rank = rank)

    private fun popupOrder(vararg shortcuts: Shortcut): List<String> =
        sortAndFilterShortcuts(shortcuts.toList(), { it.manifest }, { !it.manifest }, { it.rank }).map { it.id }

    @Test fun theAppsFixedShortcutsComeFirstThenEachKindByRank() {
        // Manifest and dynamic ranks are separate sequences that both start at 0.
        assertThat(popupOrder(d(1), m(1), d(0), m(0))).containsExactly("m0", "m1", "d0", "d1").inOrder()
    }

    @Test fun atMostFourShortcutsAreShown() {
        assertThat(popupOrder(d(5), d(4), d(3), d(2), d(1), d(0))).containsExactly("d0", "d1", "d2", "d3").inOrder()
    }

    @Test fun twoDynamicShortcutsTakeTheLastFixedOnesSlots() {
        assertThat(popupOrder(m(0), m(1), m(2), m(3), m(4), d(0), d(1), d(2)))
            .containsExactly("m0", "m1", "d0", "d1").inOrder()
    }

    @Test fun aSingleDynamicShortcutTakesOnlyOneSlot() {
        assertThat(popupOrder(m(0), m(1), m(2), m(3), d(0))).containsExactly("m0", "m1", "m2", "d0").inOrder()
    }

    @Test fun aDynamicShortcutAlreadyAmongTheFirstFourCountsTowardsTheTwo() {
        assertThat(popupOrder(m(0), m(1), m(2), d(0), d(1), d(2))).containsExactly("m0", "m1", "d0", "d1").inOrder()
    }
}

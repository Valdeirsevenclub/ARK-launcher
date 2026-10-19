package org.arkikeskus.launcher.feature.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The hand-over from the confirmation to the home screen: taken once, in order. */
class NewHomeItemsTest {
    private val items = NewHomeItems()

    @Test fun nothingAddedIsNothingToShow() {
        assertThat(items.take()).isEmpty()
    }

    @Test fun theRowsAreTakenOldestFirst() {
        items.add(7L)
        items.add(3L)

        assertThat(items.take()).containsExactly(7L, 3L).inOrder()
    }

    @Test fun takingThemOnceIsTakingThemForGood() {
        items.add(7L)
        items.take()

        assertThat(items.take()).isEmpty()
    }

    @Test fun rowsAddedAfterATakeWaitForTheNextOne() {
        items.add(7L)
        items.take()
        items.add(8L)

        assertThat(items.take()).containsExactly(8L)
    }
}

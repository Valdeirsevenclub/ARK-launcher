package org.arkikeskus.launcher.data

import com.google.common.truth.Truth.assertThat
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.data.local.HomeItemEntity.Companion.HOME
import org.junit.Test

/** What the confirmation of another app's pin request decides, and where the item would go. */
class PinRequestDecisionTest {

    private val shortcut = PinItem.Shortcut("com.chat", "mia", userSerial = 0L, label = "Mia")

    private fun decide(
        rows: List<HomeItemEntity>,
        valid: Boolean = true,
        locked: Boolean = false,
        columns: Int = 4,
        gridRows: Int = 6,
    ) = decidePinRequest(shortcut, valid, locked, rows, columns, gridRows)

    private fun app(id: Long, page: Int, x: Int, y: Int, container: Long = HOME) = HomeItemEntity(
        id = id, containerId = container, packageName = "p$id", className = "C", page = page, cellX = x, cellY = y,
    )

    /** A full page: one widget covering every cell of a [columns]×[rows] grid. */
    private fun fullPage(page: Int, columns: Int = 4, rows: Int = 6) = HomeItemEntity(
        id = 100L + page, page = page, cellX = 0, cellY = 0, spanX = columns, spanY = rows,
        appWidgetId = page, widgetProvider = "p",
    )

    @Test fun aLockedDesktopIsReportedAndNothingIsPlaced() {
        assertThat(decide(emptyList(), locked = true)).isEqualTo(PinDecision.Locked)
    }

    @Test fun anInvalidRequestIsDismissedEvenWhenTheDesktopIsLocked() {
        assertThat(decide(emptyList(), valid = false, locked = true)).isEqualTo(PinDecision.Dismiss)
    }

    @Test fun anEmptyHomeTakesTheItemAtTheOriginOfTheFirstPage() {
        assertThat(decide(emptyList())).isEqualTo(PinDecision.Confirm(0, 0, 0))
    }

    @Test fun theFirstFreeCellInReadingOrderOnTheFirstPageIsUsed() {
        val rows = listOf(app(1, 0, 0, 0), app(2, 0, 1, 0), app(3, 0, 2, 0), app(4, 0, 3, 0), app(5, 0, 0, 1))
        assertThat(decide(rows)).isEqualTo(PinDecision.Confirm(0, 1, 1))
    }

    @Test fun aGapOnTheFirstPageBeatsAnEmptySecondPage() {
        val rows = listOf(app(1, 0, 0, 0), app(2, 0, 2, 0), app(3, 1, 0, 0))
        assertThat(decide(rows)).isEqualTo(PinDecision.Confirm(0, 1, 0))
    }

    @Test fun folderChildrenDoNotOccupyHomeCells() {
        // A folder at (0,0,0) with children whose cells are in-folder order, not home cells.
        val rows = listOf(
            HomeItemEntity(id = 1, folderName = "F", page = 0, cellX = 0, cellY = 0),
            app(2, 0, 1, 0, container = 1),
            app(3, 0, 2, 0, container = 1),
        )
        assertThat(decide(rows)).isEqualTo(PinDecision.Confirm(0, 1, 0))
    }

    @Test fun fullPagesSendTheItemToANewPageAtTheEnd() {
        val rows = listOf(fullPage(0), fullPage(1))
        assertThat(decide(rows)).isEqualTo(PinDecision.Confirm(2, 0, 0))
    }

    @Test fun theLastPageThePagerShowsStillTakesTheItem() {
        val rows = (0 until HomeLayoutRepository.MAX_PAGES - 1).map { fullPage(it) }
        assertThat(decide(rows)).isEqualTo(PinDecision.Confirm(HomeLayoutRepository.MAX_PAGES - 1, 0, 0))
    }

    /** The pager shows MAX_PAGES pages; index MAX_PAGES is only its trailing page (a drop target while
     *  dragging, the first page's copy with infinite scroll), where an item would never be seen. */
    @Test fun everyPageThePagerShowsFullMeansNoSpaceAndNoAcceptance() {
        val rows = (0 until HomeLayoutRepository.MAX_PAGES).map { fullPage(it) }
        assertThat(decide(rows)).isEqualTo(PinDecision.NoSpace)
    }

    @Test fun theLockWinsOverAFullHome() {
        val rows = (0 until HomeLayoutRepository.MAX_PAGES).map { fullPage(it) }
        assertThat(decide(rows, locked = true)).isEqualTo(PinDecision.Locked)
    }

    // --- app widgets: the footprint comes measured in cells ([PinSpans]), the grid decides the rest ---

    private val clock = PinItem.Widget("com.clock/.Analog", "Analog clock", ownProfile = true)

    private fun decideWidget(
        rows: List<HomeItemEntity>,
        spans: PinSpans,
        item: PinItem.Widget = clock,
        valid: Boolean = true,
        locked: Boolean = false,
        columns: Int = 4,
        gridRows: Int = 6,
    ) = decidePinRequest(item, valid, locked, rows, columns, gridRows, spans)

    @Test fun aWidgetTakesTheFirstRectangleThatIsFreeInFull() {
        // Row 0 full; (0,1) taken: a 2×2 can't start at column 0 of row 1, so it starts at column 1.
        val rows = listOf(app(1, 0, 0, 0), app(2, 0, 1, 0), app(3, 0, 2, 0), app(4, 0, 3, 0), app(5, 0, 0, 1))
        assertThat(decideWidget(rows, PinSpans(2, 2, 2, 2))).isEqualTo(PinDecision.Confirm(0, 1, 1, 2, 2))
    }

    @Test fun aWidgetThatFitsNoExistingPageGoesToANewOneAtTheEnd() {
        // A 4×2 needs two whole rows; page 0 has an icon on every other row, page 1 is full.
        val rows = listOf(app(1, 0, 0, 0), app(2, 0, 0, 2), app(3, 0, 0, 4), fullPage(1))
        assertThat(decideWidget(rows, PinSpans(4, 2, 4, 2))).isEqualTo(PinDecision.Confirm(2, 0, 0, 4, 2))
    }

    @Test fun aWidgetWithNoRoomUpToThePageLimitIsNoSpace() {
        val rows = (0 until HomeLayoutRepository.MAX_PAGES).map { fullPage(it) }
        assertThat(decideWidget(rows, PinSpans(2, 2, 1, 1))).isEqualTo(PinDecision.NoSpace)
    }

    @Test fun aWidgetWiderThanTheGridShrinksToItWhenItsMinimumFits() {
        // The picker gives a 5×7 default on a 4×6 grid a 4×6 footprint; so does the request.
        assertThat(decideWidget(emptyList(), PinSpans(5, 7, 2, 1))).isEqualTo(PinDecision.Confirm(0, 0, 0, 4, 6))
    }

    @Test fun aWidgetWhoseMinimumDoesNotFitTheGridIsTooBig() {
        assertThat(decideWidget(emptyList(), PinSpans(5, 2, 5, 2))).isEqualTo(PinDecision.TooBig)
        assertThat(decideWidget(emptyList(), PinSpans(2, 7, 1, 7))).isEqualTo(PinDecision.TooBig)
    }

    @Test fun aWorkProfileWidgetIsRefusedWithItsOwnState() {
        val work = clock.copy(ownProfile = false)
        assertThat(decideWidget(emptyList(), PinSpans(2, 2, 2, 2), item = work)).isEqualTo(PinDecision.WorkProfile)
    }

    @Test fun theLockAndAnInvalidRequestComeBeforeTheWidgetIsLookedAt() {
        val work = clock.copy(ownProfile = false)
        assertThat(decideWidget(emptyList(), PinSpans(9, 9, 9, 9), item = work, locked = true)).isEqualTo(PinDecision.Locked)
        assertThat(decideWidget(emptyList(), PinSpans(9, 9, 9, 9), item = work, valid = false)).isEqualTo(PinDecision.Dismiss)
    }

    @Test fun aShortcutIsStillOneCell() {
        assertThat(decide(listOf(app(1, 0, 0, 0)))).isEqualTo(PinDecision.Confirm(0, 1, 0, 1, 1))
    }
}

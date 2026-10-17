package org.arkikeskus.launcher.feature.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The decisions behind infinite scrolling: when the pager's tail shows a copy of page 0, when a
 *  drag at an end jumps, and how the copy borrows page 0's dot and widget views. */
class WorkspaceWrapTest {
    @Test
    fun theTailShowsPageZeroWhenInfiniteScrollWrapsTwoOrMorePages() {
        assertThat(tailShowsPageZero(infiniteScroll = true, pageCount = 3, dragActive = false, newPageLatched = false)).isTrue()
        assertThat(tailShowsPageZero(infiniteScroll = true, pageCount = 2, dragActive = false, newPageLatched = false)).isTrue()
    }

    @Test
    fun theTailStaysTheEmptyNewPageWhenTheSettingIsOff() {
        assertThat(tailShowsPageZero(infiniteScroll = false, pageCount = 3, dragActive = false, newPageLatched = false)).isFalse()
    }

    @Test
    fun aSinglePageHasNothingToWrapTo() {
        assertThat(tailShowsPageZero(infiniteScroll = true, pageCount = 1, dragActive = false, newPageLatched = false)).isFalse()
    }

    @Test
    fun aDragKeepsTheTailAsTheNewPageUntilThePagerSettlesOnARealPage() {
        assertThat(tailShowsPageZero(infiniteScroll = true, pageCount = 3, dragActive = true, newPageLatched = false)).isFalse()
        assertThat(tailShowsPageZero(infiniteScroll = true, pageCount = 3, dragActive = false, newPageLatched = true)).isFalse()
    }

    @Test
    fun aDragBackFromPageZeroJumpsForwardOntoTheCopy() {
        assertThat(wrapJumpPages(atStart = true, atEnd = false, pageCount = 3, tailShowsPageZero = true, leftEdgeAction = false))
            .isEqualTo(3)
    }

    @Test
    fun theLeftEdgeAppKeepsPageZeroFromWrappingBack() {
        assertThat(wrapJumpPages(atStart = true, atEnd = false, pageCount = 3, tailShowsPageZero = true, leftEdgeAction = true))
            .isEqualTo(0)
    }

    @Test
    fun aDragOnPastTheCopyJumpsBackOntoPageZeroEvenWithTheLeftEdgeApp() {
        assertThat(wrapJumpPages(atStart = false, atEnd = true, pageCount = 3, tailShowsPageZero = true, leftEdgeAction = false))
            .isEqualTo(-3)
        assertThat(wrapJumpPages(atStart = false, atEnd = true, pageCount = 3, tailShowsPageZero = true, leftEdgeAction = true))
            .isEqualTo(-3)
    }

    @Test
    fun noJumpWithoutTheCopyOrAwayFromTheEnds() {
        assertThat(wrapJumpPages(atStart = true, atEnd = false, pageCount = 3, tailShowsPageZero = false, leftEdgeAction = false))
            .isEqualTo(0)
        assertThat(wrapJumpPages(atStart = false, atEnd = true, pageCount = 3, tailShowsPageZero = false, leftEdgeAction = false))
            .isEqualTo(0)
        assertThat(wrapJumpPages(atStart = false, atEnd = false, pageCount = 3, tailShowsPageZero = true, leftEdgeAction = false))
            .isEqualTo(0)
    }

    @Test
    fun theCopyLightsPageZerosDotWhileTheNewPageLightsNone() {
        assertThat(dotForPagerPage(pagerPage = 3, pageCount = 3, tailShowsPageZero = true)).isEqualTo(0)
        assertThat(dotForPagerPage(pagerPage = 3, pageCount = 3, tailShowsPageZero = false)).isEqualTo(3)
        assertThat(dotForPagerPage(pagerPage = 1, pageCount = 3, tailShowsPageZero = true)).isEqualTo(1)
    }

    @Test
    fun theCopyIsOnScreenAsSoonAsThePagerMovesPastTheLastPage() {
        assertThat(tailCopyOnScreen(position = 2f, pageCount = 3)).isFalse()
        assertThat(tailCopyOnScreen(position = 2.01f, pageCount = 3)).isTrue()
        assertThat(tailCopyOnScreen(position = 3f, pageCount = 3)).isTrue()
        assertThat(tailCopyOnScreen(position = 0f, pageCount = 3)).isFalse()
    }

    @Test
    fun pageZerosWidgetViewsGoToWhicheverOfItsTwoInstancesIsOnScreen() {
        assertThat(ownsWidgetViews(page = 0, isTailCopy = false, copyOnScreen = false)).isTrue()
        assertThat(ownsWidgetViews(page = 0, isTailCopy = false, copyOnScreen = true)).isFalse()
        assertThat(ownsWidgetViews(page = 0, isTailCopy = true, copyOnScreen = true)).isTrue()
        assertThat(ownsWidgetViews(page = 0, isTailCopy = true, copyOnScreen = false)).isFalse()
    }

    @Test
    fun aScrollThatRunsPastTheLastPageIsCutBackToIt() {
        // From inside the pages: everything past the last page (2) is the overrun.
        assertThat(dropPageOverrun(before = 1.5f, after = 2.4f, lastPage = 2)).isWithin(1e-4f).of(0.4f)
        assertThat(dropPageOverrun(before = 2f, after = 2.3f, lastPage = 2)).isWithin(1e-4f).of(0.3f)
    }

    @Test
    fun aScrollThatStaysInsideThePagesIsLeftAlone() {
        assertThat(dropPageOverrun(before = 1f, after = 1.8f, lastPage = 2)).isEqualTo(0f)
        assertThat(dropPageOverrun(before = 2f, after = 1.6f, lastPage = 2)).isEqualTo(0f)
    }

    @Test
    fun aPagerAlreadyOnTheDropPageIsNotPulledBackByTheCut() {
        // After a drag ended there; the workspace's own retreat brings it back.
        assertThat(dropPageOverrun(before = 2.6f, after = 2.8f, lastPage = 2)).isEqualTo(0f)
    }

    @Test
    fun otherPagesAlwaysHostTheirOwnWidgets() {
        assertThat(ownsWidgetViews(page = 2, isTailCopy = false, copyOnScreen = true)).isTrue()
    }
}

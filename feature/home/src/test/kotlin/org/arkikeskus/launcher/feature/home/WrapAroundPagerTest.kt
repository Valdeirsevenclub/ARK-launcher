package org.arkikeskus.launcher.feature.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Infinite scrolling on a pager shaped like the home workspace: [PAGES] real pages plus the
 * trailing page, wired with the same overscroll and swap the workspace uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WrapAroundPagerTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var pager: PagerState
    private val wrapsOntoCopy = mutableListOf<Int>()
    private val swapsFromCopy = mutableListOf<PagerMoment>()

    /** Where the pager is, and whether it still moves, at the moment of a swap. */
    private data class PagerMoment(val page: Int, val scrolling: Boolean, val offsetFraction: Float)

    private fun setPager(startPage: Int, copy: Boolean = true, leftEdgeAction: Boolean = false) {
        compose.setContent {
            pager = rememberPagerState(initialPage = startPage) { PAGES + 1 }
            HorizontalPager(
                pager,
                Modifier.size(300.dp).testTag("pager"),
                overscrollEffect = rememberWrapAroundOverscroll(
                    pagerState = pager,
                    pageCount = PAGES,
                    tailShowsPageZero = copy,
                    leftEdgeAction = leftEdgeAction,
                    onWrap = { toCopy -> if (toCopy) wrapsOntoCopy += pager.currentPage },
                ),
            ) { Box(Modifier.fillMaxSize()) }
            PageZeroCopySwap(pager, PAGES, copy, onBeforeSwap = {
                swapsFromCopy += PagerMoment(pager.currentPage, pager.isScrollInProgress, pager.currentPageOffsetFraction)
            })
        }
    }

    private fun swipeOn() = compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
    private fun swipeBack() = compose.onNodeWithTag("pager").performTouchInput { swipeRight() }

    @Test
    fun swipingOnFromTheLastPageLandsOnTheFirst() {
        setPager(startPage = PAGES - 1)
        swipeOn()
        compose.waitForIdle()
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(pager.settledPage).isEqualTo(0)
    }

    @Test
    fun theSwapToPageZeroHappensOnlyOnceThePagerRestsOnTheCopy() {
        setPager(startPage = PAGES - 1)
        swipeOn()
        compose.waitForIdle()
        // Exactly once, on the copy, with the pager at rest there — not while it is still sliding.
        assertThat(swapsFromCopy).hasSize(1)
        val swap = swapsFromCopy.single()
        assertThat(swap.page).isEqualTo(PAGES)
        assertThat(swap.scrolling).isFalse()
        assertThat(swap.offsetFraction).isWithin(0.001f).of(0f)
    }

    @Test
    fun swipingBackFromTheFirstPageLandsOnTheLast() {
        setPager(startPage = 0)
        swipeBack()
        compose.waitForIdle()
        assertThat(pager.currentPage).isEqualTo(PAGES - 1)
    }

    @Test
    fun theJumpOntoTheCopyIsAnnouncedWhileStillOnPageZero() {
        setPager(startPage = 0)
        swipeBack()
        compose.waitForIdle()
        assertThat(wrapsOntoCopy).containsExactly(0)
    }

    @Test
    fun theLeftEdgeAppKeepsPageZeroFromWrappingBack() {
        setPager(startPage = 0, leftEdgeAction = true)
        swipeBack()
        compose.waitForIdle()
        assertThat(pager.currentPage).isEqualTo(0)
    }

    @Test
    fun withoutTheCopySwipingOnStillReachesTheEmptyTail() {
        setPager(startPage = PAGES - 1, copy = false)
        swipeOn()
        compose.waitForIdle()
        assertThat(pager.currentPage).isEqualTo(PAGES)
        assertThat(swapsFromCopy).isEmpty()
    }

    @Test
    fun withoutTheCopyPageZeroDoesNotWrapBack() {
        setPager(startPage = 0, copy = false)
        swipeBack()
        compose.waitForIdle()
        assertThat(pager.currentPage).isEqualTo(0)
    }

    @Test
    fun oneLongDragThroughTheCopyCarriesOnToPageOne() {
        setPager(startPage = PAGES - 1)
        compose.onNodeWithTag("pager").performTouchInput {
            // 1.6 pages: one to the copy, then 0.6 on past it.
            swipe(
                start = Offset(width - 5f, height / 2f),
                end = Offset(width - 5f - width * 1.6f, height / 2f),
                durationMillis = 1000,
            )
        }
        compose.waitForIdle()
        assertThat(pager.currentPage).isEqualTo(1)
    }

    private companion object {
        const val PAGES = 3
    }
}

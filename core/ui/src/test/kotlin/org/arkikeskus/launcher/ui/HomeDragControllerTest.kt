package org.arkikeskus.launcher.ui

import android.os.Process
import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import org.arkikeskus.launcher.model.AppItem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [HomeDragController.leftSource]: a cross-surface drag counts as "over the home screen" only once
 * the finger has left the surface it lifted from. Home, dock and drawer drags leave with their first
 * move; a drag inside the open folder's card moves without leaving (a reorder) until it crosses the
 * card's edge, so the home surfaces draw no drop cue and flip no page behind the card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeDragControllerTest {

    private val app = AppItem("p", "p.Main", Process.myUserHandle(), 0L, "App")
    private val c = HomeDragController()
    private val token = Any()

    @Test
    fun leftSourceIsFalseAfterStartAndTrueAfterBeginMove() {
        assertThat(c.start(app, DragSource.Drawer, Offset.Zero, token)).isTrue()
        assertThat(c.leftSource).isFalse()

        c.beginMove()

        assertThat(c.moving).isTrue()
        assertThat(c.leftSource).isTrue()
        assertThat(c.isIncomingFrom(DragSource.Drawer)).isTrue()
        assertThat(c.isIncomingFrom(DragSource.Dock)).isFalse()
    }

    @Test
    fun aFolderReorderMovesWithoutLeavingItsSource() {
        c.start(app, DragSource.Folder, Offset.Zero, token)

        c.beginMove(leftSource = false)

        assertThat(c.moving).isTrue()
        assertThat(c.leftSource).isFalse()
        assertThat(c.isIncomingFrom(DragSource.Folder)).isFalse()
        c.update(Offset(5f, 5f))
        assertThat(c.leftSource).isFalse()

        c.markLeftSource()

        assertThat(c.isIncomingFrom(DragSource.Folder)).isTrue()
        assertThat(c.isIncomingFrom(DragSource.Drawer, DragSource.Dock, DragSource.Folder)).isTrue()
    }

    @Test
    fun stopAndCancelResetLeftSource() {
        c.start(app, DragSource.Dock, Offset.Zero, token)
        c.beginMove()
        c.stop(token)
        assertThat(c.leftSource).isFalse()
        assertThat(c.isDragging).isFalse()

        c.start(app, DragSource.Dock, Offset.Zero, token)
        c.beginMove()
        c.cancel()
        assertThat(c.leftSource).isFalse()
        assertThat(c.isDragging).isFalse()
    }

    @Test
    fun isIncomingFromIsFalseWhileOnlyLifted() {
        c.start(app, DragSource.Dock, Offset.Zero, token)

        assertThat(c.isIncomingFrom(DragSource.Dock)).isFalse()
    }
}

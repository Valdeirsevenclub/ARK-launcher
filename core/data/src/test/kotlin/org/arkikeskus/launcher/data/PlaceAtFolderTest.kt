package org.arkikeskus.launcher.data

import android.os.Process
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.arkikeskus.launcher.data.local.HomeItemDao
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.data.local.HomeItemEntity.Companion.HOME
import org.arkikeskus.launcher.data.local.LauncherDatabase
import org.arkikeskus.launcher.model.AppItem
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * An icon dropped from the drawer or the dock onto a home cell that holds an app makes a folder of
 * the two, and onto a folder joins it — what a drop between two home icons already does. Only an
 * app folds: a shortcut, a widget or a builtin under the finger still sends the icon to the first
 * free cell, as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaceAtFolderTest {

    private lateinit var db: LauncherDatabase
    private lateinit var dao: HomeItemDao
    private lateinit var repo: HomeLayoutRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LauncherDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.homeItemDao()
        repo = HomeLayoutRepository(db, dao, FakeAppRowResolver())
    }

    @After
    fun tearDown() = db.close()

    private fun app(name: String) = AppItem("com.$name", "Main", Process.myUserHandle(), 0L, name)

    private suspend fun homeRow(app: AppItem) = dao.getByKey(HOME, app.packageName, app.className, app.userSerial)

    private suspend fun childRow(folderId: Long, app: AppItem) =
        dao.getByKey(folderId, app.packageName, app.className, app.userSerial)

    private suspend fun insertOnHome(app: AppItem, page: Int, cellX: Int, cellY: Int) =
        dao.insert(HomeItemEntity(packageName = app.packageName, className = app.className, page = page, cellX = cellX, cellY = cellY))

    private suspend fun place(app: AppItem, cellX: Int, cellY: Int) =
        repo.placeAt(app, 0, cellX, cellY, columns = 2, rows = 2, folderName = "Folder")

    private suspend fun folderAt(cellX: Int, cellY: Int): HomeItemEntity {
        val row = dao.getAt(HOME, 0, cellX, cellY)
        assertThat(row).isNotNull()
        assertThat(row!!.isFolder).isTrue()
        return row
    }

    @Test
    fun aNewAppDroppedOnAHomeAppMakesAFolderOfTheTwo() = runTest {
        val target = app("target")
        val dropped = app("dropped")
        insertOnHome(target, 0, 0, 0)

        assertThat(place(dropped, 0, 0)).isEqualTo(PlaceResult.Placed)

        val folder = folderAt(0, 0)
        assertThat(folder.folderName).isEqualTo("Folder")
        assertThat(dao.getContainer(HOME)).hasSize(1)
        assertThat(childRow(folder.id, target)?.cellX).isEqualTo(0)
        assertThat(childRow(folder.id, dropped)?.cellX).isEqualTo(1)
        assertThat(homeRow(dropped)).isNull()
    }

    @Test
    fun aNewAppDroppedOnAFolderJoinsItLast() = runTest {
        val a = app("a")
        val b = app("b")
        val c = app("c")
        insertOnHome(a, 0, 0, 0)
        insertOnHome(b, 0, 1, 0)
        val folderId = repo.createFolder(a, b, "Pair")

        assertThat(place(c, 0, 0)).isEqualTo(PlaceResult.Placed)

        assertThat(dao.childCount(folderId)).isEqualTo(3)
        assertThat(childRow(folderId, c)?.cellX).isEqualTo(2)
        assertThat(homeRow(c)).isNull()
        assertThat(dao.getContainer(HOME).single().id).isEqualTo(folderId)
    }

    @Test
    fun aHomeAppDroppedOnAnotherHomeAppMakesAFolderAndFreesItsOwnCell() = runTest {
        val a = app("a")
        val b = app("b")
        insertOnHome(a, 0, 0, 0)
        insertOnHome(b, 0, 1, 0)

        assertThat(place(b, 0, 0)).isEqualTo(PlaceResult.Placed)

        val folder = folderAt(0, 0)
        assertThat(dao.getAt(HOME, 0, 1, 0)).isNull()
        assertThat(dao.getContainer(HOME)).hasSize(1)
        assertThat(childRow(folder.id, a)?.cellX).isEqualTo(0)
        assertThat(childRow(folder.id, b)?.cellX).isEqualTo(1)
    }

    @Test
    fun aHomeAppDroppedOnAFolderMovesIntoIt() = runTest {
        val a = app("a")
        val b = app("b")
        val c = app("c")
        insertOnHome(a, 0, 0, 0)
        insertOnHome(b, 0, 1, 0)
        val folderId = repo.createFolder(a, b, "Pair")
        insertOnHome(c, 0, 1, 0)

        assertThat(place(c, 0, 0)).isEqualTo(PlaceResult.Placed)

        assertThat(dao.getAt(HOME, 0, 1, 0)).isNull()
        assertThat(dao.childCount(folderId)).isEqualTo(3)
        assertThat(childRow(folderId, c)?.cellX).isEqualTo(2)
    }

    @Test
    fun anAppTheFolderAlreadyHoldsIsNotAddedTwice() = runTest {
        val a = app("a")
        val b = app("b")
        insertOnHome(a, 0, 0, 0)
        insertOnHome(b, 0, 1, 0)
        val folderId = repo.createFolder(a, b, "Pair")

        assertThat(place(a, 0, 0)).isEqualTo(PlaceResult.Placed)

        assertThat(dao.childCount(folderId)).isEqualTo(2)
        assertThat(dao.getContainer(folderId).count { it.packageName == a.packageName }).isEqualTo(1)
        assertThat(homeRow(a)).isNull()
    }

    @Test
    fun aShortcutUnderTheDropStillSendsTheAppToTheFirstFreeCell() = runTest {
        val dropped = app("dropped")
        dao.insert(
            HomeItemEntity(packageName = "com.chat", className = "Main", shortcutId = "compose", page = 0, cellX = 0, cellY = 0),
        )

        assertThat(place(dropped, 0, 0)).isEqualTo(PlaceResult.Placed)

        val row = homeRow(dropped)
        assertThat(row).isNotNull()
        assertThat(Triple(row!!.page, row.cellX, row.cellY)).isEqualTo(Triple(0, 1, 0))
        assertThat(dao.getAt(HOME, 0, 0, 0)?.shortcutId).isEqualTo("compose")
        assertThat(dao.getContainer(HOME)).hasSize(2)
    }

    @Test
    fun aFreeCellStillTakesTheAppItself() = runTest {
        val dropped = app("dropped")

        assertThat(place(dropped, 1, 1)).isEqualTo(PlaceResult.Placed)

        val row = homeRow(dropped)
        assertThat(Triple(row!!.page, row.cellX, row.cellY)).isEqualTo(Triple(0, 1, 1))
    }
}

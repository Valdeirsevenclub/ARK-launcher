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
 * Moving an app inside an open folder and out of it. Reordering works on the folder's child rows
 * (a hidden, unresolved child keeps its place next to its neighbours) and never trips the unique
 * (container, page, cellX, cellY) index. A move out follows [HomeLayoutRepository.placeAt]'s cell
 * rules with the child row itself as the row that moves: an empty cell takes it, an app there makes
 * a folder, a folder there takes it in, a widget's cell sends it to the first free cell, a full home
 * (or a page the pager never shows) leaves it in the folder and says so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FolderMoveTest {

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

    private val a = app("a")
    private val b = app("b")
    private val c = app("c")
    private val d = app("d")

    private fun app(name: String) = AppItem("com.$name", "Main", Process.myUserHandle(), 0L, name)

    private suspend fun homeRow(app: AppItem) = dao.getByKey(HOME, app.packageName, app.className, app.userSerial)

    private suspend fun childRow(folderId: Long, app: AppItem) =
        dao.getByKey(folderId, app.packageName, app.className, app.userSerial)

    private suspend fun insertOnHome(app: AppItem, page: Int, cellX: Int, cellY: Int) =
        dao.insert(HomeItemEntity(packageName = app.packageName, className = app.className, page = page, cellX = cellX, cellY = cellY))

    private suspend fun folderWith(vararg apps: AppItem, at: Pair<Int, Int> = 0 to 0): Long {
        val id = dao.insert(HomeItemEntity(folderName = "Pair", page = 0, cellX = at.first, cellY = at.second))
        apps.forEachIndexed { i, app ->
            dao.insert(HomeItemEntity(containerId = id, packageName = app.packageName, className = app.className, page = 0, cellX = i, cellY = 0))
        }
        return id
    }

    private suspend fun order(folderId: Long) = dao.getContainerOrdered(folderId).map { it.packageName }

    private suspend fun moveOut(app: AppItem, folderId: Long, page: Int, cellX: Int, cellY: Int) =
        repo.moveOutOfFolderTo(app, folderId, page, cellX, cellY, columns = 2, rows = 2, folderName = "Folder")

    private suspend fun assertIndexed(folderId: Long) {
        dao.getContainerOrdered(folderId).forEachIndexed { i, row ->
            assertThat(row.page).isEqualTo(0)
            assertThat(row.cellY).isEqualTo(0)
            assertThat(row.cellX).isEqualTo(i)
        }
    }

    // --- moveWithinFolder ---

    @Test
    fun movingToTheFrontReindexesWithoutACollision() = runTest {
        val id = folderWith(a, b, c)

        assertThat(repo.moveWithinFolder(id, c, before = a)).isTrue()

        assertThat(order(id)).containsExactly("com.c", "com.a", "com.b").inOrder()
        assertIndexed(id)
    }

    @Test
    fun movingToTheEndAppendsWhenBeforeIsNull() = runTest {
        val id = folderWith(a, b, c)

        assertThat(repo.moveWithinFolder(id, a, before = null)).isTrue()

        assertThat(order(id)).containsExactly("com.b", "com.c", "com.a").inOrder()
        assertIndexed(id)
    }

    @Test
    fun movingBeforeANeighbourInsertsThere() = runTest {
        val id = folderWith(a, b, c, d)

        assertThat(repo.moveWithinFolder(id, d, before = b)).isTrue()

        assertThat(order(id)).containsExactly("com.a", "com.d", "com.b", "com.c").inOrder()
        assertIndexed(id)
    }

    @Test
    fun aHiddenChildKeepsItsPlaceNextToItsNeighbours() = runTest {
        // "hidden" stands for a child the home screen does not show (a disabled app, a paused
        // profile): the move only touches the moving row and its new neighbour.
        val hidden = app("hidden")
        val id = folderWith(a, hidden, b, c)

        assertThat(repo.moveWithinFolder(id, c, before = a)).isTrue()

        assertThat(order(id)).containsExactly("com.c", "com.a", "com.hidden", "com.b").inOrder()
        assertIndexed(id)
    }

    @Test
    fun aVanishedNeighbourAppendsTheMovedApp() = runTest {
        val id = folderWith(a, b, c)

        assertThat(repo.moveWithinFolder(id, a, before = app("gone"))).isTrue()

        assertThat(order(id)).containsExactly("com.b", "com.c", "com.a").inOrder()
        assertIndexed(id)
    }

    @Test
    fun anAppNotInTheFolderIsRefused() = runTest {
        val id = folderWith(a, b, c)

        assertThat(repo.moveWithinFolder(id, app("x"), before = null)).isFalse()

        assertThat(order(id)).containsExactly("com.a", "com.b", "com.c").inOrder()
    }

    // --- moveOutOfFolderTo ---

    @Test
    fun aMoveToAnEmptyCellMovesTheChildRow() = runTest {
        val id = folderWith(a, b, c)
        val childId = childRow(id, c)!!.id

        assertThat(moveOut(c, id, 0, 1, 1)).isEqualTo(PlaceResult.Placed)

        val home = homeRow(c)
        assertThat(home).isNotNull()
        assertThat(home!!.id).isEqualTo(childId)
        assertThat(Triple(home.page, home.cellX, home.cellY)).isEqualTo(Triple(0, 1, 1))
        assertThat(order(id)).containsExactly("com.a", "com.b").inOrder()
        assertIndexed(id)
    }

    @Test
    fun aMoveOntoAHomeAppMakesAFolderHoldingTargetThenChild() = runTest {
        val target = app("target")
        val id = folderWith(a, b, c)
        insertOnHome(target, 0, 1, 0)

        assertThat(moveOut(c, id, 0, 1, 0)).isEqualTo(PlaceResult.Placed)

        val folder = dao.getAt(HOME, 0, 1, 0)
        assertThat(folder).isNotNull()
        assertThat(folder!!.isFolder).isTrue()
        assertThat(folder.folderName).isEqualTo("Folder")
        assertThat(childRow(folder.id, target)?.cellX).isEqualTo(0)
        assertThat(childRow(folder.id, c)?.cellX).isEqualTo(1)
        assertThat(homeRow(c)).isNull()
        assertThat(order(id)).containsExactly("com.a", "com.b").inOrder()
    }

    @Test
    fun aMoveOntoAFolderJoinsItAtTheEnd() = runTest {
        val id = folderWith(a, b, c)
        val second = folderWith(app("x"), app("y"), at = 1 to 0)

        assertThat(moveOut(c, id, 0, 1, 0)).isEqualTo(PlaceResult.Placed)

        assertThat(childRow(second, c)?.cellX).isEqualTo(2)
        assertThat(order(id)).containsExactly("com.a", "com.b").inOrder()
    }

    @Test
    fun aMoveOntoTheSourceFoldersOwnCellChangesNothing() = runTest {
        val id = folderWith(a, b, c)

        assertThat(moveOut(c, id, 0, 0, 0)).isEqualTo(PlaceResult.Placed)

        assertThat(order(id)).containsExactly("com.a", "com.b", "com.c").inOrder()
        assertThat(homeRow(c)).isNull()
    }

    @Test
    fun aMoveOntoAWidgetCellTakesTheFirstFreeCell() = runTest {
        val id = folderWith(a, b, c)
        dao.insert(HomeItemEntity(page = 0, cellX = 1, cellY = 0, spanX = 1, spanY = 2, appWidgetId = 1, widgetProvider = "w/.P"))

        assertThat(moveOut(c, id, 0, 1, 1)).isEqualTo(PlaceResult.Placed)

        val home = homeRow(c)
        assertThat(home).isNotNull()
        assertThat(Triple(home!!.page, home.cellX, home.cellY)).isEqualTo(Triple(0, 0, 1))
        assertThat(order(id)).containsExactly("com.a", "com.b").inOrder()
    }

    @Test
    fun aFullHomeKeepsTheAppInTheFolderAndSaysSo() = runTest {
        val id = folderWith(a, b, c)
        dao.insert(HomeItemEntity(page = 0, cellX = 1, cellY = 0, spanX = 1, spanY = 2, appWidgetId = 1, widgetProvider = "w/.P"))
        dao.insert(HomeItemEntity(page = 0, cellX = 0, cellY = 1, spanX = 1, spanY = 1, appWidgetId = 2, widgetProvider = "w/.Q"))
        for (page in 1 until HomeLayoutRepository.MAX_PAGES) {
            dao.insert(HomeItemEntity(page = page, cellX = 0, cellY = 0, spanX = 2, spanY = 2, appWidgetId = 1000 + page, widgetProvider = "w/.Full"))
        }

        assertThat(moveOut(c, id, 0, 1, 1)).isEqualTo(PlaceResult.HomeFull)

        assertThat(order(id)).containsExactly("com.a", "com.b", "com.c").inOrder()
        assertThat(homeRow(c)).isNull()
    }

    @Test
    fun anUnshownPageIsHomeFull() = runTest {
        val id = folderWith(a, b, c)

        assertThat(moveOut(c, id, HomeLayoutRepository.MAX_PAGES, 0, 0)).isEqualTo(PlaceResult.HomeFull)

        assertThat(order(id)).containsExactly("com.a", "com.b", "com.c").inOrder()
        assertThat(homeRow(c)).isNull()
    }

    @Test
    fun theFolderDissolvesWhenOneChildRemains() = runTest {
        val id = folderWith(a, b)

        assertThat(moveOut(b, id, 0, 1, 1)).isEqualTo(PlaceResult.Placed)

        assertThat(dao.getById(id)).isNull()
        val homeA = homeRow(a)
        assertThat(Triple(homeA!!.page, homeA.cellX, homeA.cellY)).isEqualTo(Triple(0, 0, 0))
        val homeB = homeRow(b)
        assertThat(Triple(homeB!!.page, homeB.cellX, homeB.cellY)).isEqualTo(Triple(0, 1, 1))
    }

    @Test
    fun anAppWithAHomeIconMergesInsteadOfDuplicating() = runTest {
        val id = folderWith(a, b, c)
        insertOnHome(c, 0, 1, 1)

        assertThat(moveOut(c, id, 0, 0, 1)).isEqualTo(PlaceResult.Placed)

        val homeRows = dao.getContainer(HOME).filter { it.packageName == c.packageName }
        assertThat(homeRows).hasSize(1)
        assertThat(Triple(homeRows[0].page, homeRows[0].cellX, homeRows[0].cellY)).isEqualTo(Triple(0, 1, 1))
        assertThat(childRow(id, c)).isNull()
        assertThat(order(id)).containsExactly("com.a", "com.b").inOrder()
    }

    // --- removeChild ---

    @Test
    fun removeChildReindexesAndDissolves() = runTest {
        val id = folderWith(a, b, c)

        repo.removeChild(a, id)
        assertThat(order(id)).containsExactly("com.b", "com.c").inOrder()
        assertIndexed(id)

        repo.removeChild(b, id)
        assertThat(dao.getById(id)).isNull()
        val homeC = homeRow(c)
        assertThat(Triple(homeC!!.page, homeC.cellX, homeC.cellY)).isEqualTo(Triple(0, 0, 0))

        repo.removeChild(app("x"), id) // the folder is gone: nothing to do, nothing thrown
    }
}

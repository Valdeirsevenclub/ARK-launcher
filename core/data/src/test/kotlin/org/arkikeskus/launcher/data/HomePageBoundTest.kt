package org.arkikeskus.launcher.data

import android.os.Process
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.arkikeskus.launcher.data.HomeLayoutRepository.Companion.MAX_PAGES
import org.arkikeskus.launcher.data.local.HomeItemDao
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.data.local.HomeItemEntity.Companion.HOME
import org.arkikeskus.launcher.data.local.LauncherDatabase
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.WidgetPlacement
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The launcher's own placements stay on the pages the pager shows, `0 until MAX_PAGES`. Index
 * MAX_PAGES is only the pager's trailing drop page — with infinite scroll the first page's copy —
 * so a row stored there is never seen at rest. Every path that places a row refuses instead and
 * tells its caller, so the screen can say that the home screen is full.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomePageBoundTest {

    private lateinit var db: LauncherDatabase
    private lateinit var dao: HomeItemDao
    private lateinit var repo: HomeLayoutRepository

    private val lastShown = MAX_PAGES - 1
    private val trailing = MAX_PAGES

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

    private suspend fun rowOf(app: AppItem) = dao.getByKey(HOME, app.packageName, app.className, app.userSerial)

    private suspend fun homeRows() = dao.getContainer(HOME)

    /** On a 1×1 grid one row fills a page: [pages] filler rows fill pages 0 until [pages]. */
    private suspend fun fillPages(pages: Int, skip: Set<Int> = emptySet()) {
        for (page in 0 until pages) {
            if (page in skip) continue
            dao.insert(HomeItemEntity(packageName = "com.filler$page", className = "Main", page = page, cellX = 0, cellY = 0))
        }
    }

    // --- the rule itself ---

    @Test
    fun theShownPagesAreZeroUntilMaxPages() {
        assertThat(HomeLayoutRepository.isShownPage(0)).isTrue()
        assertThat(HomeLayoutRepository.isShownPage(lastShown)).isTrue()
        assertThat(HomeLayoutRepository.isShownPage(trailing)).isFalse()
        assertThat(HomeLayoutRepository.isShownPage(-1)).isFalse()
    }

    // --- first-free placements ---

    @Test
    fun addToHome_refusesWhenEveryShownPageIsFull() = runTest {
        fillPages(MAX_PAGES)

        assertThat(repo.addToHome(app("a"), columns = 1, rows = 1)).isFalse()

        assertThat(rowOf(app("a"))).isNull()
        assertThat(homeRows()).hasSize(MAX_PAGES)
    }

    @Test
    fun addToHome_fillsTheLastShownPage() = runTest {
        fillPages(lastShown)

        assertThat(repo.addToHome(app("a"), columns = 1, rows = 1)).isTrue()

        assertThat(rowOf(app("a"))?.page).isEqualTo(lastShown)
    }

    @Test
    fun addToHome_reportsAnAppAlreadyOnHomeAsPlaced() = runTest {
        fillPages(MAX_PAGES, skip = setOf(7))
        dao.insert(HomeItemEntity(packageName = "com.a", className = "Main", page = 7, cellX = 0, cellY = 0))

        assertThat(repo.addToHome(app("a"), columns = 1, rows = 1)).isTrue()

        assertThat(rowOf(app("a"))?.page).isEqualTo(7)
    }

    @Test
    fun addShortcut_refusesWhenEveryShownPageIsFull() = runTest {
        fillPages(MAX_PAGES)

        assertThat(repo.addShortcut("com.chat", "mia", 0L, columns = 1, rows = 1)).isFalse()

        assertThat(homeRows().none { it.isShortcut }).isTrue()
    }

    @Test
    fun addShortcut_fillsTheLastShownPage() = runTest {
        fillPages(lastShown)

        assertThat(repo.addShortcut("com.chat", "mia", 0L, columns = 1, rows = 1)).isTrue()

        assertThat(homeRows().single { it.isShortcut }.page).isEqualTo(lastShown)
    }

    @Test
    fun addBuiltin_refusesWhenEveryShownPageIsFull() = runTest {
        fillPages(MAX_PAGES)

        assertThat(repo.addBuiltin(HomeItemEntity.BUILTIN_BATTERY, 1, 1, columns = 1, rows = 1)).isFalse()

        assertThat(homeRows().none { it.isBuiltin }).isTrue()
    }

    @Test
    fun addBuiltin_fillsTheLastShownPage() = runTest {
        fillPages(lastShown)

        assertThat(repo.addBuiltin(HomeItemEntity.BUILTIN_BATTERY, 1, 1, columns = 1, rows = 1)).isTrue()

        assertThat(homeRows().single { it.isBuiltin }.page).isEqualTo(lastShown)
    }

    @Test
    fun removeFromFolder_keepsTheAppInTheFolderWhenHomeIsFull() = runTest {
        // A 2×1 grid. Page 7 holds the folder of a and b plus a filler; every other page is full.
        for (page in 0 until MAX_PAGES) {
            if (page == 7) continue
            dao.insert(HomeItemEntity(packageName = "com.l$page", className = "Main", page = page, cellX = 0, cellY = 0))
            dao.insert(HomeItemEntity(packageName = "com.r$page", className = "Main", page = page, cellX = 1, cellY = 0))
        }
        dao.insert(HomeItemEntity(packageName = "com.a", className = "Main", page = 7, cellX = 0, cellY = 0))
        dao.insert(HomeItemEntity(packageName = "com.b", className = "Main", page = 7, cellX = 1, cellY = 0))
        val folderId = repo.createFolder(app("a"), app("b"), "Pair")
        dao.insert(HomeItemEntity(packageName = "com.filler7", className = "Main", page = 7, cellX = 1, cellY = 0))

        assertThat(repo.removeFromFolder(app("a"), folderId, columns = 2, rows = 1)).isFalse()

        assertThat(dao.getContainer(folderId).map { it.packageName }).containsExactly("com.a", "com.b")
        assertThat(rowOf(app("a"))).isNull()
        assertThat(dao.getById(folderId)?.isFolder).isTrue()
    }

    @Test
    fun removeFromFolder_mergesIntoTheAppsHomeIconWithoutNeedingRoom() = runTest {
        // a is both inside the folder and directly on home; taking it out just drops the child row.
        fillPages(MAX_PAGES, skip = setOf(0, 1))
        dao.insert(HomeItemEntity(packageName = "com.a", className = "Main", page = 0, cellX = 0, cellY = 0))
        dao.insert(HomeItemEntity(packageName = "com.b", className = "Main", page = 1, cellX = 0, cellY = 0))
        val folderId = repo.createFolder(app("a"), app("b"), "Pair")
        dao.insert(HomeItemEntity(packageName = "com.a", className = "Main", page = 1, cellX = 0, cellY = 0))
        dao.insert(HomeItemEntity(packageName = "com.c", className = "Main", containerId = folderId, page = 0, cellX = 2, cellY = 0))

        assertThat(repo.removeFromFolder(app("a"), folderId, columns = 1, rows = 1)).isTrue()

        assertThat(dao.getContainer(folderId).map { it.packageName }).containsExactly("com.b", "com.c")
        assertThat(rowOf(app("a"))?.page).isEqualTo(1)
    }

    // --- placements at a given cell ---

    @Test
    fun placeAt_refusesTheTrailingPageWhenHomeIsFull() = runTest {
        fillPages(MAX_PAGES)

        assertThat(repo.placeAt(app("a"), trailing, 0, 0, columns = 1, rows = 1)).isEqualTo(PlaceResult.HomeFull)

        assertThat(rowOf(app("a"))).isNull()
    }

    @Test
    fun placeAt_fallsBackFromTheTrailingPageToAShownOne() = runTest {
        // A drop on the pager's trailing page while there is room elsewhere lands on the first free cell.
        fillPages(MAX_PAGES, skip = setOf(4))

        assertThat(repo.placeAt(app("a"), trailing, 0, 0, columns = 1, rows = 1)).isEqualTo(PlaceResult.Placed)

        assertThat(rowOf(app("a"))?.page).isEqualTo(4)
    }

    @Test
    fun placeAt_fallsBackOnlyToShownPages() = runTest {
        fillPages(MAX_PAGES)

        assertThat(repo.placeAt(app("a"), 0, 0, 0, columns = 1, rows = 1)).isEqualTo(PlaceResult.HomeFull)

        assertThat(rowOf(app("a"))).isNull()
    }

    @Test
    fun placeAt_acceptsTheLastShownPage() = runTest {
        assertThat(repo.placeAt(app("a"), lastShown, 0, 0, columns = 1, rows = 1)).isEqualTo(PlaceResult.Placed)

        assertThat(rowOf(app("a"))?.page).isEqualTo(lastShown)
    }

    @Test
    fun placeAt_movesAnAppAlreadyOnHomeOnlyWithinShownPages() = runTest {
        dao.insert(HomeItemEntity(packageName = "com.a", className = "Main", page = 3, cellX = 0, cellY = 0))

        assertThat(repo.placeAt(app("a"), trailing, 0, 0, columns = 1, rows = 1)).isEqualTo(PlaceResult.HomeFull)
        assertThat(rowOf(app("a"))?.page).isEqualTo(3)

        assertThat(repo.placeAt(app("a"), lastShown, 0, 0, columns = 1, rows = 1)).isEqualTo(PlaceResult.Placed)
        assertThat(rowOf(app("a"))?.page).isEqualTo(lastShown)
    }

    @Test
    fun placeAt_reportsAWidgetCellAsBlockedForAnAppAlreadyOnHome() = runTest {
        dao.insert(HomeItemEntity(packageName = "com.a", className = "Main", page = 0, cellX = 0, cellY = 0))
        dao.insert(HomeItemEntity(page = 1, cellX = 0, cellY = 0, appWidgetId = 9, widgetProvider = "p"))

        assertThat(repo.placeAt(app("a"), 1, 0, 0, columns = 1, rows = 1)).isEqualTo(PlaceResult.Blocked)

        assertThat(rowOf(app("a"))?.page).isEqualTo(0)
    }

    @Test
    fun moveItem_refusesTheTrailingPage() = runTest {
        dao.insert(HomeItemEntity(packageName = "com.a", className = "Main", page = 3, cellX = 0, cellY = 0))

        assertThat(repo.moveItem(app("a"), trailing, 0, 0)).isFalse()

        assertThat(rowOf(app("a"))?.page).isEqualTo(3)
    }

    @Test
    fun moveItem_acceptsTheLastShownPage() = runTest {
        dao.insert(HomeItemEntity(packageName = "com.a", className = "Main", page = 3, cellX = 0, cellY = 0))

        assertThat(repo.moveItem(app("a"), lastShown, 0, 0)).isTrue()

        assertThat(rowOf(app("a"))?.page).isEqualTo(lastShown)
    }

    @Test
    fun moveFolder_refusesTheTrailingPage() = runTest {
        dao.insert(HomeItemEntity(packageName = "com.a", className = "Main", page = 3, cellX = 0, cellY = 0))
        dao.insert(HomeItemEntity(packageName = "com.b", className = "Main", page = 3, cellX = 1, cellY = 0))
        val folderId = repo.createFolder(app("a"), app("b"), "Pair")

        assertThat(repo.moveFolder(folderId, trailing, 0, 0)).isFalse()

        assertThat(dao.getById(folderId)?.page).isEqualTo(3)
    }

    @Test
    fun setWidgetBounds_refusesTheTrailingPage() = runTest {
        val rowId = dao.insert(HomeItemEntity(page = 3, cellX = 0, cellY = 0, appWidgetId = 9, widgetProvider = "p"))

        assertThat(repo.setWidgetBounds(rowId, trailing, 0, 0, 1, 1, columns = 1, rows = 1)).isFalse()

        assertThat(dao.getById(rowId)?.page).isEqualTo(3)
    }

    @Test
    fun addWidgetAt_refusesTheTrailingPage() = runTest {
        val placed = repo.addWidgetAt(9, "p", null, WidgetPlacement(trailing, 0, 0, 1, 1), columns = 1, rows = 1)

        assertThat(placed).isFalse()
        assertThat(homeRows()).isEmpty()
    }

    @Test
    fun addWidgetAt_acceptsTheLastShownPage() = runTest {
        val placed = repo.addWidgetAt(9, "p", null, WidgetPlacement(lastShown, 0, 0, 1, 1), columns = 1, rows = 1)

        assertThat(placed).isTrue()
        assertThat(homeRows().single().page).isEqualTo(lastShown)
    }
}

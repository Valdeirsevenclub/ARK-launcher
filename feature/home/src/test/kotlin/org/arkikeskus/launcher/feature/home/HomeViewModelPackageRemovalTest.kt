package org.arkikeskus.launcher.feature.home

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Looper
import android.os.Process
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.data.local.HomeItemEntity.Companion.HOME
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * An uninstall's home-row cleanup. The removal reaches the ViewModel through a pool that also keeps
 * it for a ViewModel created later (the activity was destroyed behind Settings), so by the time it is
 * handled the app may be back — reinstalled, with a shortcut already pinned through the confirmation
 * that runs without a ViewModel. Rows go only while the app is still gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HomeViewModelPackageRemovalTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val launcherApps = shadowOf(context.getSystemService(LauncherApps::class.java))

    private suspend fun ProductionWiring.homeRow(packageName: String): Long =
        dao.insert(HomeItemEntity(packageName = packageName, className = "Main", page = 0, cellX = 0, cellY = 0))

    private fun installed(packageName: String) {
        launcherApps.addApplicationInfo(Process.myUserHandle(), packageName, ApplicationInfo().also { it.packageName = packageName })
    }

    @Test fun aRemovalHandledAfterTheAppCameBackKeepsItsRows() = runTest {
        val wiring = ProductionWiring(context, this)
        try {
            // Uninstalled while no ViewModel exists: the removal pools.
            launcherApps.notifyPackageRemoved("com.chat")
            shadowOf(Looper.getMainLooper()).idle()
            // Reinstalled, and a shortcut of it pinned on home through the confirmation.
            installed("com.chat")
            val row = wiring.homeRow("com.chat")

            val viewModel = wiring.homeViewModel()
            settle()

            assertThat(wiring.dao.getContainer(HOME).map { it.id }).containsExactly(row)
            wiring.clear(viewModel)
        } finally {
            wiring.close()
        }
    }

    @Test fun aRemovalOfAnAppStillGoneDropsItsRows() = runTest {
        val wiring = ProductionWiring(context, this)
        try {
            launcherApps.notifyPackageRemoved("com.gone")
            shadowOf(Looper.getMainLooper()).idle()
            wiring.homeRow("com.gone")

            val viewModel = wiring.homeViewModel()
            awaitUntil("the rows gone") { wiring.dao.getContainer(HOME).isEmpty() }

            wiring.clear(viewModel)
        } finally {
            wiring.close()
        }
    }

    /** Lets the ViewModel's collectors run through their storage reads and main-looper hops. */
    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private suspend fun awaitUntil(what: String, done: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for $what" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }
}

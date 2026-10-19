package org.arkikeskus.launcher.feature.home

import android.app.Application
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.arkikeskus.launcher.data.local.HomeItemEntity.Companion.HOME
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The init block starts its collectors on `Main.immediate`, i.e. synchronously inside the
 * constructor. A shortcut change that pooled in the repository while no ViewModel existed (the
 * activity was destroyed behind Settings) is handed to the new one during construction, so
 * everything the collectors touch must be initialised by then. Robolectric's test thread is the main
 * thread, so the constructor runs exactly as it does in the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HomeViewModelInitTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    @Test fun aShortcutChangePooledBeforeTheViewModelExistedDoesNotCrashItsCreation() = runTest {
        val wiring = ProductionWiring(context, this)
        try {
            val row = wiring.pinOnHome("chat")
            wiring.publishShortcut("chat") // nothing is collecting yet: the change pools in the repository

            val viewModel = wiring.homeViewModel()

            shadowOf(Looper.getMainLooper()).idle()
            assertThat(wiring.dao.getContainer(HOME).map { it.id }).containsExactly(row)
            wiring.clear(viewModel)
        } finally {
            wiring.close()
        }
    }

    @Test fun aViewModelCreatedWithNothingPooledStartsNormally() = runTest {
        val wiring = ProductionWiring(context, this)
        try {
            val row = wiring.pinOnHome("chat")

            val viewModel = wiring.homeViewModel()

            shadowOf(Looper.getMainLooper()).idle()
            assertThat(wiring.dao.getContainer(HOME).map { it.id }).containsExactly(row)
            wiring.clear(viewModel)
        } finally {
            wiring.close()
        }
    }
}

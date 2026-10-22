package org.arkikeskus.launcher.data

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** `UserInfo.FLAG_MANAGED_PROFILE` (the class is not in the SDK stubs). */
private const val FLAG_MANAGED_PROFILE = 0x00000020

/** `UserHandle.of(10)` is not in the SDK stubs; a uid in user 10's range resolves to the same handle. */
private fun workHandle(): UserHandle = UserHandle.getUserHandleForUid(10 * 100_000)

/**
 * The platform side of the Work tab on Android 14 (API 34), where the public API does not type
 * profiles: every profile the user manager lists is reported with its serial and quiet mode, and
 * each profile broadcast becomes one change event from one receiver that goes when the collector
 * does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AndroidProfileSystemTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val userManager = shadowOf(context.getSystemService(UserManager::class.java))
    private val system = AndroidProfileSystem(context)
    private val work = workHandle()

    private fun addWorkProfile() {
        userManager.addUser(0, "Owner", 0)
        userManager.addProfile(0, 10, "Work", FLAG_MANAGED_PROFILE)
    }

    @Test fun snapshotListsTheOwnUserAsOwnAndAProfileWithItsSerial() {
        addWorkProfile()

        val snapshot = system.snapshot()

        assertThat(snapshot.map { it.serial to it.kind })
            .containsExactly(0L to ProfileKind.Own, 10L to ProfileKind.Unknown)
    }

    @Test fun snapshotReadsQuietModeFromTheSystem() {
        addWorkProfile()
        assertThat(system.snapshot().single { it.serial == 10L }.paused).isFalse()

        context.getSystemService(UserManager::class.java).requestQuietModeEnabled(true, work)

        assertThat(system.snapshot().single { it.serial == 10L }.paused).isTrue()
    }

    @Test fun changesEmitsOncePerProfileBroadcast() = runTest {
        val seen = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { system.changes().collect { seen += it } }

        context.sendBroadcast(Intent(Intent.ACTION_MANAGED_PROFILE_AVAILABLE))
        context.sendBroadcast(Intent(Intent.ACTION_MANAGED_PROFILE_AVAILABLE))
        context.sendBroadcast(Intent("android.intent.action.PROFILE_ADDED"))
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(seen).hasSize(3)
    }

    @Test fun theReceiverGoesWhenTheCollectorStops() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { system.changes().collect {} }
        assertThat(profileReceivers()).hasSize(1)

        job.cancel()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(profileReceivers()).isEmpty()
    }

    @Test fun requestQuietModeDelegatesToUserManager() {
        addWorkProfile()

        assertThat(system.requestQuietMode(10, true)).isTrue()

        assertThat(context.getSystemService(UserManager::class.java).isQuietModeEnabled(work)).isTrue()
    }

    private fun profileReceivers() = shadowOf(context).registeredReceivers
        .filter { it.intentFilter.hasAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE) }
}

/** On Android 15 the platform types profiles; without a shadow for that call it must still answer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidProfileSystemApi35Test {
    private val context: Application = RuntimeEnvironment.getApplication()

    @Test fun snapshotNeverThrows() {
        val userManager = shadowOf(context.getSystemService(UserManager::class.java))
        userManager.addUser(0, "Owner", 0)
        userManager.addProfile(0, 10, "Work", FLAG_MANAGED_PROFILE)

        val snapshot = AndroidProfileSystem(context).snapshot()

        assertThat(snapshot.map { it.serial }).containsExactly(0L, 10L)
    }
}

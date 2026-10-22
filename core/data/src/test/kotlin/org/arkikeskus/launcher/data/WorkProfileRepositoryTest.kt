package org.arkikeskus.launcher.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The work-profile state behind the drawer's Work tab: current the moment it is first read, one
 * receiver however many collectors, and Launcher3's transition window after a pause request
 * (`UserProfileManager.STATE_TRANSITION`) that ends when the system reports the flip or, failing
 * that, after five seconds — the user may cancel the system's credential prompt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkProfileRepositoryTest {
    private class FakeProfileSystem(var profiles: List<ProfileSnapshot>) : ProfileSystem {
        val broadcasts = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
        var collectors = 0
        var request: (Long, Boolean) -> Boolean = { _, _ -> true }
        override fun snapshot(): List<ProfileSnapshot> = profiles
        override fun changes(): Flow<Unit> = broadcasts.onStart { collectors++ }
        override fun requestQuietMode(serial: Long, enabled: Boolean): Boolean = request(serial, enabled)
    }

    private val own = ProfileSnapshot(0, ProfileKind.Own, badged = false, paused = false)
    private fun work(paused: Boolean = false) = ProfileSnapshot(10, ProfileKind.Managed, badged = true, paused = paused)

    private fun TestScope.repository(fake: FakeProfileSystem): WorkProfileRepository {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return WorkProfileRepository(
            system = fake,
            scope = CoroutineScope(backgroundScope.coroutineContext + dispatcher),
            io = dispatcher,
            transitionTimeoutMs = 5_000,
        )
    }

    @Test fun initialSnapshotPausedIsReportedBeforeAnyBroadcast() = runTest {
        val repo = repository(FakeProfileSystem(listOf(own, work(paused = true))))

        assertThat(repo.workProfile.value).isEqualTo(WorkProfile(userSerial = 10, paused = true))
    }

    @Test fun theInitialSnapshotIsReadInTheConstructorNotOnTheScope() = runTest {
        // In the app the scope is Dispatchers.Default: a value that only arrives once the scope
        // runs is null for the first reader (the drawer's first combine). Read it right away.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = WorkProfileRepository(
            system = FakeProfileSystem(listOf(own, work(paused = true))),
            scope = CoroutineScope(backgroundScope.coroutineContext + dispatcher),
            io = dispatcher,
            transitionTimeoutMs = 5_000,
        )

        assertThat(repo.workProfile.value).isEqualTo(WorkProfile(userSerial = 10, paused = true))
    }

    @Test fun aChangeBeforeTheFirstCollectorIsNotLost() = runTest {
        val fake = FakeProfileSystem(listOf(own))
        val repo = repository(fake)
        assertThat(repo.workProfile.value).isNull()

        fake.profiles = listOf(own, work())
        fake.broadcasts.emit(Unit)

        assertThat(repo.workProfile.value?.userSerial).isEqualTo(10)
    }

    @Test fun profileRemovalEmitsNull() = runTest {
        val fake = FakeProfileSystem(listOf(own, work()))
        val repo = repository(fake)

        fake.profiles = listOf(own)
        fake.broadcasts.emit(Unit)

        assertThat(repo.workProfile.value).isNull()
    }

    @Test fun oneReceiverHoweverManyCollectors() = runTest {
        val fake = FakeProfileSystem(listOf(own))
        val repo = repository(fake)

        repeat(3) { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.changes.collect {} } }

        assertThat(fake.collectors).isEqualTo(1)
    }

    @Test fun changesRelaysEveryBroadcast() = runTest {
        val fake = FakeProfileSystem(listOf(own))
        val repo = repository(fake)
        val seen = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.changes.collect { seen += it } }

        repeat(3) { fake.broadcasts.emit(Unit) }

        assertThat(seen).hasSize(3)
    }

    @Test fun setWorkPausedRequestedWhenThePlatformSaysTrueOrFalse() = runTest {
        val fake = FakeProfileSystem(listOf(own, work()))
        val calls = mutableListOf<Pair<Long, Boolean>>()
        fake.request = { serial, enabled -> calls += serial to enabled; true }
        val repo = repository(fake)

        assertThat(repo.setWorkPaused(true)).isEqualTo(PauseOutcome.Requested)
        fake.request = { _, _ -> false }
        assertThat(repo.setWorkPaused(true)).isEqualTo(PauseOutcome.Requested)

        assertThat(calls).containsExactly(10L to true)
    }

    @Test fun setWorkPausedSecurityExceptionIsNotDefaultLauncher() = runTest {
        val fake = FakeProfileSystem(listOf(own, work()))
        fake.request = { _, _ -> throw SecurityException("not the default launcher") }
        val repo = repository(fake)

        assertThat(repo.setWorkPaused(true)).isEqualTo(PauseOutcome.NotDefaultLauncher)
        assertThat(repo.workProfile.value?.canPause).isTrue()
    }

    @Test fun setWorkPausedOtherExceptionIsFailedAndDropsCanPause() = runTest {
        val fake = FakeProfileSystem(listOf(own, work()))
        fake.request = { _, _ -> throw IllegalArgumentException("no quiet mode for this profile") }
        val repo = repository(fake)

        assertThat(repo.setWorkPaused(true)).isEqualTo(PauseOutcome.Failed)
        assertThat(repo.workProfile.value?.canPause).isFalse()
    }

    @Test fun canPauseReturnsOnTheNextProfileChange() = runTest {
        val fake = FakeProfileSystem(listOf(own, work()))
        fake.request = { _, _ -> throw IllegalArgumentException() }
        val repo = repository(fake)
        repo.setWorkPaused(true)
        assertThat(repo.workProfile.value?.canPause).isFalse()

        fake.broadcasts.emit(Unit)

        assertThat(repo.workProfile.value?.canPause).isTrue()
    }

    @Test fun transitioningIsSetByARequestAndClearedWhenPausedFlips() = runTest {
        val fake = FakeProfileSystem(listOf(own, work()))
        val repo = repository(fake)

        repo.setWorkPaused(true)
        assertThat(repo.workProfile.value?.transitioning).isTrue()

        fake.profiles = listOf(own, work(paused = true))
        fake.broadcasts.emit(Unit)

        assertThat(repo.workProfile.value).isEqualTo(WorkProfile(userSerial = 10, paused = true))
    }

    @Test fun transitioningClearsAfterFiveSecondsWithoutAStateChange() = runTest {
        val fake = FakeProfileSystem(listOf(own, work()))
        val repo = repository(fake)
        repo.setWorkPaused(true)
        assertThat(repo.workProfile.value?.transitioning).isTrue()

        advanceTimeBy(4_000)
        assertThat(repo.workProfile.value?.transitioning).isTrue()
        advanceTimeBy(1_001)

        assertThat(repo.workProfile.value).isEqualTo(WorkProfile(userSerial = 10, paused = false))
    }

    @Test fun setWorkPausedWithoutAWorkProfileIsFailed() = runTest {
        val fake = FakeProfileSystem(listOf(own))
        var called = false
        fake.request = { _, _ -> called = true; true }
        val repo = repository(fake)

        assertThat(repo.setWorkPaused(true)).isEqualTo(PauseOutcome.Failed)
        assertThat(called).isFalse()
    }
}

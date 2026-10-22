package org.arkikeskus.launcher.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.arkikeskus.launcher.data.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The work profile the drawer's Work tab shows. [paused] is the profile's quiet mode as the system
 * reports it; [canPause] drops to false when the platform rejected a pause request for this
 * profile (API 30–34 cannot tell a clone profile from a work profile, and a clone profile may have
 * no quiet mode), so the drawer hides the buttons instead of offering dead ones; [transitioning]
 * is true from a pause request until the system reports the flip, at most a few seconds.
 */
data class WorkProfile(
    val userSerial: Long,
    val paused: Boolean,
    val canPause: Boolean = true,
    val transitioning: Boolean = false,
)

/** What came of [WorkProfileRepository.setWorkPaused]. */
enum class PauseOutcome {
    /** The system took the request (or is showing its own confirmation); the state flips on its broadcast. */
    Requested,
    /** Only the default launcher may toggle quiet mode (`SecurityException`). */
    NotDefaultLauncher,
    /** No work profile, or the platform rejected the request for this profile. */
    Failed,
}

/**
 * Turns [ProfileSystem] into one process-wide state: [workProfile] is current from the moment it is
 * first read (read once in the constructor, held eagerly, re-read on every profile broadcast),
 * [changes] is the single shared
 * relay of the system's broadcasts (one receiver per process; `LauncherAppsSource` reloads on it,
 * because `LauncherApps.Callback` never reports a profile coming or going), and [setWorkPaused]
 * mirrors Launcher3's `UserProfileManager`: the request goes to the system, and a transition window
 * covers the gap until its broadcast arrives.
 */
@Singleton
class WorkProfileRepository(
    private val system: ProfileSystem,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val transitionTimeoutMs: Long,
) {
    @Inject constructor(system: ProfileSystem, @ApplicationScope scope: CoroutineScope) :
        this(system, scope, Dispatchers.IO, TRANSITION_TIMEOUT_MS)

    private data class Flags(val canPause: Boolean = true, val transitioning: Boolean = false)

    private val flags = MutableStateFlow(Flags())

    /** Shared relay of [ProfileSystem.changes]: ONE receiver for the whole process. */
    val changes: SharedFlow<Unit> = system.changes().shareIn(scope, SharingStarted.Eagerly)

    /** The platform's answer: read once right here (the first reader must not see a stale null
     *  while the scope spins up), re-read on every broadcast — subscribed before that re-read, so
     *  a broadcast in between is not lost — and a change also lifts a `canPause` veto. */
    private val platform: StateFlow<ProfileSnapshot?> = changes
        .onSubscription { emit(Unit) }
        .onEach { flags.update { it.copy(canPause = true) } }
        .map { withContext(io) { workProfileOf(system.snapshot()) } }
        .stateIn(scope, SharingStarted.Eagerly, initialValue = workProfileOf(system.snapshot()))

    /** Null while the device has no work profile. */
    val workProfile: StateFlow<WorkProfile?> = combine(platform, flags) { profile, f ->
        profile?.let { WorkProfile(it.serial, it.paused, f.canPause, f.transitioning) }
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        initialValue = platform.value?.let { WorkProfile(it.serial, it.paused) },
    )

    /**
     * Asks the system to pause ([paused] = true) or unpause the work profile. A `true` or `false`
     * answer both mean the system took it — `false` is its own confirmation prompt — and the
     * state follows the broadcast; until then (or [transitionTimeoutMs], should the user cancel
     * that prompt) [WorkProfile.transitioning] is set.
     */
    suspend fun setWorkPaused(paused: Boolean): PauseOutcome {
        val serial = platform.value?.serial ?: return PauseOutcome.Failed
        val failure = withContext(io) { runCatching { system.requestQuietMode(serial, paused) } }.exceptionOrNull()
        return when {
            failure is SecurityException -> PauseOutcome.NotDefaultLauncher
            failure != null -> {
                flags.update { it.copy(canPause = false) }
                PauseOutcome.Failed
            }
            else -> {
                flags.update { it.copy(transitioning = true) }
                scope.launch {
                    withTimeoutOrNull(transitionTimeoutMs) { platform.first { it?.paused == paused } }
                    flags.update { it.copy(transitioning = false) }
                }
                PauseOutcome.Requested
            }
        }
    }

    private companion object {
        const val TRANSITION_TIMEOUT_MS = 5_000L
    }
}

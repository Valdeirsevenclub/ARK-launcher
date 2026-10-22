package org.arkikeskus.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which profile the drawer's Work tab shows. The platform only types profiles from API 35 on
 * ([ProfileKind.Unknown] below that, or when it does not describe one), where Launcher3's badge
 * check decides instead; the own user never qualifies, private and clone profiles never do.
 */
class WorkProfileDecisionTest {
    private fun p(serial: Long, kind: ProfileKind, badged: Boolean = false, paused: Boolean = false) =
        ProfileSnapshot(serial, kind, badged, paused)

    @Test fun theOwnUserIsNeverWork() {
        assertThat(workProfileOf(listOf(p(0, ProfileKind.Own, badged = true)))).isNull()
    }

    @Test fun aManagedProfileIsWorkWhateverItsBadge() {
        assertThat(workProfileOf(listOf(p(0, ProfileKind.Own), p(10, ProfileKind.Managed)))?.serial).isEqualTo(10)
    }

    @Test fun privateAndCloneProfilesAreNotWorkEvenWhenBadged() {
        val profiles = listOf(
            p(0, ProfileKind.Own),
            p(11, ProfileKind.Private, badged = true),
            p(12, ProfileKind.Clone, badged = true),
        )
        assertThat(workProfileOf(profiles)).isNull()
    }

    @Test fun anUnknownProfileIsWorkOnlyWhenBadged() {
        assertThat(workProfileOf(listOf(p(0, ProfileKind.Own), p(10, ProfileKind.Unknown, badged = false)))).isNull()
        assertThat(workProfileOf(listOf(p(0, ProfileKind.Own), p(10, ProfileKind.Unknown, badged = true)))?.serial)
            .isEqualTo(10)
    }

    @Test fun theLowestSerialWinsWhenTwoQualify() {
        val profiles = listOf(p(12, ProfileKind.Managed), p(10, ProfileKind.Unknown, badged = true))
        assertThat(workProfileOf(profiles)?.serial).isEqualTo(10)
    }

    @Test fun theChosenProfileKeepsItsPausedFlag() {
        assertThat(workProfileOf(listOf(p(10, ProfileKind.Managed, paused = true)))?.paused).isTrue()
    }

    @Test fun noProfilesMeansNoWork() {
        assertThat(workProfileOf(emptyList())).isNull()
    }
}

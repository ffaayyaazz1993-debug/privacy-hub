package com.privacyhub.app

import com.privacyhub.app.data.db.DetoxActivationEntity
import com.privacyhub.app.data.db.DetoxProfileEntity
import com.privacyhub.app.domain.detox.DetoxCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetoxCoordinatorTest {
    private val now = 1_800_000_000_000L
    private val profiles = listOf(
        DetoxProfileEntity(id = 1, name = "Global", isDefault = true),
        DetoxProfileEntity(id = 2, name = "Project", projectId = 5),
        DetoxProfileEntity(id = 3, name = "Node override"),
    )

    @Test fun `profile priority explicit beats node beats project beats global`() {
        assertEquals(3L, DetoxCoordinator.resolveProfile(3, 9, 5, profiles, mapOf(9L to 3L))?.id)
        assertEquals(3L, DetoxCoordinator.resolveProfile(null, 9, 5, profiles, mapOf(9L to 3L))?.id)
        assertEquals(2L, DetoxCoordinator.resolveProfile(null, 9, 5, profiles, emptyMap())?.id)
        assertEquals(1L, DetoxCoordinator.resolveProfile(null, null, null, profiles, emptyMap())?.id)
        assertNull(DetoxCoordinator.resolveProfile(null, null, null, emptyList(), emptyMap()))
    }

    @Test fun `expired source is not live`() {
        val a = DetoxActivationEntity(sourceType = "MANUAL", sourceId = "m", profileId = 1,
            startedAt = now - 60_000, endsAt = now - 1000)
        assertFalse(DetoxCoordinator.isSourceLive(a, now))
        assertTrue(DetoxCoordinator.isSourceLive(a.copy(endsAt = 0), now))
    }

    @Test fun `override window pauses only that source`() {
        val a = DetoxActivationEntity(sourceType = "MANUAL", sourceId = "m", profileId = 1, overrideUntil = now + 60_000)
        val b = DetoxActivationEntity(sourceType = "POMODORO", sourceId = "s", profileId = 1)
        assertFalse(DetoxCoordinator.isSourceLive(a, now))
        assertTrue(DetoxCoordinator.isSourceLive(b, now))
        assertEquals(1, DetoxCoordinator.liveSources(listOf(a, b), now).size)
    }

    @Test fun `combined restrictions are the union across sources`() {
        val acts = listOf(
            DetoxActivationEntity(id = 1, sourceType = "MANUAL", sourceId = "m", profileId = 1),
            DetoxActivationEntity(id = 2, sourceType = "SCHEDULED", sourceId = "s", profileId = 2))
        val out = DetoxCoordinator.combinedRestrictions(
            profiles.associateBy { it.id }, mapOf(1L to listOf("a.pkg"), 2L to listOf("b.pkg", "a.pkg")), acts, now)
        assertEquals(setOf("a.pkg", "b.pkg"), out)
    }
}

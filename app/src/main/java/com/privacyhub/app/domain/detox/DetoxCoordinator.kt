package com.privacyhub.app.domain.detox

import com.privacyhub.app.data.db.DetoxActivationEntity
import com.privacyhub.app.data.db.DetoxProfileEntity

enum class DetoxSource(val key: String) { POMODORO("POMODORO"), SCHEDULED("SCHEDULED"), MANUAL("MANUAL");
    companion object { fun of(s: String) = entries.firstOrNull { it.key == s } ?: MANUAL } }

/**
 * Pure coordination rules. No Android, no Room, no clock dependency.
 * A source is live while its row is ACTIVE, not expired and not inside an
 * emergency override window. Releasing one source never touches another.
 */
object DetoxCoordinator {
    fun isSourceLive(a: DetoxActivationEntity, now: Long): Boolean =
        a.status == "ACTIVE" &&
            (a.endsAt == 0L || a.endsAt > now) &&
            (a.overrideUntil == 0L || a.overrideUntil <= now)

    fun liveSources(activations: List<DetoxActivationEntity>, now: Long): List<DetoxActivationEntity> =
        activations.filter { isSourceLive(it, now) }

    fun anyLive(activations: List<DetoxActivationEntity>, now: Long): Boolean =
        liveSources(activations, now).isNotEmpty()

    /** Union of package names across all live profiles; no per-source precedence to break. */
    fun combinedRestrictions(profilesById: Map<Long, DetoxProfileEntity>,
                             packagesByProfile: Map<Long, List<String>>,
                             activations: List<DetoxActivationEntity>, now: Long): Set<String> =
        liveSources(activations, now)
            .mapNotNull { profilesById[it.profileId]?.id }
            .flatMap { packagesByProfile[it].orEmpty().asIterable() }
            .toSet()

    /**
     * Predictable priority: explicit session selection → node override →
     * project default → global default. Returns null when Detox must stay off.
     */
    fun resolveProfile(explicitId: Long?, nodeId: Long?, projectId: Long?,
                       profiles: List<DetoxProfileEntity>,
                       nodeOverrides: Map<Long, Long?>): DetoxProfileEntity? {
        explicitId?.let { id -> profiles.firstOrNull { it.id == id }?.let { return it } }
        nodeId?.let { n -> nodeOverrides[n]?.let { pid -> profiles.firstOrNull { it.id == pid }?.let { return it } } }
        projectId?.let { p -> profiles.firstOrNull { it.projectId == p && !it.isDefault }?.let { return it } }
        return profiles.firstOrNull { it.isDefault && it.projectId == null }
            ?: profiles.firstOrNull { it.projectId == null }
    }
}

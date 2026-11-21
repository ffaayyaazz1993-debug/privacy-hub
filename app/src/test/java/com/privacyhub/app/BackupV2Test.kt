package com.privacyhub.app

import com.privacyhub.app.data.db.FlashcardEntity
import com.privacyhub.app.data.db.ReviewLogEntity
import com.privacyhub.app.data.db.SchedulerSettingsEntity
import com.privacyhub.app.data.export.BackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupV2Test {
    @Test fun `v2 backup round trips cards logs and settings`() {
        val b = BackupManager.Backup(
            version = 2,
            flashcards = listOf(FlashcardEntity(id = 7, projectId = 1, nodeId = 3,
                question = "What is inflation?", answer = "A sustained rise in prices",
                stability = 4.2, difficulty = 5.1, dueTimestamp = 12345L, schedulerVersion = 1)),
            reviewLogs = listOf(ReviewLogEntity(id = 9, flashcardId = 7, reviewedAt = 1000L,
                rating = "GOOD", previousDueTimestamp = 500L, nextDueTimestamp = 90000L,
                schedulerVersion = 1)),
            schedulerSettings = SchedulerSettingsEntity(desiredRetention = 0.95),
        )
        val back = BackupManager.deserialize(BackupManager.serialize(b))!!
        assertEquals(2, back.version)
        assertEquals("What is inflation?", back.flashcards[0].question)
        assertEquals(4.2, back.flashcards[0].stability, 0.0)
        assertEquals(7L, back.reviewLogs[0].flashcardId)
        assertEquals(0.95, back.schedulerSettings!!.desiredRetention, 0.0)
    }

    @Test fun `old v1 backup without learning fields still parses`() {
        val old = "{\"version\":1,\"projects\":[],\"nodes\":[],\"sessions\":[]}"
        val b = BackupManager.deserialize(old)!!
        assertEquals(1, b.version)
        assertTrue((b.flashcards?.size ?: 0) == 0)
    }

    @Test fun `malformed backup rejected`() {
        assertNull(BackupManager.deserialize("not json"))
    }
}

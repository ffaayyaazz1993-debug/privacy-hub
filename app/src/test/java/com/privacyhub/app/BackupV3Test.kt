package com.privacyhub.app

import com.privacyhub.app.data.export.BackupManager
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupV3Test {
    @Test fun `v3 backup keeps routines and references`() {
        val json = "{\"version\":3,\"routines\":[{\"id\":1,\"title\":\"MWF\",\"projectId\":2,\"weekdays\":\"0,2,4\",\"durationMinutes\":25,\"weeklyTarget\":3,\"reminderMinutes\":15,\"active\":true,\"createdAt\":1}],\"references\":[]}"
        val b = BackupManager.deserialize(json)!!
        assertEquals(3, b.version)
        assertEquals("MWF", b.routines[0].title)
        assertEquals("0,2,4", b.routines[0].weekdays)
    }

    @Test fun `v1 backup still parses with no new collections`() {
        val b = BackupManager.deserialize("{\"version\":1,\"projects\":[]}")!!
        assertEquals(1, b.version)
        assertEquals(0, (b.routines?.size ?: 0))
    }
}

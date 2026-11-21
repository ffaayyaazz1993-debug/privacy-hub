package com.privacyhub.app

import com.privacyhub.app.domain.template.WorkspaceTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTemplateTest {
    @Test fun `all four templates are available with unique ids`() {
        assertEquals(4, WorkspaceTemplates.ALL.size)
        assertEquals(4, WorkspaceTemplates.ALL.map { it.id }.toSet().size)
    }

    @Test fun `study tree has subjects chapters and assessments`() {
        val t = WorkspaceTemplates.byId(WorkspaceTemplates.STUDY)
        val tops = t.root!!.children.map { it.title }
        assertTrue("Subjects" in tops); assertTrue("Revision" in tops); assertTrue("Assessments" in tops)
        val subjects = t.root.children.first { it.title == "Subjects" }
        assertTrue(subjects.children.any { it.title == "Subject 1" })
        assertTrue(subjects.children.first { it.title == "Subject 1" }.children.any { it.title == "Chapter 1" })
        assertEquals(listOf(25, 50, 90), t.presets.map { it.minutes })
    }

    @Test fun `interview tree supports multiple projects per company`() {
        val t = WorkspaceTemplates.byId(WorkspaceTemplates.INTERVIEW)
        val tops = t.root!!.children.map { it.title }
        assertTrue("Target Role" in tops); assertTrue("Mock Interviews" in tops); assertTrue("Interview Schedule" in tops)
        assertTrue(t.root.children.first { it.title == "Interview Schedule" }.children.any { it.title == "Interview Date" })
    }

    @Test fun `work tree has workstreams milestones and review`() {
        val t = WorkspaceTemplates.byId(WorkspaceTemplates.WORK)
        val tops = t.root!!.children.map { it.title }
        assertTrue("Workstreams" in tops); assertTrue("Milestones" in tops); assertTrue("Review" in tops)
        assertEquals(listOf(25, 45, 60), t.presets.map { it.minutes })
    }

    @Test fun `preview contains nested nodes and blank template has no imposed tree`() {
        assertTrue(WorkspaceTemplates.preview(WorkspaceTemplates.byId(WorkspaceTemplates.STUDY)).contains("Topic 1"))
        assertTrue(WorkspaceTemplates.preview(WorkspaceTemplates.byId(WorkspaceTemplates.BLANK)).contains("Blank Project"))
    }
}

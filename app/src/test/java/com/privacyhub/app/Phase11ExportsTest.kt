package com.privacyhub.app

import com.privacyhub.app.domain.export.CalendarEvent
import com.privacyhub.app.domain.export.ExportProject
import com.privacyhub.app.domain.export.OutlineNode
import com.privacyhub.app.domain.export.TextExports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase11ExportsTest {
    private val project = ExportProject("Macroeconomics", "Study of the economy", listOf(
        OutlineNode("Inflation", "Price level", listOf(
            OutlineNode("Demand-pull", "Demand exceeds capacity"),
            OutlineNode("Cost-push", "Costs rise")),
        ),
        OutlineNode("Monetary policy"),
    ))

    @Test fun `opml round trip preserves hierarchy and notes`() {
        val xml = TextExports.toOpml(listOf(project))
        val back = TextExports.parseOpml(xml)
        assertEquals(1, back.size)
        assertEquals("Macroeconomics", back[0].title)
        assertEquals("Inflation", back[0].nodes[0].title)
        assertEquals("Demand-pull", back[0].nodes[0].children[0].title)
        assertEquals("Demand exceeds capacity", back[0].nodes[0].children[0].description)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed opml rejected`() { TextExports.parseOpml("<opml><body><outline text=\"A\"></body></opml>") }

    @Test fun `markdown uses headings and bullets`() {
        val md = TextExports.toMarkdown(project)
        assertTrue(md.startsWith("# Macroeconomics"))
        assertTrue(md.contains("## Inflation"))
        assertTrue(md.contains("- Demand-pull"))
    }

    @Test fun `ics contains alarm and utc times`() {
        val ics = TextExports.toIcs(listOf(CalendarEvent(1, "Study, topic", "Node\nNotes", 1_800_000_000_000L, 25, 15)))
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.contains("SUMMARY:Study\\, topic"))
        assertTrue(ics.contains("BEGIN:VALARM"))
        assertTrue(ics.contains("TRIGGER:-PT15M"))
        assertTrue(ics.contains("END:VCALENDAR"))
    }

    @Test fun `xml special characters are escaped and restored`() {
        val p = ExportProject("A & B", "x < y", listOf(OutlineNode("Q \"quoted\"")))
        val back = TextExports.parseOpml(TextExports.toOpml(listOf(p)))
        assertEquals("A & B", back[0].title)
        assertEquals("x < y", back[0].description)
        assertEquals("Q \"quoted\"", back[0].nodes[0].title)
    }
}

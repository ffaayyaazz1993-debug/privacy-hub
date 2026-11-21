package com.privacyhub.app.domain.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class OutlineNode(
    val title: String,
    val description: String = "",
    val children: List<OutlineNode> = emptyList(),
)

data class ExportProject(
    val title: String,
    val description: String = "",
    val nodes: List<OutlineNode> = emptyList(),
)

data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String,
    val startMillis: Long,
    val durationMinutes: Int,
    val reminderMinutes: Int = 0,
)

/** Deterministic, dependency-free OPML / Markdown / iCalendar conversions. */
object TextExports {
    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
    private fun unxml(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")

    fun toOpml(projects: List<ExportProject>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<opml version=\"2.0\"><head><title>FocusMap export</title></head><body>\n")
        projects.forEach { p -> appendOutline(this, p.title, p.description, p.nodes, 1) }
        append("</body></opml>\n")
    }

    private fun appendOutline(sb: StringBuilder, title: String, description: String,
                              children: List<OutlineNode>, depth: Int) {
        val note = if (description.isBlank()) "" else " _note=\"${xml(description)}\""
        if (children.isEmpty()) {
            sb.append("  ".repeat(depth)).append("<outline text=\"${xml(title)}\"$note/>\n")
        } else {
            sb.append("  ".repeat(depth)).append("<outline text=\"${xml(title)}\"$note>\n")
            children.forEach { appendOutline(sb, it.title, it.description, it.children, depth + 1) }
            sb.append("  ".repeat(depth)).append("</outline>\n")
        }
    }

    /** Parses our own OPML shape. Throws on malformed documents instead of returning partial data. */
    fun parseOpml(input: String): List<ExportProject> {
        require(input.contains("<opml", ignoreCase = true)) { "Not an OPML document" }
        require(input.contains("<body", ignoreCase = true)) { "OPML body missing" }
        val stack = ArrayDeque<MutableOutline>()
        val roots = mutableListOf<MutableOutline>()
        val tag = Regex("<outline\\b([^>]*?)(/?)>", RegexOption.IGNORE_CASE)
        val attrs = Regex("([A-Za-z_][A-Za-z0-9_]*)=\"([^\"]*)\"")
        var cursor = 0
        for (m in tag.findAll(input)) {
            val between = input.substring(cursor, m.range.first)
            val closes = Regex("</outline\\s*>", RegexOption.IGNORE_CASE).findAll(between).count()
            repeat(closes) {
                require(stack.isNotEmpty()) { "Unbalanced OPML outline" }
                stack.removeLast()
            }
            cursor = m.range.last + 1
            val values = attrs.findAll(m.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            val title = values["text"] ?: values["title"] ?: ""
            require(title.isNotBlank()) { "Outline without title" }
            val node = MutableOutline(unxml(title), unxml(values["_note"] ?: ""))
            if (stack.isEmpty()) roots += node else stack.last().children += node
            val selfClosing = m.value.trim().endsWith("/>")
            if (!selfClosing) stack.addLast(node)
        }
        val tail = input.substring(cursor)
        val tailCloses = Regex("</outline\\s*>", RegexOption.IGNORE_CASE).findAll(tail).count()
        repeat(tailCloses) {
            require(stack.isNotEmpty()) { "Unbalanced OPML outline" }
            stack.removeLast()
        }
        require(stack.isEmpty()) { "Unclosed OPML outline" }
        return roots.map { ExportProject(it.title, it.description, freeze(it.children)) }
    }

    private class MutableOutline(val title: String, val description: String) {
        val children = mutableListOf<MutableOutline>()
    }
    private fun freeze(list: List<MutableOutline>): List<OutlineNode> = list.map {
        OutlineNode(it.title, it.description, freeze(it.children))
    }

    fun toMarkdown(project: ExportProject): String = buildString {
        append("# ").append(project.title).append('\n')
        if (project.description.isNotBlank()) append(project.description).append("\n\n")
        fun walk(nodes: List<OutlineNode>, depth: Int) {
            nodes.forEach { n ->
                if (n.children.isNotEmpty()) {
                    append("\n").append("##".padStart(0)).append(" ".repeat(0))
                    append("#".repeat((depth + 1).coerceAtMost(6))).append(' ').append(n.title).append('\n')
                    if (n.description.isNotBlank()) append(n.description).append('\n')
                    walk(n.children, depth + 1)
                } else {
                    append("- ").append(n.title).append('\n')
                    if (n.description.isNotBlank()) append("  ").append(n.description).append('\n')
                }
            }
        }
        walk(project.nodes, 1)
    }

    private val utc: SimpleDateFormat
        get() = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    fun toIcs(events: List<CalendarEvent>): String = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//FocusMap//Offline study plan//EN\r\nCALSCALE:GREGORIAN\r\n")
        events.forEach { e ->
            val end = e.startMillis + e.durationMinutes * 60_000L
            append("BEGIN:VEVENT\r\n")
            append("UID:focusmap-${e.id}@local\r\n")
            append("DTSTAMP:").append(utc.format(Date())).append("\r\n")
            append("DTSTART:").append(utc.format(Date(e.startMillis))).append("\r\n")
            append("DTEND:").append(utc.format(Date(end))).append("\r\n")
            append("SUMMARY:").append(ics(e.title)).append("\r\n")
            append("DESCRIPTION:").append(ics(e.description)).append("\r\n")
            if (e.reminderMinutes > 0) {
                append("BEGIN:VALARM\r\nACTION:DISPLAY\r\nTRIGGER:-PT")
                append(e.reminderMinutes).append("M\r\nDESCRIPTION:Study reminder\r\nEND:VALARM\r\n")
            }
            append("END:VEVENT\r\n")
        }
        append("END:VCALENDAR\r\n")
    }

    private fun ics(s: String) = s.replace("\\", "\\\\").replace(";", "\\;")
        .replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")
}

package com.privacyhub.app.ui.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.privacyhub.app.domain.export.ExportProject
import com.privacyhub.app.domain.export.OutlineNode
import java.io.OutputStream

/** Framework-only PDF writer; no third-party PDF dependency. */
object PdfExporter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48

    fun write(projects: List<ExportProject>, out: OutputStream) {
        val doc = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; color = Color.rgb(20, 40, 80); isFakeBoldText = true }
        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; color = Color.rgb(30, 60, 110); isFakeBoldText = true }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.rgb(40, 40, 50) }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var y = MARGIN

        fun newPage() {
            doc.finishPage(page); pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            y = MARGIN
        }
        fun line(text: String, paint: Paint, indent: Int) {
            val max = PAGE_W - MARGIN * 2 - indent
            val words = text.split(' ')
            val sb = StringBuilder()
            for (word in words) {
                val candidate = if (sb.isEmpty()) word else "$sb $word"
                if (paint.measureText(candidate) > max && sb.isNotEmpty()) {
                    if (y > PAGE_H - MARGIN) newPage()
                    page.canvas.drawText(sb.toString(), (MARGIN + indent).toFloat(), y.toFloat(), paint)
                    y += (paint.textSize + 4).toInt(); sb.setLength(0)
                }
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(word)
            }
            if (sb.isNotEmpty()) {
                if (y > PAGE_H - MARGIN) newPage()
                page.canvas.drawText(sb.toString(), (MARGIN + indent).toFloat(), y.toFloat(), paint)
                y += (paint.textSize + 6).toInt()
            }
        }
        projects.forEach { p ->
            if (y > PAGE_H - 160) newPage()
            line(p.title, titlePaint, 0)
            if (p.description.isNotBlank()) line(p.description, bodyPaint, 0)
            fun walk(nodes: List<OutlineNode>, depth: Int) {
                nodes.forEach { n ->
                    if (y > PAGE_H - 100) newPage()
                    line("${"  ".repeat(depth)}${n.title}", if (depth == 0) headPaint else bodyPaint, depth * 14)
                    if (n.description.isNotBlank()) line(n.description, bodyPaint, depth * 14 + 10)
                    walk(n.children, depth + 1)
                }
            }
            walk(p.nodes, 0)
            y += 16
        }
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }
}

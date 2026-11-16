package sg.edumate.app.data.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sg.edumate.domain.model.Worksheet
import sg.edumate.domain.model.WorksheetVersion
import java.io.File
import javax.inject.Inject

/**
 * Renders a worksheet version to an A4 PDF in the app's cache and returns a content:// URI for sharing.
 * Google Docs/Slides and DOCX exports are produced server-side (POST /v1/exports) so the Google
 * OAuth scopes and document templates live in one place.
 */
class WorksheetPdfExporter @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun export(ws: Worksheet, version: WorksheetVersion, includeAnswers: Boolean): android.net.Uri = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val pageW = 595; val pageH = 842; val margin = 48
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
        val heading = TextPaint(body).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD }
        val small = TextPaint(body).apply { textSize = 9f; color = 0xFF555555.toInt() }

        val blocks = buildList {
            add(heading to "${ws.title} – ${version.stream.label}")
            add(small to "Name: ____________________   Class: ______   Date: ________   Total: ${version.totalMarks} marks")
            ws.objectives.takeIf { it.isNotEmpty() }?.let { add(small to "Learning objectives: " + it.joinToString("; ")) }
            version.questions.forEach { q ->
                add(body to "${q.number}. ${q.prompt}  [${q.marks}]")
                q.options.forEach { add(body to "     $it") }
                q.scaffolds.forEach { add(small to "     ${it.kind.label}: ${it.content}") }
                add(body to " ")
            }
            if (includeAnswers) {
                add(heading to "Answer key & marking scheme")
                version.answerKey.forEach { a -> add(body to "${a.questionId}: ${a.answer} — ${a.markingPoints.joinToString("; ")}") }
            }
            add(small to "AI-assisted content reviewed by the teacher. Source: ${ws.provenance?.citationNote ?: ws.provenance?.citations?.joinToString { it.title }}")
        }

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        var y = margin.toFloat()
        for ((paint, text) in blocks) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, pageW - 2 * margin)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
            if (y + layout.height > pageH - margin) {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create()); y = margin.toFloat()
            }
            page.canvas.save(); page.canvas.translate(margin.toFloat(), y); layout.draw(page.canvas); page.canvas.restore()
            y += layout.height + 6
        }
        doc.finishPage(page)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = ws.title.replace(Regex("[^A-Za-z0-9]+"), "_").take(40)
        val file = File(dir, "${safeName}_${version.stream.name}${if (includeAnswers) "_key" else ""}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}

package docfiller.template

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFRun
import java.io.File

/**
 * Подстановка значений вместо плейсхолдеров {{...}} в .docx.
 * Плейсхолдер может быть разрезан Word'ом на несколько runs внутри абзаца —
 * поиск ведётся по склеенному тексту абзаца, замена пишется в первый run
 * (сохраняя его форматирование), остальные кусочки плейсхолдера стираются.
 */
object DocxFiller {

    /**
     * @param values ключ — внутренность плейсхолдера («person.ФИО:инициалы»), значение — подстановка.
     *               Плейсхолдеры без значения остаются в документе как есть.
     */
    fun fill(templateFile: File, outputFile: File, values: Map<String, String>) {
        XWPFDocument(templateFile.inputStream()).use { doc ->
            for (paragraph in collectParagraphs(doc)) {
                replaceInParagraph(paragraph, values)
            }
            outputFile.outputStream().use { doc.write(it) }
        }
    }

    private fun replaceInParagraph(paragraph: org.apache.poi.xwpf.usermodel.XWPFParagraph, values: Map<String, String>) {
        val runs = paragraph.runs
        if (runs.isEmpty()) return
        val texts = runs.map { it.text() ?: "" }.toMutableList()
        val combined = texts.joinToString("")
        if (!combined.contains("{{")) return

        val matches = Placeholders.REGEX.findAll(combined).toList()
        if (matches.isEmpty()) return

        // Справа налево: позиции совпадений левее текущего не сдвигаются,
        // а тексты изменённых runs обновляем в снапшоте (texts).
        for (match in matches.asReversed()) {
            val rawKey = match.groupValues[1].trim()
            val replacement = values[rawKey]
                ?: Placeholders.parse(rawKey)?.let { values[it.key] }
                ?: continue
            replaceRange(runs, texts, match.range.first, match.range.last + 1, replacement)
        }
    }

    private fun replaceRange(runs: List<XWPFRun>, texts: MutableList<String>, start: Int, end: Int, replacement: String) {
        var pos = 0
        var firstRun = true
        for (i in runs.indices) {
            val runStart = pos
            val runEnd = pos + texts[i].length
            pos = runEnd
            val overlapStart = maxOf(start, runStart)
            val overlapEnd = minOf(end, runEnd)
            if (overlapStart >= overlapEnd) continue

            val localStart = overlapStart - runStart
            val localEnd = overlapEnd - runStart
            val insert = if (firstRun) replacement else ""
            if (firstRun) clearHighlight(runs[i])
            firstRun = false
            val newText = texts[i].substring(0, localStart) + insert + texts[i].substring(localEnd)
            texts[i] = newText
            setRunText(runs[i], newText)
        }
    }

    /** В шаблоне плейсхолдеры могут быть подсвечены — в готовом документе подсветка не нужна. */
    private fun clearHighlight(run: XWPFRun) {
        runCatching {
            val rPr = run.ctr.rPr ?: return@runCatching
            val node = rPr.domNode
            val toRemove = mutableListOf<org.w3c.dom.Node>()
            for (i in 0 until node.childNodes.length) {
                val child = node.childNodes.item(i)
                if (child.localName == "highlight") toRemove += child
            }
            toRemove.forEach { node.removeChild(it) }
        }
    }

    private fun setRunText(run: XWPFRun, text: String) {
        val ctr = run.ctr
        while (ctr.sizeOfTArray() > 0) ctr.removeT(0)
        if (text.isNotEmpty()) run.setText(text, 0)
    }
}

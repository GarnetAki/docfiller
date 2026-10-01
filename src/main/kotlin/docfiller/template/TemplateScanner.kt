package docfiller.template

import docfiller.model.Placeholder
import docfiller.model.PlaceholderSource
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.File

object Placeholders {
    val REGEX = Regex("\\{\\{\\s*([^{}]+?)\\s*\\}\\}")

    fun parse(inner: String): Placeholder? {
        val colonParts = inner.split(":")
        val base = colonParts[0].trim()
        val modifier = colonParts.getOrNull(1)?.trim()?.lowercase()
        val dot = base.indexOf('.')
        if (dot <= 0 || dot == base.length - 1) return null
        val source = when (base.substring(0, dot).lowercase()) {
            "contract" -> PlaceholderSource.CONTRACT
            "event" -> PlaceholderSource.EVENT
            "person" -> PlaceholderSource.PERSON
            "each" -> PlaceholderSource.EACH
            else -> return null
        }
        return Placeholder(key = base.trim() + (modifier?.let { ":$it" } ?: ""), source = source,
            name = base.substring(dot + 1).trim(), modifier = modifier)
    }
}

/** Сбор всех абзацев документа: тело, таблицы (в т.ч. вложенные), колонтитулы. */
fun collectParagraphs(doc: XWPFDocument): List<XWPFParagraph> {
    val result = mutableListOf<XWPFParagraph>()

    fun addTables(tables: List<XWPFTable>) {
        for (table in tables) {
            for (row in table.rows) {
                for (cell in row.tableCells) {
                    result.addAll(cell.paragraphs)
                    addTables(cell.tables)
                }
            }
        }
    }

    result.addAll(doc.paragraphs)
    addTables(doc.tables)
    for (header in doc.headerList) {
        result.addAll(header.paragraphs)
        addTables(header.tables)
    }
    for (footer in doc.footerList) {
        result.addAll(footer.paragraphs)
        addTables(footer.tables)
    }
    return result
}

object TemplateScanner {

    /** Список плейсхолдеров шаблона в порядке следования, без дубликатов. */
    fun scan(file: File): List<Placeholder> {
        XWPFDocument(file.inputStream()).use { doc ->
            val seen = LinkedHashMap<String, Placeholder>()
            for (paragraph in collectParagraphs(doc)) {
                val text = paragraph.runs.joinToString("") { it.text() ?: "" }
                for (match in Placeholders.REGEX.findAll(text)) {
                    val placeholder = Placeholders.parse(match.groupValues[1]) ?: continue
                    seen.putIfAbsent(placeholder.key, placeholder)
                }
            }
            return seen.values.toList()
        }
    }
}

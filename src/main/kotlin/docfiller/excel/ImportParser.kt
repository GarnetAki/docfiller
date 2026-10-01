package docfiller.excel

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File

/** Разбор данных, скопированных из таблицы ответов формы, или из файла. */
object ImportParser {

    data class ParsedTable(
        val headers: List<String>,
        val rows: List<List<String>>,
    ) {
        fun toMaps(): List<Map<String, String>> =
            rows.map { row ->
                headers.indices
                    .filter { headers[it].isNotBlank() }
                    .associate { headers[it] to (row.getOrElse(it) { "" }) }
            }.filter { row -> row.values.any { it.isNotBlank() } }
    }

    /** Текст из буфера обмена (TSV из Google Sheets/Excel) или содержимое csv-файла. */
    fun parseDelimited(text: String): ParsedTable {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return ParsedTable(emptyList(), emptyList())

        val delimiter = listOf('\t', ';', ',').maxBy { d -> lines[0].count { it == d } }
        if (lines[0].count { it == delimiter } == 0) {
            // Одна колонка — возможно, вставили просто список ФИО
            return ParsedTable(listOf("ФИО"), lines.map { listOf(it.trim()) })
        }
        fun split(line: String): List<String> =
            line.split(delimiter).map { it.trim().removeSurrounding("\"") }

        val headers = split(lines[0])
        val rows = lines.drop(1).map { line ->
            val cells = split(line)
            if (cells.size < headers.size) cells + List(headers.size - cells.size) { "" } else cells
        }
        return ParsedTable(headers, rows)
    }

    fun parseXlsx(file: File): ParsedTable {
        XSSFWorkbook(file.inputStream()).use { wb ->
            val sheet = wb.getSheetAt(0) ?: return ParsedTable(emptyList(), emptyList())
            val formatter = DataFormatter()
            val headerRow = sheet.getRow(0) ?: return ParsedTable(emptyList(), emptyList())
            val headers = (0 until headerRow.lastCellNum.toInt()).map {
                formatter.formatCellValue(headerRow.getCell(it))?.trim() ?: ""
            }
            val rows = mutableListOf<List<String>>()
            for (rowNum in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowNum) ?: continue
                val cells = headers.indices.map {
                    formatter.formatCellValue(row.getCell(it))?.trim() ?: ""
                }
                if (cells.any { it.isNotBlank() }) rows += cells
            }
            return ParsedTable(headers, rows)
        }
    }
}

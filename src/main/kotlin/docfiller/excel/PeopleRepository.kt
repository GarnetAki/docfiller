package docfiller.excel

import docfiller.model.Person
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.floor

data class PeopleData(
    val columns: List<String>,
    val people: List<Person>,
)

/**
 * Люди и теги в локальном Excel-файле. Первая строка — заголовки колонок.
 * Служебные колонки: «ID» (заполняется приложением) и «Теги» (через «;»).
 * Приложение пишет только эти две колонки, остальные данные не трогает.
 */
class PeopleRepository(private val file: File) {

    companion object {
        const val ID_COLUMN = "ID"
        const val TAGS_COLUMN = "Теги"

        val DEFAULT_COLUMNS = listOf(
            ID_COLUMN, "ФИО", TAGS_COLUMN,
            "Дата рождения", "ИНН", "СНИЛС",
            "Паспорт серия", "Паспорт номер", "Паспорт дата выдачи", "Паспорт кем выдан",
            "Код подразделения", "Адрес регистрации",
            "Банк", "БИК", "К/С", "Л/С",
            "Сумма", "Вид услуг",
        )
    }

    fun ensureExists() {
        if (file.exists()) return
        file.parentFile?.mkdirs()
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Люди")
            val header = sheet.createRow(0)
            DEFAULT_COLUMNS.forEachIndexed { i, name -> header.createCell(i).setCellValue(name) }
            val sample = sheet.createRow(1)
            sample.createCell(0).setCellValue(UUID.randomUUID().toString().take(8))
            sample.createCell(1).setCellValue("Иванов Иван Иванович")
            sample.createCell(3).setCellValue("01.01.1990")
            sample.createCell(DEFAULT_COLUMNS.indexOf("Сумма")).setCellValue("10000")
            file.outputStream().use { wb.write(it) }
        }
    }

    fun load(): PeopleData {
        ensureExists()
        XSSFWorkbook(file.inputStream()).use { wb ->
            val sheet = wb.getSheetAt(0)
            val headerRow = sheet.getRow(0)
                ?: return PeopleData(emptyList(), emptyList())
            val formatter = DataFormatter()

            // Итерируем по индексам, а не по существующим ячейкам:
            // после удаления колонок в Excel в заголовке могут быть дыры.
            val columns = headerColumns(headerRow, formatter)
            val idIdx = columns.indexOfFirst { it.equals(ID_COLUMN, ignoreCase = true) }
            val tagsIdx = columns.indexOfFirst { it.equals(TAGS_COLUMN, ignoreCase = true) }

            val people = mutableListOf<Person>()
            var needsIdSave = false
            for (rowNum in 1..sheet.lastRowNum) {
                val row: Row = sheet.getRow(rowNum) ?: continue
                val fields = linkedMapOf<String, String>()
                columns.forEachIndexed { i, name ->
                    if (name.isNotBlank() && i != idIdx && i != tagsIdx) {
                        fields[name] = cellToString(row.getCell(i), formatter)
                    }
                }
                if (fields.values.all { it.isBlank() }) continue

                var id = if (idIdx >= 0) cellToString(row.getCell(idIdx), formatter) else ""
                if (id.isBlank()) {
                    id = UUID.randomUUID().toString().take(8)
                    needsIdSave = true
                }
                val tags = if (tagsIdx >= 0) parseTags(cellToString(row.getCell(tagsIdx), formatter)) else emptySet()
                people += Person(id = id, fields = fields, tags = tags)
            }

            if (needsIdSave || idIdx < 0 || tagsIdx < 0) {
                saveServiceColumns(people)
            }
            return PeopleData(columns.filter { it.isNotBlank() }, people)
        }
    }

    /** Сохраняет колонки ID и Теги, остальное содержимое файла не меняет. */
    fun saveServiceColumns(people: List<Person>) {
        ensureExists()
        XSSFWorkbook(file.inputStream()).use { wb ->
            val sheet = wb.getSheetAt(0)
            val headerRow = sheet.getRow(0) ?: sheet.createRow(0)
            val formatter = DataFormatter()
            val columns = headerColumns(headerRow, formatter).toMutableList()

            var idIdx = columns.indexOfFirst { it.equals(ID_COLUMN, ignoreCase = true) }
            if (idIdx < 0) {
                idIdx = columns.size
                headerRow.createCell(idIdx).setCellValue(ID_COLUMN)
            }
            var tagsIdx = columns.indexOfFirst { it.equals(TAGS_COLUMN, ignoreCase = true) }
            if (tagsIdx < 0) {
                tagsIdx = maxOf(columns.size, idIdx + 1)
                headerRow.createCell(tagsIdx).setCellValue(TAGS_COLUMN)
            }

            people.forEachIndexed { index, person ->
                val row = sheet.getRow(index + 1) ?: sheet.createRow(index + 1)
                row.createCell(idIdx).setCellValue(person.id)
                row.createCell(tagsIdx).setCellValue(person.tags.joinToString(";"))
            }
            writeFile(wb)
        }
    }

    fun saveTags(people: List<Person>) = saveServiceColumns(people)

    data class ImportResult(val added: Int, val updated: Int, val newColumns: List<String>)

    /**
     * Импорт строк (например, ответов формы). Колонки сопоставляются по именам,
     * недостающие создаются. При [upsertByFio] строки с совпадающим ФИО обновляют
     * существующего человека (пустые значения не затирают заполненные),
     * остальные добавляются новыми строками.
     */
    fun importRows(rows: List<Map<String, String>>, upsertByFio: Boolean): ImportResult {
        ensureExists()
        XSSFWorkbook(file.inputStream()).use { wb ->
            val sheet = wb.getSheetAt(0)
            val header = sheet.getRow(0) ?: sheet.createRow(0)
            val formatter = DataFormatter()
            val columns = headerColumns(header, formatter).toMutableList()

            fun ensureColumn(name: String, newColumns: MutableList<String>): Int {
                val existing = columns.indexOfFirst { it.equals(name, ignoreCase = true) }
                if (existing >= 0) return existing
                val idx = columns.size
                header.createCell(idx).setCellValue(name)
                columns += name
                newColumns += name
                return idx
            }

            val newColumns = mutableListOf<String>()
            val idIdx = ensureColumn(ID_COLUMN, newColumns)
            ensureColumn(TAGS_COLUMN, newColumns)

            // Новые колонки из импортируемых данных
            val wantedKeys = rows.flatMap { it.keys }.distinct().filter { it.isNotBlank() }
            val keyToIdx = wantedKeys.associateWith { ensureColumn(it, newColumns) }

            val fioIdx = columns.indexOfFirst { it.equals("ФИО", ignoreCase = true) }
            fun norm(s: String) = s.lowercase().replace(Regex("\\s+"), " ").trim()

            // Существующие люди: ФИО → номер строки
            val rowByFio = mutableMapOf<String, Int>()
            if (fioIdx >= 0) {
                for (rowNum in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(rowNum) ?: continue
                    val fio = cellToString(row.getCell(fioIdx), formatter)
                    if (fio.isNotBlank()) rowByFio[norm(fio)] = rowNum
                }
            }

            var added = 0
            var updated = 0
            for (rowData in rows) {
                val fio = rowData.entries.firstOrNull { it.key.equals("ФИО", ignoreCase = true) }?.value.orEmpty()
                val existingRow = if (upsertByFio && fio.isNotBlank()) rowByFio[norm(fio)] else null

                val targetRowNum: Int
                if (existingRow != null) {
                    targetRowNum = existingRow
                    updated++
                } else {
                    targetRowNum = sheet.lastRowNum + 1
                    added++
                    if (fio.isNotBlank()) rowByFio[norm(fio)] = targetRowNum
                }
                val row = sheet.getRow(targetRowNum) ?: sheet.createRow(targetRowNum)

                if (existingRow == null) {
                    row.createCell(idIdx).setCellValue(UUID.randomUUID().toString().take(8))
                }
                for ((key, value) in rowData) {
                    if (key.isBlank()) continue
                    // Служебные колонки импорт не трогает: ID выдаём сами, теги задаются в приложении
                    if (key.equals(ID_COLUMN, ignoreCase = true) || key.equals(TAGS_COLUMN, ignoreCase = true)) continue
                    if (existingRow != null && value.isBlank()) continue // не затираем заполненное пустым
                    val idx = keyToIdx[key] ?: continue
                    row.createCell(idx).setCellValue(docfiller.util.ImportNormalizer.normalize(key, value))
                }
            }
            writeFile(wb)
            return ImportResult(added, updated, newColumns - ID_COLUMN - TAGS_COLUMN)
        }
    }


    private fun writeFile(wb: XSSFWorkbook) {
        try {
            file.outputStream().use { wb.write(it) }
        } catch (e: java.io.FileNotFoundException) {
            throw IllegalStateException("Не удалось записать ${file.name}. Закройте файл в Excel и повторите.", e)
        }
    }

    private fun parseTags(text: String): Set<String> =
        text.split(";", ",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private fun headerColumns(headerRow: Row, formatter: DataFormatter): List<String> {
        val last = headerRow.lastCellNum.toInt() // -1, если строка пустая
        if (last <= 0) return emptyList()
        return (0 until last).map { cellToString(headerRow.getCell(it), formatter) }
    }

    private fun cellToString(cell: org.apache.poi.ss.usermodel.Cell?, formatter: DataFormatter): String {
        if (cell == null) return ""
        return try {
            when (cell.cellType) {
                CellType.NUMERIC ->
                    if (DateUtil.isCellDateFormatted(cell)) {
                        cell.localDateTimeCellValue?.format(dateFormatter) ?: ""
                    } else {
                        val d = cell.numericCellValue
                        if (d == floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString()
                        else d.toString()
                    }
                // stringCellValue может быть null у "пустых" ячеек, оставшихся после удаления колонок
                CellType.STRING -> cell.stringCellValue?.trim() ?: ""
                CellType.BOOLEAN -> if (cell.booleanCellValue) "да" else "нет"
                else -> formatter.formatCellValue(cell)?.trim() ?: ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}

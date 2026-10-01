package docfiller.excel

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PeopleRepositoryTest {

    private fun tempFile(): File {
        val f = File.createTempFile("people", ".xlsx")
        f.delete()
        return f
    }

    @Test
    fun `создание файла и чтение примера`() {
        val repo = PeopleRepository(tempFile())
        val data = repo.load()
        assertTrue("ФИО" in data.columns)
        assertTrue("Теги" in data.columns)
        assertEquals(1, data.people.size)
        assertEquals("Иванов Иван Иванович", data.people[0].displayName)
        assertTrue(data.people[0].id.isNotBlank())
    }

    @Test
    fun `теги сохраняются и читаются обратно`() {
        val file = tempFile()
        val repo = PeopleRepository(file)
        val data = repo.load()
        val updated = data.people.map { it.copy(tags = setOf("судьи", "сентябрь")) }
        repo.saveTags(updated)

        val reloaded = PeopleRepository(file).load()
        assertEquals(setOf("судьи", "сентябрь"), reloaded.people[0].tags)
    }

    @Test
    fun `пользовательские колонки читаются, числа без хвоста дробной части`() {
        val file = tempFile()
        val repo = PeopleRepository(file)
        repo.load() // создаёт файл

        // Добавляем колонку и человека «как пользователь в Excel»
        org.apache.poi.xssf.usermodel.XSSFWorkbook(file.inputStream()).use { wb ->
            val sheet = wb.getSheetAt(0)
            val header = sheet.getRow(0)
            val colIdx = header.lastCellNum.toInt()
            header.createCell(colIdx).setCellValue("Разряд")
            sheet.getRow(1).createCell(colIdx).setCellValue("КМС")
            val row = sheet.createRow(2)
            row.createCell(1).setCellValue("Петров Пётр Петрович")
            row.createCell(4).setCellValue(123456789012.0) // ИНН числом
            file.outputStream().use { wb.write(it) }
        }

        val data = PeopleRepository(file).load()
        assertTrue("Разряд" in data.columns)
        assertEquals(2, data.people.size)
        assertEquals("КМС", data.people[0].value("Разряд"))
        val petrov = data.people[1]
        assertEquals("Петров Пётр Петрович", petrov.displayName)
        assertEquals("123456789012", petrov.value("ИНН"))
        assertTrue(petrov.id.isNotBlank()) // ID дозаполнен
    }

    @Test
    fun `удаление колонок в Excel не ломает чтение`() {
        val file = tempFile()
        val repo = PeopleRepository(file)
        repo.load() // создаёт файл

        // Удаляем колонки «Дата рождения» и «ИНН», как пользователь в Excel
        org.apache.poi.xssf.usermodel.XSSFWorkbook(file.inputStream()).use { wb ->
            val sheet = wb.getSheetAt(0)
            for (row in sheet) {
                row.getCell(3)?.let { row.removeCell(it) } // Дата рождения
                row.getCell(4)?.let { row.removeCell(it) } // ИНН
            }
            file.outputStream().use { wb.write(it) }
        }

        val data = PeopleRepository(file).load()
        // Дыры в заголовке сохраняют позиции остальных колонок
        assertEquals("Иванов Иван Иванович", data.people[0].displayName)
        assertEquals("10000", data.people[0].value("Сумма"))
        assertEquals(null, data.people[0].value("ИНН"))
        assertTrue(data.people[0].id.isNotBlank())
    }
}

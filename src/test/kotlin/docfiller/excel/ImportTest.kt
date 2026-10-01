package docfiller.excel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportParserTest {

    @Test
    fun `TSV из буфера обмена`() {
        val text = "ФИО\tИНН\tДата рождения\nИванов Иван Иванович\t123456\t01.01.1990\nПетров Пётр\t\t02.02.1992"
        val table = ImportParser.parseDelimited(text)
        assertEquals(listOf("ФИО", "ИНН", "Дата рождения"), table.headers)
        assertEquals(2, table.rows.size)
        val maps = table.toMaps()
        assertEquals("123456", maps[0]["ИНН"])
        assertEquals("", maps[1]["ИНН"])
    }

    @Test
    fun `CSV с точкой с запятой`() {
        val table = ImportParser.parseDelimited("ФИО;ИНН\nСидоров Сидор;999")
        assertEquals(listOf("ФИО", "ИНН"), table.headers)
        assertEquals(listOf(listOf("Сидоров Сидор", "999")), table.rows)
    }

    @Test
    fun `одна колонка трактуется как список ФИО`() {
        val table = ImportParser.parseDelimited("Иванов Иван Иванович\nПетров Пётр Петрович")
        assertEquals(listOf("ФИО"), table.headers)
        assertEquals(2, table.rows.size)
    }

    @Test
    fun `пустые строки и полностью пустые записи отбрасываются`() {
        val table = ImportParser.parseDelimited("ФИО\tИНН\nИванов\t1\n\n\t\nПетров\t2\n")
        assertEquals(2, table.toMaps().size)
    }
}

class ImportRowsTest {

    private fun tempFile(): java.io.File {
        val f = java.io.File.createTempFile("people", ".xlsx")
        f.delete()
        return f
    }

    @Test
    fun `импорт добавляет новых и обновляет существующих по ФИО`() {
        val file = tempFile()
        val repo = PeopleRepository(file)
        repo.load() // Иванов Иван Иванович, Сумма = 10000

        val result = repo.importRows(
            listOf(
                mapOf("ФИО" to "Иванов Иван Иванович", "ИНН" to "123456789012", "Сумма" to ""),
                mapOf("ФИО" to "Петров Пётр Петрович", "ИНН" to "111", "Новая графа" to "x"),
            ),
            upsertByFio = true,
        )

        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        assertEquals(listOf("Новая графа"), result.newColumns)

        val data = PeopleRepository(file).load()
        assertEquals(2, data.people.size)
        val ivanov = data.people.first { it.displayName == "Иванов Иван Иванович" }
        assertEquals("123456789012", ivanov.value("ИНН"))
        assertEquals("10000", ivanov.value("Сумма")) // пустое значение не затёрло заполненное
        val petrov = data.people.first { it.displayName == "Петров Пётр Петрович" }
        assertEquals("111", petrov.value("ИНН"))
        assertEquals("x", petrov.value("Новая графа"))
        assertTrue(petrov.id.isNotBlank())
    }

    @Test
    fun `без upsert все строки добавляются как новые`() {
        val file = tempFile()
        PeopleRepository(file).load()

        val result = PeopleRepository(file).importRows(
            listOf(mapOf("ФИО" to "Иванов Иван Иванович", "ИНН" to "1")),
            upsertByFio = false,
        )
        assertEquals(1, result.added)
        assertEquals(0, result.updated)
        assertEquals(2, PeopleRepository(file).load().people.size)
    }

    @Test
    fun `при импорте значения нормализуются, служебные колонки не перезаписываются`() {
        val file = tempFile()
        val repo = PeopleRepository(file)
        repo.load()

        repo.importRows(
            listOf(
                mapOf(
                    "ID" to "вражеский-id", // должен игнорироваться
                    "ФИО" to "Сидорова Анна Петровна",
                    "СНИЛС" to "12345678901",
                    "Паспорт серия" to "7010",
                    "Код подразделения" to "720003",
                    "Дата рождения" to "2014-07-06",
                ),
            ),
            upsertByFio = true,
        )

        val data = PeopleRepository(file).load()
        val person = data.people.first { it.displayName == "Сидорова Анна Петровна" }
        assertEquals("123-456-789 01", person.value("СНИЛС"))
        assertEquals("70 10", person.value("Паспорт серия"))
        assertEquals("720-003", person.value("Код подразделения"))
        assertEquals("06.07.2014", person.value("Дата рождения"))
        assertTrue(person.id != "вражеский-id" && person.id.isNotBlank())
    }
}

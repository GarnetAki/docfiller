package docfiller.template

import docfiller.model.Person
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun createDocx(file: File, block: XWPFDocument.() -> Unit) {
    XWPFDocument().use { doc ->
        doc.block()
        file.outputStream().use { doc.write(it) }
    }
}

private fun readText(file: File): String =
    XWPFDocument(file.inputStream()).use { doc ->
        collectParagraphs(doc).joinToString("\n") { p -> p.runs.joinToString("") { it.text() ?: "" } }
    }

class DocxFillerTest {

    @Test
    fun `замена плейсхолдера разрезанного на несколько runs`() {
        val template = File.createTempFile("tpl", ".docx")
        val output = File.createTempFile("out", ".docx")
        createDocx(template) {
            val p = createParagraph()
            // Имитируем разбиение Word'ом: {{per + son.ФИО}}
            p.createRun().setText("Исполнитель: {{per")
            p.createRun().setText("son.ФИО}}")
            p.createRun().setText(", далее по тексту.")
        }

        DocxFiller.fill(template, output, mapOf("person.ФИО" to "Иванов Иван Иванович"))

        val text = readText(output)
        assertTrue("Исполнитель: Иванов Иван Иванович, далее по тексту." in text)
    }

    @Test
    fun `плейсхолдер без значения остаётся в документе`() {
        val template = File.createTempFile("tpl", ".docx")
        val output = File.createTempFile("out", ".docx")
        createDocx(template) {
            createParagraph().createRun().setText("ИНН {{person.ИНН}}")
        }

        DocxFiller.fill(template, output, emptyMap())
        assertTrue("{{person.ИНН}}" in readText(output))
    }

    @Test
    fun `плейсхолдеры в таблицах заменяются`() {
        val template = File.createTempFile("tpl", ".docx")
        val output = File.createTempFile("out", ".docx")
        createDocx(template) {
            val table = createTable(1, 1)
            table.getRow(0).getCell(0).paragraphs[0].createRun().setText("Номер {{contract.number}}-СЗ")
        }

        DocxFiller.fill(template, output, mapOf("contract.number" to "42"))
        assertTrue("Номер 42-СЗ" in readText(output))
    }
}

class TemplateScannerTest {

    @Test
    fun `сканирование находит плейсхолдеры и разбирает модификаторы`() {
        val template = File.createTempFile("tpl", ".docx")
        createDocx(template) {
            createParagraph().createRun().setText("{{contract.number}} {{event.дата_договора}} {{person.ФИО}}")
            createParagraph().createRun().setText("{{person.Сумма:прописью}} {{person.ФИО}}")
            createParagraph().createRun().setText("{{неизвестный тип}}")
        }

        val placeholders = TemplateScanner.scan(template)
        assertEquals(
            listOf("contract.number", "event.дата_договора", "person.ФИО", "person.Сумма:прописью"),
            placeholders.map { it.key },
        )
        assertEquals("прописью", placeholders.last().modifier)
    }
}

class ContractGeneratorTest {

    @Test
    fun `генерация для двух человек с инкрементом номера и предупреждением о пустом поле`() {
        val template = File.createTempFile("tpl", ".docx")
        createDocx(template) {
            createParagraph().createRun()
                .setText("Договор № {{contract.number}} от {{event.дата}} с {{person.ФИО}}, ИНН {{person.ИНН}}, сумма {{person.Сумма:прописью}}")
        }
        val people = listOf(
            Person("1", mapOf("ФИО" to "Иванов Иван Иванович", "ИНН" to "123", "Сумма" to "10 800"), emptySet()),
            Person("2", mapOf("ФИО" to "Петров Пётр Петрович", "ИНН" to "", "Сумма" to "21000"), emptySet()),
        )
        val dir = java.nio.file.Files.createTempDirectory("gen").toFile()

        val result = ContractGenerator.generate(template, people, mapOf("дата" to "22.10.2025"), 42, dir)

        assertEquals(2, result.documents.size)
        assertEquals(listOf(42, 43), result.documents.map { it.contractNumber })
        assertEquals(1, result.warnings.size)
        assertTrue("ИНН" in result.warnings[0].message)

        val text1 = readText(result.documents[0].file)
        assertTrue("Договор № 42 от 22.10.2025 с Иванов Иван Иванович, ИНН 123, сумма Десять тысяч восемьсот" in text1, "actual: $text1")
        val text2 = readText(result.documents[1].file)
        assertTrue("Договор № 43 от 22.10.2025 с Петров Пётр Петрович" in text2)
        assertTrue("__________" in text2) // пустое поле — подчёркивание, чтобы не ломать разметку
        assertTrue("сумма Двадцать одна тысяча" in text2)
    }

    @Test
    fun `each-поля - переопределение при генерации и запасной вариант из Excel`() {
        val template = File.createTempFile("tpl", ".docx")
        createDocx(template) {
            createParagraph().createRun()
                .setText("{{person.ФИО}}: {{each.Сумма}} ({{each.Сумма:прописью}})")
        }
        val people = listOf(
            Person("1", mapOf("ФИО" to "Иванов Иван Иванович", "Сумма" to "10 800"), emptySet()),
            Person("2", mapOf("ФИО" to "Петров Пётр Петрович", "Сумма" to "21000"), emptySet()),
        )
        val dir = java.nio.file.Files.createTempDirectory("gen").toFile()

        val result = ContractGenerator.generate(
            template, people, emptyMap(), 1, dir,
            perPersonValues = mapOf("1" to mapOf("Сумма" to "5000"), "2" to mapOf("Сумма" to "")),
        )

        assertEquals(0, result.warnings.size)
        // У первого — значение из диалога, у второго (пустой ввод) — из Excel
        assertTrue("Иванов Иван Иванович: 5000 (Пять тысяч)" in readText(result.documents[0].file))
        assertTrue("Петров Пётр Петрович: 21000 (Двадцать одна тысяча)" in readText(result.documents[1].file))
    }
}

package docfiller.template

import docfiller.model.Person
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Сквозной тест на реальном шаблоне templates/Договор СЗ.docx. */
class SzTemplateTest {

    @Test
    fun `шаблон Договор СЗ заполняется без остаточных плейсхолдеров`() {
        val template = File("templates/Договор СЗ.docx")
        assumeTrue(template.exists(), "шаблон не найден")

        val person = Person(
            id = "t1",
            fields = mapOf(
                "ФИО" to "Иванов Иван Иванович",
                "Дата рождения" to "01.01.1990",
                "ИНН" to "123456789012",
                "СНИЛС" to "000-000-000 00",
                "Паспорт серия" to "00 00",
                "Паспорт номер" to "000000",
                "Паспорт дата выдачи" to "01.01.2020",
                "Паспорт кем выдан" to "ОТДЕЛОМ УФМС РОССИИ ПО Г. МОСКВЕ",
                "Код подразделения" to "000-000",
                "Адрес регистрации" to "г. Москва, ул. Примерная, д. 1, кв. 1",
                "Банк" to "ПАО «Банк»",
                "БИК" to "000000000",
                "К/С" to "00000000000000000000",
                "Л/С" to "00000000000000000000",
                "Вид услуг" to "услуги судьи 2 категории",
            ),
            tags = emptySet(),
        )
        val events = mapOf(
            "дата_договора" to "« 22 » октября 2025 г.",
            "дата_акта" to "« 23 » октября 2025 г.",
            "название_мероприятия" to "Чемпионата города по спортивному ориентированию",
            "срок_услуг" to "с 22 по 23 октября 2025 года",
            "срок_оплаты" to "01.11.2025",
        )
        val dir = java.nio.file.Files.createTempDirectory("sz").toFile()

        val result = ContractGenerator.generate(
            template, listOf(person), events, 42, dir,
            perPersonValues = mapOf("t1" to mapOf("Сумма" to "10 800")),
        )

        assertEquals(emptyList(), result.warnings)
        assertEquals(1, result.documents.size)

        val text = XWPFDocument(result.documents[0].file.inputStream()).use { doc ->
            collectParagraphs(doc).joinToString("\n") { p -> p.runs.joinToString("") { it.text() ?: "" } }
        }
        assertTrue("{{" !in text, "остались плейсхолдеры: ${Regex("\\{\\{[^{}]*\\}\\}").findAll(text).toList()}")
        assertTrue("№ 42-СЗ" in text)
        assertTrue("Иванов Иван Иванович, 01.01.1990 г.р." in text)
        assertTrue("И.И. Иванов" in text)
        assertTrue("10 800 (Десять тысяч восемьсот) рублей" in text)
        assertTrue("серия 00 00 № 000000" in text)
        assertTrue("« 22 » октября 2025 г." in text)
    }
}

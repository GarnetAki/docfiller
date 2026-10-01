package docfiller.template

import docfiller.model.GeneratedDocument
import docfiller.model.GenerationResult
import docfiller.model.GenerationWarning
import docfiller.model.Person
import docfiller.model.PlaceholderSource
import docfiller.util.Fio
import docfiller.util.NumberToWordsRu
import java.io.File

object ContractGenerator {

    /** Подстановка для незаполненных полей — короткая линия, чтобы не ломать разметку страниц. */
    private const val EMPTY_PLACEHOLDER = "__________"

    fun generate(
        templateFile: File,
        people: List<Person>,
        eventValues: Map<String, String>,
        startNumber: Int,
        outputDir: File,
        perPersonValues: Map<String, Map<String, String>> = emptyMap(),
    ): GenerationResult {
        val placeholders = TemplateScanner.scan(templateFile)
        val documents = mutableListOf<GeneratedDocument>()
        val warnings = mutableListOf<GenerationWarning>()

        outputDir.mkdirs()
        val templateName = templateFile.nameWithoutExtension

        people.forEachIndexed { index, person ->
            val number = startNumber + index
            val values = mutableMapOf<String, String>()

            for (placeholder in placeholders) {
                when (placeholder.source) {
                    PlaceholderSource.CONTRACT -> {
                        values[placeholder.key] = number.toString()
                    }
                    PlaceholderSource.EVENT -> {
                        val raw = eventValues[placeholder.name]?.takeIf { it.isNotBlank() }
                        if (raw == null) {
                            warnings += GenerationWarning("—", placeholder.name, isEventField = true)
                            values[placeholder.key] = EMPTY_PLACEHOLDER
                        } else {
                            values[placeholder.key] = applyModifier(raw, placeholder.modifier, placeholder.name, warnings)
                        }
                    }
                    PlaceholderSource.PERSON -> {
                        val raw = person.value(placeholder.name)
                        if (raw == null) {
                            warnings += GenerationWarning(person.displayName, placeholder.name, isEventField = false)
                            values[placeholder.key] = EMPTY_PLACEHOLDER
                        } else {
                            values[placeholder.key] = applyModifier(raw, placeholder.modifier, placeholder.name, warnings, person)
                        }
                    }
                    PlaceholderSource.EACH -> {
                        // Введённое при генерации значение; если пусто — запасной вариант из Excel.
                        val raw = perPersonValues[person.id]?.get(placeholder.name)?.takeIf { it.isNotBlank() }
                            ?: person.value(placeholder.name)
                        if (raw == null) {
                            warnings += GenerationWarning(person.displayName, placeholder.name, isEventField = false)
                            values[placeholder.key] = EMPTY_PLACEHOLDER
                        } else {
                            values[placeholder.key] = applyModifier(raw, placeholder.modifier, placeholder.name, warnings, person)
                        }
                    }
                }
            }

            val fileName = sanitizeFileName("$templateName №$number ${person.displayName}.docx")
            val outFile = File(outputDir, fileName)
            DocxFiller.fill(templateFile, outFile, values)
            documents += GeneratedDocument(person.displayName, number, outFile)
        }

        return GenerationResult(documents, warnings.distinct())
    }

    private fun applyModifier(
        value: String,
        modifier: String?,
        fieldName: String,
        warnings: MutableList<GenerationWarning>,
        person: Person? = null,
    ): String = when (modifier) {
        null -> value
        "инициалы" -> Fio.withInitials(value)
        "прописью" -> try {
            NumberToWordsRu.convert(NumberToWordsRu.parseAmount(value))
        } catch (e: NumberFormatException) {
            warnings += GenerationWarning(
                person?.displayName ?: "—", fieldName, isEventField = false,
                detail = "Поле «$fieldName» не похоже на число, прописью не построить: «$value»",
            )
            value
        }
        else -> value
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[<>:\"/\\\\|?*]"), "_").trim()
}

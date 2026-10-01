package docfiller.model

import java.io.File

data class Person(
    val id: String,
    val fields: Map<String, String>,
    val tags: Set<String>,
) {
    val displayName: String
        get() = fields.entries.firstOrNull { it.key.equals("ФИО", ignoreCase = true) }
            ?.value?.takeIf { it.isNotBlank() } ?: "(без ФИО, $id)"

    fun value(column: String): String? =
        fields.entries.firstOrNull { it.key.equals(column, ignoreCase = true) }
            ?.value?.takeIf { it.isNotBlank() }
}

enum class PlaceholderSource { CONTRACT, EVENT, PERSON, EACH }

data class Placeholder(
    val key: String,
    val source: PlaceholderSource,
    val name: String,
    val modifier: String?,
)

data class TemplateInfo(
    val file: File,
    val placeholders: List<Placeholder>,
) {
    val eventFields: List<String>
        get() = placeholders.filter { it.source == PlaceholderSource.EVENT }
            .map { it.name }.distinct()
    val personFields: List<String>
        get() = placeholders.filter { it.source == PlaceholderSource.PERSON }
            .map { it.name }.distinct()
    /** Поля, вводимые при генерации отдельно для каждого человека (например, сумма). */
    val eachFields: List<String>
        get() = placeholders.filter { it.source == PlaceholderSource.EACH }
            .map { it.name }.distinct()
    val usesContractNumber: Boolean
        get() = placeholders.any { it.source == PlaceholderSource.CONTRACT }
}

data class GenerationWarning(
    val personName: String,
    val fieldName: String,
    val isEventField: Boolean,
    val detail: String? = null,
) {
    val message: String
        get() {
            if (detail != null) return detail
            return if (isEventField) "Не заполнено поле события «$fieldName»" else "Не заполнено поле «$fieldName»"
        }
}

data class GeneratedDocument(val personName: String, val contractNumber: Int, val file: File)

data class GenerationResult(
    val documents: List<GeneratedDocument>,
    val warnings: List<GenerationWarning>,
)

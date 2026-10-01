package docfiller.util

/**
 * Нормализация значений при импорте: люди в форме пишут данные как попало
 * («720003», «12345678901», «2014-07-06»), а в таблице нужен единый формат.
 * Правила идемпотентны: уже правильное значение не меняется.
 */
object ImportNormalizer {

    fun normalize(columnName: String, value: String): String {
        val v = value.trim()
        if (v.isEmpty()) return v
        val name = columnName.lowercase()
        return when {
            "снилс" in name -> normalizeSnils(v)
            "код подразделения" in name -> withDigits(v, 6) { "${it.substring(0, 3)}-${it.substring(3, 6)}" }
            "паспорт серия" in name || name == "серия" ->
                withDigits(v, 4) { "${it.substring(0, 2)} ${it.substring(2, 4)}" }
            "дата" in name -> normalizeDate(v)
            else -> v
        }
    }

    /** «12345678901» → «123-456-789 01» */
    private fun normalizeSnils(value: String): String =
        withDigits(value, 11) { "${it.substring(0, 3)}-${it.substring(3, 6)}-${it.substring(6, 9)} ${it.substring(9, 11)}" }

    private inline fun withDigits(value: String, count: Int, format: (String) -> String): String {
        val digits = value.filter(Char::isDigit)
        return if (digits.length == count) format(digits) else value
    }

    private val datePatterns = listOf(
        // сначала однозначные (год в конце / в начале), потом остальные
        "dd/MM/yyyy", "dd.MM.yyyy", "dd-MM-yyyy",
        "d/M/yyyy", "d.M.yyyy", "d-M-yyyy",
        "yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd",
        "dd/MM/yy", "dd.MM.yy", "dd-MM-yy",
    ).map { java.time.format.DateTimeFormatter.ofPattern(it, java.util.Locale.ROOT) }

    private val outputDateFormat: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** Любую узнаваемую дату → «дд.ММ.гггг». Неузнаваемое возвращается как есть. */
    fun normalizeDate(value: String): String {
        val v = value.trim()
        for (pattern in datePatterns) {
            try {
                return java.time.LocalDate.parse(v, pattern).format(outputDateFormat)
            } catch (_: java.time.format.DateTimeParseException) {
            }
        }
        return value
    }
}

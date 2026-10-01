package docfiller.util

object Fio {

    /** «Иванов Иван Иванович» → «И.И. Иванов» */
    fun withInitials(fio: String): String {
        val parts = fio.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size < 2) return fio
        if (parts.any { it.contains('.') }) return fio
        val surname = parts[0]
        val initials = parts.drop(1).joinToString("") { "${it.first().uppercaseChar()}." }
        return "$initials $surname"
    }
}

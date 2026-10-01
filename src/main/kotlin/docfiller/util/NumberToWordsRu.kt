package docfiller.util

/**
 * Число прописью по-русски (для сумм в договорах), первая буква заглавная.
 * Разряд тысяч склоняется в женском роде («одна тысяча», «две тысячи»).
 */
object NumberToWordsRu {

    private val unitsM = arrayOf(
        "", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять",
        "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
        "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"
    )
    private val unitsF = arrayOf(
        "", "одна", "две", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять",
        "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
        "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"
    )
    private val tens = arrayOf(
        "", "", "двадцать", "тридцать", "сорок", "пятьдесят",
        "шестьдесят", "семьдесят", "восемьдесят", "девяносто"
    )
    private val hundreds = arrayOf(
        "", "сто", "двести", "триста", "четыреста", "пятьсот",
        "шестьсот", "семьсот", "восемьсот", "девятьсот"
    )

    fun convert(number: Long): String {
        require(number >= 0) { "Отрицательные числа не поддерживаются" }
        if (number == 0L) return "Ноль"
        val words = convertInternal(number)
        return words.replaceFirstChar { it.uppercase() }
    }

    private fun convertInternal(number: Long): String {
        val parts = mutableListOf<String>()
        var rest = number

        val groups = arrayOf(
            Triple(1_000_000_000L, arrayOf("миллиард", "миллиарда", "миллиардов"), false),
            Triple(1_000_000L, arrayOf("миллион", "миллиона", "миллионов"), false),
            Triple(1_000L, arrayOf("тысяча", "тысячи", "тысяч"), true),
        )
        for ((divisor, forms, feminine) in groups) {
            val group = (rest / divisor).toInt()
            if (group > 0) {
                parts += tripleToWords(group, feminine)
                parts += plural(group, forms)
            }
            rest %= divisor
        }
        if (rest > 0) parts += tripleToWords(rest.toInt(), feminine = false)

        return parts.joinToString(" ")
    }

    private fun tripleToWords(value: Int, feminine: Boolean): List<String> {
        val words = mutableListOf<String>()
        val h = value / 100
        val rem = value % 100
        if (h > 0) words += hundreds[h]
        if (rem >= 20) {
            words += tens[rem / 10]
            val u = rem % 10
            if (u > 0) words += (if (feminine) unitsF else unitsM)[u]
        } else if (rem > 0) {
            words += (if (feminine) unitsF else unitsM)[rem]
        }
        return words
    }

    private fun plural(value: Int, forms: Array<String>): String {
        val rem100 = value % 100
        if (rem100 in 11..14) return forms[2]
        return when (value % 10) {
            1 -> forms[0]
            2, 3, 4 -> forms[1]
            else -> forms[2]
        }
    }

    /** «10 800», «10800», «10 800,00» → 10800 */
    fun parseAmount(text: String): Long {
        val cleaned = text
            .replace("\u00A0", "")
            .replace(" ", "")
            .substringBefore(',')
            .substringBefore('.')
        return cleaned.toLong()
    }
}

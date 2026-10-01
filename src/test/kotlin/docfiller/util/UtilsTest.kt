package docfiller.util

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberToWordsRuTest {

    @Test
    fun basic() {
        assertEquals("Ноль", NumberToWordsRu.convert(0))
        assertEquals("Один", NumberToWordsRu.convert(1))
        assertEquals("Десять", NumberToWordsRu.convert(10))
        assertEquals("Одиннадцать", NumberToWordsRu.convert(11))
        assertEquals("Двадцать пять", NumberToWordsRu.convert(25))
        assertEquals("Сто", NumberToWordsRu.convert(100))
        assertEquals("Двести пятьдесят шесть", NumberToWordsRu.convert(256))
    }

    @Test
    fun thousandsFeminine() {
        assertEquals("Десять тысяч восемьсот", NumberToWordsRu.convert(10800))
        assertEquals("Одна тысяча", NumberToWordsRu.convert(1000))
        assertEquals("Две тысячи", NumberToWordsRu.convert(2000))
        assertEquals("Пять тысяч", NumberToWordsRu.convert(5000))
        assertEquals("Двадцать одна тысяча", NumberToWordsRu.convert(21000))
        assertEquals("Сто двенадцать тысяч", NumberToWordsRu.convert(112000))
    }

    @Test
    fun millions() {
        assertEquals("Один миллион", NumberToWordsRu.convert(1000000))
        assertEquals("Два миллиона триста тысяч", NumberToWordsRu.convert(2300000))
    }

    @Test
    fun parseAmounts() {
        assertEquals(10800, NumberToWordsRu.parseAmount("10 800"))
        assertEquals(10800, NumberToWordsRu.parseAmount("10800"))
        assertEquals(10800, NumberToWordsRu.parseAmount("10 800,00"))
        assertEquals(10800, NumberToWordsRu.parseAmount("10 800.00"))
    }
}

class FioTest {

    @Test
    fun initials() {
        assertEquals("И.И. Иванов", Fio.withInitials("Иванов Иван Иванович"))
        assertEquals("П.П. Петров", Fio.withInitials("Петров  Пётр   Петрович"))
        assertEquals("И. Иванов", Fio.withInitials("Иванов Иван"))
        assertEquals("Мадонна", Fio.withInitials("Мадонна"))
        assertEquals("И.И. Иванов", Fio.withInitials("И.И. Иванов"))
    }
}

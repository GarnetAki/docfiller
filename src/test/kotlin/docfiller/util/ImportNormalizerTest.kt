package docfiller.util

import kotlin.test.Test
import kotlin.test.assertEquals

class ImportNormalizerTest {

    @Test
    fun `СНИЛС из одних цифр и обратно`() {
        assertEquals("123-456-789 01", ImportNormalizer.normalize("СНИЛС", "12345678901"))
        assertEquals("175-329-379 95", ImportNormalizer.normalize("СНИЛС", "175-329-379 95"))
        assertEquals("123-456-789 01", ImportNormalizer.normalize("СНИЛС", "123 456 789 01"))
        assertEquals("123-456-789 01", ImportNormalizer.normalize("СНИЛС", "123.456.789-01"))
        // неверное число цифр — не трогаем
        assertEquals("12345", ImportNormalizer.normalize("СНИЛС", "12345"))
    }

    @Test
    fun `серия паспорта`() {
        assertEquals("71 22", ImportNormalizer.normalize("Паспорт серия", "7122"))
        assertEquals("71 22", ImportNormalizer.normalize("Паспорт серия", "71 22"))
        assertEquals("70 10", ImportNormalizer.normalize("Паспорт серия", "7010"))
    }

    @Test
    fun `код подразделения`() {
        assertEquals("720-003", ImportNormalizer.normalize("Код подразделения", "720003"))
        assertEquals("720-003", ImportNormalizer.normalize("Код подразделения", "720-003"))
    }

    @Test
    fun `даты приводятся к дд точка ММ точка гггг`() {
        assertEquals("06.07.2014", ImportNormalizer.normalize("Дата рождения", "2014-07-06"))
        assertEquals("02.07.2002", ImportNormalizer.normalize("Дата рождения", "02/07/2002"))
        assertEquals("30.06.2020", ImportNormalizer.normalize("Паспорт дата выдачи", "2020-06-30"))
        assertEquals("30.06.2020", ImportNormalizer.normalize("Паспорт дата выдачи", "30.06.2020"))
        assertEquals("01.02.2003", ImportNormalizer.normalize("Дата рождения", "1/2/2003"))
        // непохожее на дату — как есть
        assertEquals("когда-то", ImportNormalizer.normalize("Дата рождения", "когда-то"))
    }

    @Test
    fun `прочие колонки не меняются`() {
        assertEquals("УМВД России", ImportNormalizer.normalize("Паспорт кем выдан", "УМВД России"))
        assertEquals("12345678", ImportNormalizer.normalize("БИК", "12345678"))
    }
}

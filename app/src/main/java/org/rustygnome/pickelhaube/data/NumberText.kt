package org.rustygnome.pickelhaube.data

import org.rustygnome.pickelhaube.tr064.FritzDialer

/**
 * Phone numbers in plain text – shared from another app or pasted –, written the way people write
 * them: "+49 (0)30 / 123 45-6", "030 1234556", "(030) 12 34 55 6".
 */
object NumberText {

    /** Digits with the separators used in numbers, on one line; an international "+" or "(" may lead. */
    private val CANDIDATE = Regex("""(?<![\w+])(?:\+|\()?\d[\d  ()./\-–]*\d""")

    /** Dates and times, which look like numbers: 12.03.2026, 2026-03-12, 12/03/26. */
    private val DATE = Regex("""\d{1,4}[./\-]\d{1,2}[./\-]\d{1,4}|\d{1,2}[.:]\d{2}""")

    /** Version numbers: 1.2.0, 16.0.3 (4) – no phone number starts that way. */
    private val VERSION = Regex("""^\d{1,2}\.\d{1,2}\.\d{1,3}(?!\d)""")

    /** The national "(0)" written after the country code: "+49 (0)30" is dialled "+4930". */
    private val NATIONAL_ZERO = Regex("""^((?:\+|00)\d{1,3})[  ]*\(0\)""")

    private const val MIN_DIGITS = 5
    private const val MAX_DIGITS = 17

    /** The phone numbers in [text], cleaned for dialling, in their order and each once. */
    fun extract(text: String): List<String> =
        CANDIDATE.findAll(text)
            .map { it.value.trim() }
            .filterNot { DATE.matches(it) || VERSION.containsMatchIn(it) }
            .map(::clean)
            .filter { number -> number.count(Char::isDigit) in MIN_DIGITS..MAX_DIGITS }
            .distinct()
            .toList()

    /**
     * [raw] as dialled: without the national "(0)" after a country code, and only digits and + * # –
     * followed by ",", and the digits to type once connected, if it has some ("030 1234567 x 42" →
     * "0301234567,42"; see [FritzDialer.splitExtension]).
     */
    fun clean(raw: String): String = FritzDialer.splitExtension(NATIONAL_ZERO.replace(raw.trim(), "$1")).written
}

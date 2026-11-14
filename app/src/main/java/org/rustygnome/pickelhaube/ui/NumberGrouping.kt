package org.rustygnome.pickelhaube.ui

import android.telephony.PhoneNumberUtils
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import org.rustygnome.pickelhaube.tr064.DialingCodes

/** What a number may be given between its digits to be read more easily. */
private const val SEPARATORS = " -/()"

/**
 * [number] as the dialer shows it: its leading digits grouped as numbers are written – "030 1234567",
 * "+49 171 2345678" – by [format], the rest (a pause and the digits after it) as it is. Only
 * separators are added: should [format] change a digit, add one or leave one out, or should the
 * number come with separators of its own already, it is shown as it is. Service codes and internal
 * numbers (`*31#…`, `**610`) and numbers of fewer than four digits are left alone.
 */
internal fun groupedNumber(number: String, format: (String) -> String?): String {
    val end = number.indexOfFirst { !it.isDigit() && it != '+' }.let { if (it < 0) number.length else it }
    if (end < number.length && number[end] in SEPARATORS) return number
    val head = number.substring(0, end)
    if (head.count(Char::isDigit) < 4 || head.lastIndexOf('+') > 0) return number
    val formatted = format(head) ?: return number
    if (formatted.any { !it.isDigit() && it != '+' && it !in SEPARATORS }) return number
    if (formatted.filter { it.isDigit() || it == '+' } != head) return number
    return formatted + number.substring(end)
}

/**
 * Where the characters of [original] stand in [shown], which is [original] with separators put in
 * (see [groupedNumber]).
 */
internal fun groupingOffsets(original: String, shown: String): OffsetMapping {
    // positions[i]: the place of original[i] in shown; one more for the end.
    val positions = IntArray(original.length + 1)
    var j = 0
    for (i in original.indices) {
        while (j < shown.length && shown[j] != original[i]) j++
        positions[i] = j
        j++
    }
    positions[original.length] = shown.length
    return object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int = positions[offset.coerceIn(0, original.length)]
        override fun transformedToOriginal(offset: Int): Int =
            (0 until original.length).count { positions[it] < offset }
    }
}

/** Shows the number in the dialer grouped by [format] (see [groupedNumber]); what is dialled stays exactly what was entered. */
internal class NumberGrouping(private val format: (String) -> String?) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val shown = groupedNumber(original, format)
        if (shown == original) return TransformedText(text, OffsetMapping.Identity)
        return TransformedText(AnnotatedString(shown), groupingOffsets(original, shown))
    }

    override fun equals(other: Any?) = other is NumberGrouping && other.format == format
    override fun hashCode() = format.hashCode()
}

/**
 * Groups numbers as Android writes them in the country of the box in use (by its [codes]), else in
 * the phone's ([countryIso]) – keeping the national prefix as typed ("030 …", not "+49 30 …").
 */
internal fun phoneNumberFormat(countryIso: String, codes: DialingCodes?): (String) -> String? = { head ->
    // Its country code tells Android the box' country, for a number typed without one.
    val international = codes?.let { internationalForm(head, it) }?.takeIf { it.startsWith('+') }
    runCatching { PhoneNumberUtils.formatNumber(head, international, countryIso.ifEmpty { "ZZ" }) }.getOrNull()
}

/**
 * Shows numbers grouped as numbers are written (see [groupedNumber]) – in the lists, the suggestions
 * and the call status, as in the number field. Anything that is no plain number (a name, a service
 * code, a number written with separators of its own) is shown as it is. What it made is kept, so a
 * list scrolling by does not format the same numbers again and again.
 */
internal class NumberDisplay(private val format: (String) -> String?) {
    private val made = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > CACHE_SIZE
    }

    fun show(text: String): String = synchronized(made) { made.getOrPut(text) { groupedNumber(text, format) } }

    private companion object {
        const val CACHE_SIZE = 1000
    }
}

/** How a number is shown on screen ([NumberDisplay.show]); as it is outside the app's screens (previews, tests). */
internal val LocalNumberDisplay = staticCompositionLocalOf<(String) -> String> { { it } }

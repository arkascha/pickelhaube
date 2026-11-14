package org.rustygnome.pickelhaube.ui

/** A number being edited and where the cursor stands in it after the edit. */
internal data class Edited(val number: String, val cursor: Int)

/** [c] typed into [number] at the cursor [at] – in place of what is selected (from..to); the cursor after it. */
internal fun typed(number: String, at: IntRange, c: Char): Edited =
    Edited(number.substring(0, at.first) + c + number.substring(at.last), at.first + 1)

/**
 * ⌫ in [number] with the cursor [at]: what is selected deleted, else the character before the cursor;
 * null at the start of the field, where there is nothing before it.
 */
internal fun deletedBefore(number: String, at: IntRange): Edited? {
    val from = if (at.first < at.last) at.first else at.first - 1
    if (from < 0) return null
    return Edited(number.substring(0, from) + number.substring(at.last), from)
}

package com.hafeziquran.app.core.util

private val BengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

/**
 * Renders an [Int] with Bengali numerals, matching the original app.
 * Non-digit characters (minus signs, separators) pass through untouched.
 */
fun Int.toBanglaDigits(): String = toString().map { char ->
    if (char in '0'..'9') BengaliDigits[char - '0'] else char
}.joinToString("")

/**
 * Parses a string that may use either ASCII (`0`-`9`) or Bengali (`০`-`৯`) digits.
 *
 * Returns `null` if the string is empty or holds any other character, so callers
 * can keep treating the result as "not a number". The inverse of [toBanglaDigits]:
 * a page number typed on a Bangla keyboard round-trips to the same [Int].
 */
fun String.toIntOrNullBangla(): Int? {
    if (isEmpty()) return null
    val builder = StringBuilder(length)
    for (char in this) {
        when (char) {
            in '0'..'9' -> builder.append(char)
            in '০'..'৯' -> builder.append(char - '০')
            else -> return null
        }
    }
    return builder.toString().toIntOrNull()
}

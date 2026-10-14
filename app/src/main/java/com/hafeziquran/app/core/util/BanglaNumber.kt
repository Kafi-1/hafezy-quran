package com.hafeziquran.app.core.util

private val BengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

/**
 * Renders an [Int] with Bengali numerals, matching the original app.
 * Non-digit characters (minus signs, separators) pass through untouched.
 */
fun Int.toBanglaDigits(): String = toString().map { char ->
    if (char in '0'..'9') BengaliDigits[char - '0'] else char
}.joinToString("")

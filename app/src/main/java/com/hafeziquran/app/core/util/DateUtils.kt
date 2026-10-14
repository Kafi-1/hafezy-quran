package com.hafeziquran.app.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

/** Human-readable bookmark timestamp, e.g. `14 Oct 2026`. */
fun Long.toReadableDate(): String = dateFormat.format(Date(this))

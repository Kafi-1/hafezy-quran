package com.hafeziquran.app.core.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Short haptic used by the digital tasbeeh.
 *
 * Kept out of the composables so the ViewModel owns the moment a tap lands while
 * the platform call stays in one place. Both effects use API 26 primitives, so no
 * version guards are needed above [vibrator].
 */
fun Context.vibrateTick() {
    vibrator().vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
}

/** Slightly longer double-bump played when a round completes. */
fun Context.vibrateRoundComplete() {
    vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 45, 60, 90), -1))
}

private fun Context.vibrator(): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

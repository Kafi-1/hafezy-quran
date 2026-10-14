package com.hafeziquran.app.core.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.hafeziquran.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the short Tasbeeh counter click.
 *
 * [SoundPool] (not `MediaPlayer`) because the tap rate is high and the clip is
 * tiny — `SoundPool` decodes once and mixes overlapping taps without latency.
 *
 * The sample is loaded asynchronously, so [play] is a no-op until decoding
 * finishes (and forever if the asset is missing or corrupt). That is deliberate:
 * a missing sound must never crash or block the counter.
 */
@Singleton
class ClickSoundPlayer @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val loaded = AtomicBoolean(false)

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val soundId: Int = runCatching {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            loaded.set(status == 0)
            if (status != 0) Log.w(TAG, "SoundPool failed to load click sound (status=$status)")
        }
        soundPool.load(context, R.raw.click_sound, 1)
    }.getOrElse {
        Log.e(TAG, "Could not load click sound", it)
        0
    }

    /** Fire-and-forget tap sound. Safe to call from the main thread. */
    fun play() {
        if (!loaded.get() || soundId == 0) return
        runCatching {
            soundPool.play(soundId, VOLUME, VOLUME, /* priority = */ 1, /* loop = */ 0, /* rate = */ 1f)
        }
    }

    private companion object {
        const val TAG = "ClickSoundPlayer"
        const val VOLUME = 1f
    }
}

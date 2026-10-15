package com.hafeziquran.app.data.remote

import com.hafeziquran.app.core.constants.AppConstants
import com.hafeziquran.app.data.model.Reciter

/** Builds the per-surah streaming URL on the islamic.network CDN. */
object QuranAudioApi {

    fun surahUrl(reciter: Reciter, surahNumber: Int): String =
        "${AppConstants.AUDIO_BASE_URL}/${reciter.slug}/$surahNumber.mp3"
}

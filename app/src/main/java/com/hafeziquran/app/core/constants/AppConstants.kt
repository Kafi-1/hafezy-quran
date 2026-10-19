package com.hafeziquran.app.core.constants

/** Compile-time constants shared across data + UI layers. */
object AppConstants {

    /** Path (inside `assets/`) of the scanned Hafezi Mushaf. */
    const val QURAN_PDF_ASSET = "quran.pdf"

    /** Path (inside `assets/`) of the Tasbeeh catalogue. */
    const val TASBEEH_JSON_ASSET = "tasbeeh.json"

    /** Path (inside `assets/`) of the surah/para page index. */
    const val QURAN_INDEX_ASSET = "quran_index.json"

    /** Path (inside `assets/`) of the 114-surah audio catalogue. */
    const val SURAHS_JSON_ASSET = "surahs.json"

    /** Path (inside `assets/`) of the Islamic-books catalogue. */
    const val BOOKS_JSON_ASSET = "books.json"

    /**
     * Physical page count of the bundled `quran.pdf`. The single source of truth
     * for clamping: `quran_index.json` stores standard 604-page Madani numbers and
     * [com.hafeziquran.app.data.repository.QuranIndexRepository.resolvePage] shifts
     * them by `pageOffset` (currently 8) so they land on this 612-page scan.
     */
    const val PHYSICAL_PAGE_COUNT = 612

    /** Downloadable Bangla translation mushaf (optional, ~25 MB). */
    const val BANGLA_QURAN_URL = "https://tv.kafi.store.cv/banglaquryan.pdf"

    /** File name the Bangla mushaf is stored under inside `filesDir`. */
    const val BANGLA_QURAN_FILE = "bangla_quran.pdf"

    /** Base path of the per-surah recitation CDN. */
    const val AUDIO_BASE_URL = "https://cdn.islamic.network/quran/audio-surah/128"

    /** Uthmani Arabic text, one entry per ayah. Used by the offline Sobok checker. */
    const val QURAN_TEXT_ASSET = "quran_uthmani.json"

    /**
     * Optional Whisper model for the Sobok feature (~78 MB). Never bundled in the
     * APK — downloaded on first use and kept in `filesDir`.
     */
    const val SOBOK_MODEL_URL =
        "https://huggingface.co/sadrapp/whisper-base-ar-quran-ggml/resolve/main/ggml-model-q8_0.bin"

    const val SOBOK_MODEL_DIR = "models"
    const val SOBOK_MODEL_FILE = "ggml-model-q8_0.bin"

    /**
     * Server-reported size is 81,768,585 bytes. Anything smaller than this floor
     * is treated as a truncated/corrupt download and rejected.
     */
    const val SOBOK_MODEL_MIN_BYTES = 75L * 1024L * 1024L
}

package com.hafeziquran.app.core.constants

/** Compile-time constants shared across data + UI layers. */
object AppConstants {

    /** Path (inside `assets/`) of the scanned Hafezi Mushaf. */
    const val QURAN_PDF_ASSET = "quran.pdf"

    /** Path (inside `assets/`) of the Tasbeeh catalogue. */
    const val TASBEEH_JSON_ASSET = "tasbeeh.json"

    /** Path (inside `assets/`) of the surah/para page index. */
    const val QURAN_INDEX_ASSET = "quran_index.json"

    /** Path (inside `assets/`) of the Madani page → ayah reference index. */
    const val QURAN_PAGES_ASSET = "quran_pages.json"

    /** Standard Madani page count (the text page index ignores the PDF offset). */
    const val MADANI_PAGE_COUNT = 604

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
     * Optional Whisper model for the Sobok feature. Never bundled in the APK —
     * downloaded on first use and kept in `filesDir`.
     *
     * The tiny multilingual model (~75 MB) replaces the older base model: it is
     * several times faster on-device while still supporting Arabic via
     * `language = "ar"`.
     */
    const val SOBOK_MODEL_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"

    const val SOBOK_MODEL_DIR = "models"
    const val SOBOK_MODEL_FILE = "ggml-tiny.bin"

    /** Previous base model file name; deleted once the tiny model is validated. */
    const val SOBOK_LEGACY_MODEL_FILE = "ggml-model-q8_0.bin"

    /**
     * Lower bound for `ggml-tiny.bin` (~75 MB). Keep this close to the real size:
     * the GGML magic-byte check only inspects the first four bytes, so it cannot
     * detect a stream that ends early — the size floor is the only guard against a
     * truncated download. A loose fraction (e.g. 38 MB) would let a half-written
     * file through, and whisper.cpp would then fail to load it at runtime.
     */
    const val SOBOK_MODEL_MIN_BYTES = 70L * 1024L * 1024L
}

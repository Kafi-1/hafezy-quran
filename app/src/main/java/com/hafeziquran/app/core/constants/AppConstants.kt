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

    /** Standard Madani page count. Clamps the physical→Madani reverse mapping. */
    const val MADANI_PAGE_COUNT = 604

    /** Path (inside `assets/`) of the 114-surah audio catalogue. */
    const val SURAHS_JSON_ASSET = "surahs.json"

    /** Path (inside `assets/`) of the Islamic-books catalogue. */
    const val BOOKS_JSON_ASSET = "books.json"

    /**
     * Physical page count of the bundled `quran.pdf`. The single source of truth
     * for clamping: `quran_index.json` stores physical page numbers directly (the
     * scan's pagination drifts from the 604-page Madani index), so
     * [com.hafeziquran.app.data.repository.QuranIndexRepository.resolvePage]
     * applies only the global `pageOffset` nudge (currently 0).
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
     * Optional FastConformer Quranic-Arabic model for the Sobok feature. Never
     * bundled in the APK — downloaded on first use and kept in `filesDir`.
     *
     * `muhdur/tilawi-fastconformer-quran` is an NVIDIA NeMo FastConformer-CTC
     * model fine-tuned on Quranic recitation, exported as a single ONNX graph
     * (preprocessor included) that runs offline through ONNX Runtime. License:
     * CC-BY-4.0, so it is redistributable in a free app.
     *
     * Two files are needed: the graph itself, and the SentencePiece vocabulary
     * used to turn the CTC output ids into Arabic text.
     */
    const val SOBOK_MODEL_URL =
        "https://huggingface.co/muhdur/tilawi-fastconformer-quran/resolve/main/" +
            "fastconformer_full_mixed.onnx"

    const val SOBOK_VOCAB_URL =
        "https://huggingface.co/muhdur/tilawi-fastconformer-quran/resolve/main/vocab.json"

    const val SOBOK_MODEL_DIR = "models"
    const val SOBOK_MODEL_FILE = "tilawi_fastconformer.onnx"
    const val SOBOK_VOCAB_FILE = "tilawi_vocab.json"

    /**
     * Superseded model files from the old whisper.cpp era, deleted once the
     * FastConformer model is validated. The new files have different names, so an
     * obsolete ggml model is never mistaken for the current one.
     */
    val SOBOK_LEGACY_MODEL_FILES =
        listOf("ggml-tiny.bin", "ggml-model-q8_0.bin", "ggml-tiny-q8_0.bin")

    /**
     * Lower bound for the ONNX graph (real size ~84 MiB). The download itself is
     * atomic (`.part` renamed on completion), so this floor only guards against a
     * server that truncates the stream with a 200 — a loose fraction would let a
     * half-written graph through and ONNX Runtime would then fail to load it.
     */
    const val SOBOK_MODEL_MIN_BYTES = 80L * 1024L * 1024L

    /** Lower bound for `vocab.json` (real size ~21 KB). */
    const val SOBOK_VOCAB_MIN_BYTES = 4L * 1024L
}

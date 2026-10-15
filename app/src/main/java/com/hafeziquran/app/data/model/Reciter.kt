package com.hafeziquran.app.data.model

/**
 * A reciter available on the islamic.network CDN.
 *
 * [slug] is the path segment the CDN expects
 * (`…/audio-surah/128/{slug}/{surah}.mp3`). Only slugs verified to return 200 for
 * the whole 114-surah set are listed — the CDN 403s several otherwise well-known
 * reciters (Sudais, Maher, Husary) at this path, and offering them would look
 * like an app bug rather than a missing file.
 */
enum class Reciter(
    val slug: String,
    val banglaName: String,
    val englishName: String,
) {
    ALAFASY("ar.alafasy", "মিশারী আল-আফাসী", "Mishary Alafasy"),
    ABDUL_BASIT("ar.abdulbasitmurattal", "আবদুল বাসিত (মুরাত্তাল)", "Abdul Basit"),
    AYYUB("ar.muhammadayyub", "মুহাম্মদ আইয়ুব", "Muhammad Ayyub"),
    SHURAIM("ar.saudalshuraim", "সৌদ আল-শুরাইম", "Saud Al-Shuraim"),
    BASFAR("ar.abdullahbasfar", "আবদুল্লাহ বাসফার", "Abdullah Basfar"),
}

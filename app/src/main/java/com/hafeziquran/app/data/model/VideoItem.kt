package com.hafeziquran.app.data.model

/** One entry of the Firestore `videos` collection. */
data class VideoItem(
    val id: String,
    val title: String,
    val youtubeId: String,
    val thumbnailUrl: String,
    val order: Long,
)

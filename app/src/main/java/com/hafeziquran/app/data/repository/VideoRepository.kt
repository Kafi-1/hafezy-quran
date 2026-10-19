package com.hafeziquran.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.hafeziquran.app.data.model.VideoItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live catalogue of the Firestore `videos` collection, ordered by `order`.
 *
 * The `isActive` filter is applied client-side on purpose: a combined
 * `whereEqualTo("isActive", true) + orderBy("order")` query needs a composite
 * index, and a missing index fails the whole list at runtime. The collection is
 * small, so fetching inactive documents is cheaper than that failure mode.
 */
@Singleton
class VideoRepository @Inject constructor() {

    fun videos(): Flow<List<VideoItem>> = callbackFlow {
        val registration = FirebaseFirestore.getInstance()
            .collection(COLLECTION)
            .orderBy(FIELD_ORDER, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val videos = snapshot?.documents.orEmpty().mapNotNull { document ->
                    if (document.getBoolean(FIELD_ACTIVE) == false) return@mapNotNull null

                    val youtubeId = document.getString(FIELD_YOUTUBE_ID)?.trim().orEmpty()
                    if (youtubeId.isEmpty()) return@mapNotNull null

                    VideoItem(
                        id = document.id,
                        title = document.getString(FIELD_TITLE)?.trim().orEmpty()
                            .ifEmpty { "ভিডিও" },
                        youtubeId = youtubeId,
                        thumbnailUrl = document.getString(FIELD_THUMBNAIL)?.trim().orEmpty()
                            .ifEmpty { "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg" },
                        order = document.getLong(FIELD_ORDER) ?: 0L,
                    )
                }
                trySend(videos)
            }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val COLLECTION = "videos"
        const val FIELD_TITLE = "title"
        const val FIELD_YOUTUBE_ID = "youtubeId"
        const val FIELD_THUMBNAIL = "thumbnailUrl"
        const val FIELD_ORDER = "order"
        const val FIELD_ACTIVE = "isActive"
    }
}

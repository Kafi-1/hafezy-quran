package com.hafeziquran.app.data.recitation

import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bounded FIFO between the recorder and the single Whisper worker.
 *
 * A five-second ayah can take longer to transcribe than to recite, so the queue
 * is deliberately small: four pending segments keeps worst-case memory around
 * 4 × 20 s × 32 KB/s ≈ 2.5 MB, and the UI shows a "Processing..." badge instead of
 * letting the backlog grow without bound. [enqueue] never suspends — dropping a
 * segment is better than stalling the microphone.
 */
class AyahProcessingQueue(capacity: Int = MAX_PENDING_SEGMENTS) {

    val channel: Channel<AyahSegment> = Channel(capacity)

    private val pending = AtomicInteger(0)
    private val closed = AtomicBoolean(false)

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    /** Returns false when the queue was full and the segment was dropped. */
    fun enqueue(segment: AyahSegment): Boolean {
        if (closed.get()) return false
        if (channel.trySend(segment).isSuccess) {
            _pendingCount.value = pending.incrementAndGet()
            return true
        }
        Log.w(TAG, "segment=${segment.id} dropped: queue full (cap=$MAX_PENDING_SEGMENTS)")
        return false
    }

    /** Called by the worker the moment it takes a segment off the channel. */
    fun onDequeued() {
        val remaining = pending.updateAndGet { (it - 1).coerceAtLeast(0) }
        _pendingCount.value = remaining
    }

    /**
     * Stops accepting work and throws away whatever is still buffered. Used when
     * the user stops recording: finishing a backlog of already-spoken ayahs after
     * the session ended would only update a screen the user has left.
     */
    fun closeAndDrain() {
        if (!closed.compareAndSet(false, true)) return
        channel.close()
        var drained = 0
        while (channel.tryReceive().isSuccess) drained++
        if (drained > 0) {
            _pendingCount.value = pending.updateAndGet { (it - drained).coerceAtLeast(0) }
        }
        _pendingCount.value = pending.get().coerceAtLeast(0)
    }

    fun cancel() = closeAndDrain()

    companion object {
        const val MAX_PENDING_SEGMENTS = 4
        private const val TAG = "LIVE_SOBOK_QUEUE"
    }
}

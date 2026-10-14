package com.hafeziquran.app.core.util

/**
 * Minimal result envelope for asynchronous work that can fail for *expected*
 * reasons (missing PDF, corrupt page, IO). Keeps ViewModels from leaking
 * try/catch into every function while staying trivially pattern-matchable.
 */
sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>

    data class Success<T>(val data: T) : Resource<T>

    data class Error(val throwable: Throwable, val message: String? = null) : Resource<Nothing>
}

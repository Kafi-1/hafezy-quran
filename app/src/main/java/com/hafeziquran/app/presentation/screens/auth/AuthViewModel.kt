package com.hafeziquran.app.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafeziquran.app.data.auth.AuthRepository
import com.hafeziquran.app.data.auth.AuthUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    enum class Mode { LOGIN, REGISTER }

    data class UiState(
        val mode: Mode = Mode.LOGIN,
        val name: String = "",
        val email: String = "",
        val password: String = "",
        val passwordVisible: Boolean = false,
        val loading: Boolean = false,
        val googleLoading: Boolean = false,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** Drives the automatic pop back to Settings once a session exists. */
    val user: StateFlow<AuthUser?> = authRepository.user

    fun setMode(mode: Mode) = _ui.update { it.copy(mode = mode, error = null) }

    fun setName(name: String) = _ui.update { it.copy(name = name, error = null) }

    fun setEmail(email: String) = _ui.update { it.copy(email = email, error = null) }

    fun setPassword(password: String) = _ui.update { it.copy(password = password, error = null) }

    fun togglePasswordVisibility() = _ui.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun submit() {
        val state = _ui.value
        if (state.loading) return

        val validationError = validate(state)
        if (validationError != null) {
            _ui.update { it.copy(error = validationError) }
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            val result = if (state.mode == Mode.LOGIN) {
                authRepository.signIn(state.email, state.password)
            } else {
                authRepository.register(state.name, state.email, state.password)
            }
            _ui.update { current ->
                current.copy(loading = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun startGoogleSignIn() = _ui.update {
        if (it.googleLoading) it else it.copy(googleLoading = true, error = null)
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _ui.update { it.copy(googleLoading = true, error = null) }
            val result = authRepository.signInWithGoogle(idToken)
            _ui.update {
                it.copy(
                    googleLoading = false,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun onGoogleError(message: String) = _ui.update {
        it.copy(googleLoading = false, error = message)
    }

    private fun validate(state: UiState): String? = when {
        state.mode == Mode.REGISTER && state.name.isBlank() -> "আপনার নাম লিখুন"
        !state.email.trim().matches(EMAIL_PATTERN) -> "সঠিক ইমেইল লিখুন"
        state.password.length < 6 -> "পাসওয়ার্ড অন্তত ৬ অক্ষরের হতে হবে"
        else -> null
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

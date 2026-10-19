package com.hafeziquran.app.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/** The signed-in account as the UI needs it; null means guest mode. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
)

/**
 * Optional authentication. The app never requires a session: callers read
 * [user] only to show profile/logout affordances, and every feature (reader,
 * audio, Sobok, videos) works when it is null.
 */
@Singleton
class AuthRepository @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _user = MutableStateFlow(auth.currentUser?.toAuthUser())
    val user: StateFlow<AuthUser?> = _user.asStateFlow()

    init {
        // Fires immediately with the restored session (if any), then on every
        // sign-in/sign-out — including token refresh events.
        auth.addAuthStateListener { _user.value = it.currentUser?.toAuthUser() }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runAuth {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun register(name: String, email: String, password: String): Result<Unit> = runAuth {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val displayName = name.trim()
        if (displayName.isNotEmpty()) {
            val request = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            result.user?.updateProfile(request)?.await()
        }
    }

    /** [idToken] comes from the Google Sign-In activity result. */
    suspend fun signInWithGoogle(idToken: String): Result<Unit> = runAuth {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    fun signOut() = auth.signOut()

    private suspend fun runAuth(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (exception: Exception) {
        Result.failure(IllegalStateException(exception.toBanglaMessage(), exception))
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
    )
}

/**
 * Firebase auth errors are English developer strings; the screen is Bangla.
 * Codes the UI cannot act on fall back to a generic message.
 */
private fun Exception.toBanglaMessage(): String = when (this) {
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_INVALID_EMAIL" -> "ইমেইলটি সঠিক নয়"
        "ERROR_WRONG_PASSWORD",
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_INVALID_LOGIN_CREDENTIALS" -> "ইমেইল বা পাসওয়ার্ড ভুল"
        "ERROR_USER_NOT_FOUND" -> "এই ইমেইলে কোনো অ্যাকাউন্ট নেই"
        "ERROR_EMAIL_ALREADY_IN_USE" -> "এই ইমেইল দিয়ে ইতিমধ্যে অ্যাকাউন্ট আছে"
        "ERROR_WEAK_PASSWORD" -> "পাসওয়ার্ড অন্তত ৬ অক্ষরের হতে হবে"
        "ERROR_NETWORK_REQUEST_FAILED" -> "ইন্টারনেট সংযোগ পাওয়া যায়নি"
        "ERROR_TOO_MANY_REQUESTS" -> "অনেকবার চেষ্টা হয়েছে, কিছুক্ষণ পরে আবার চেষ্টা করুন"
        "ERROR_OPERATION_NOT_ALLOWED" -> "Firebase-এ Email/Password লগইন চালু করা নেই"
        else -> "লগইন করা যায়নি (${errorCode.ifBlank { "অজানা ত্রুটি" }})"
    }
    is UnknownHostException -> "ইন্টারনেট সংযোগ নেই"
    else -> message?.takeIf { it.isNotBlank() } ?: "লগইন ব্যর্থ হয়েছে"
}

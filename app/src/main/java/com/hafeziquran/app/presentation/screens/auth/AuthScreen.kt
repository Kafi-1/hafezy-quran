package com.hafeziquran.app.presentation.screens.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.hafeziquran.app.R
import com.hafeziquran.app.presentation.screens.auth.AuthViewModel.Mode

/**
 * Optional sign-in. Guests are first-class citizens: the back button and the
 * "অতিথি হিসেবে চালিয়ে যান" link both return to Settings without a session, and
 * nothing in the app is gated behind this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // As soon as Firebase reports a session, leave this screen.
    LaunchedEffect(user) {
        if (user != null) onSignedIn()
    }

    val googleClient = remember(context) {
        val webClientId = context.getString(R.string.default_web_client_id)
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build(),
        )
    }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK && result.data == null) {
            viewModel.onGoogleError("Google সাইন-ইন বাতিল হয়েছে")
            return@rememberLauncherForActivityResult
        }
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken.isNullOrBlank()) {
                viewModel.onGoogleError("Google থেকে আইডি টোকেন পাওয়া যায়নি")
            } else {
                viewModel.signInWithGoogle(idToken)
            }
        } catch (exception: ApiException) {
            viewModel.onGoogleError(
                if (exception.statusCode == CommonStatusCodes.SIGN_IN_CANCELLED) {
                    "Google সাইন-ইন বাতিল হয়েছে"
                } else {
                    "Google সাইন-ইন ব্যর্থ হয়েছে (কোড: ${exception.statusCode})"
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "লগইন",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (state.mode == Mode.LOGIN) "আপনার অ্যাকাউন্টে লগইন করুন" else "নতুন অ্যাকাউন্ট খুলুন",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "লগইন সম্পূর্ণ ঐচ্ছিক। অতিথি হিসেবেও কোরআন, অডিও, সবক ও ভিডিও — সব ফিচার ব্যবহার করতে পারবেন।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == Mode.LOGIN,
                    onClick = { viewModel.setMode(Mode.LOGIN) },
                    label = { Text("লগইন") },
                )
                FilterChip(
                    selected = state.mode == Mode.REGISTER,
                    onClick = { viewModel.setMode(Mode.REGISTER) },
                    label = { Text("নতুন অ্যাকাউন্ট") },
                )
            }

            Spacer(Modifier.height(16.dp))

            if (state.mode == Mode.REGISTER) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("আপনার নাম") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::setEmail,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ইমেইল") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("পাসওয়ার্ড") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (state.passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    IconButton(onClick = viewModel::togglePasswordVisibility) {
                        Icon(
                            imageVector = if (state.passwordVisible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (state.passwordVisible) {
                                "পাসওয়ার্ড লুকান"
                            } else {
                                "পাসওয়ার্ড দেখান"
                            },
                        )
                    }
                },
            )

            state.error?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = viewModel::submit,
                enabled = !state.loading && !state.googleLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = if (state.mode == Mode.LOGIN) "লগইন করুন" else "অ্যাকাউন্ট খুলুন",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "অথবা",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(18.dp))

            OutlinedButton(
                onClick = {
                    viewModel.startGoogleSignIn()
                    googleLauncher.launch(googleClient.signInIntent)
                },
                enabled = !state.loading && !state.googleLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (state.googleLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Google দিয়ে চালিয়ে যান")
                }
            }

            Spacer(Modifier.height(20.dp))

            TextButton(onClick = onBack) {
                Text("অতিথি হিসেবে চালিয়ে যান")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

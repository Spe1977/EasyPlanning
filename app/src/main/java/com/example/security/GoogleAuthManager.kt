package com.example.security

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom
import android.util.Base64
import kotlinx.coroutines.withTimeoutOrNull

sealed class GoogleSignInOutcome {
    data class Success(val email: String, val displayName: String?) : GoogleSignInOutcome()
    data object Cancelled : GoogleSignInOutcome()
    data class Error(val reason: String) : GoogleSignInOutcome()
}

/**
 * Authenticates the user with their Google Account locally on-device using Jetpack
 * Credential Manager (GetSignInWithGoogleOption) without any central app backend.
 */
object GoogleAuthManager {

    suspend fun signInWithGoogle(activityContext: Context): GoogleSignInOutcome {
        val serverClientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID
        if (serverClientId.isBlank()) {
            return GoogleSignInOutcome.Error("OAuth client not configured")
        }

        val nonceBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = Base64.encodeToString(nonceBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        val googleOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(nonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        val credentialManager = CredentialManager.create(activityContext)
        return try {
            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleId = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInOutcome.Success(
                    email = googleId.id,
                    displayName = googleId.displayName ?: googleId.givenName
                )
            } else {
                GoogleSignInOutcome.Error("Unexpected credential type")
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInOutcome.Cancelled
        } catch (_: NoCredentialException) {
            GoogleSignInOutcome.Error("No Google account available")
        } catch (e: Exception) {
            GoogleSignInOutcome.Error(e.localizedMessage ?: "Google Sign-In failed")
        }
    }

    suspend fun clearCredentialState(context: Context) {
        runCatching {
            withTimeoutOrNull(2_000L) {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }
}

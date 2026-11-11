package com.ivy.rivy.sync

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import javax.inject.Inject

class GoogleSignInException(message: String) : Exception(message)

/** Asks the owner to pick a Google account and returns its ID token. */
class GoogleSignIn @Inject constructor() {

    /**
     * @param activityContext must be an Activity, the account picker is shown on top of it
     * @param serverClientId the "Web application" OAuth client ID the server checks tokens against
     */
    suspend fun idToken(activityContext: Context, serverClientId: String): String {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()

        val activity = findActivity(activityContext)
        val credential = try {
            CredentialManager.create(activity)
                .getCredential(activity, request)
                .credential
        } catch (e: GetCredentialCancellationException) {
            throw GoogleSignInException("Masuk dengan Google dibatalkan.")
        } catch (e: GetCredentialException) {
            throw GoogleSignInException("Masuk dengan Google gagal: ${e.message ?: e.type}")
        }

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw GoogleSignInException("Google tidak mengembalikan token masuk.")
    }

    private fun findActivity(context: Context): Context {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return context
    }
}

package com.khotwa.ai

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** تسجيل الدخول بحساب Google عبر Credential Manager. */
object GoogleAuth {

    class Cancelled : Exception()
    class NoAccount : Exception()

    val configured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** يعيد (الاسم، البريد). */
    suspend fun signIn(activity: Activity): Result<Pair<String, String>> {
        return try {
            val cm = CredentialManager.create(activity)
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val cred = cm.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val g = GoogleIdTokenCredential.createFrom(cred.data)
                val email = g.id
                Result.success(Pair(g.displayName ?: email.substringBefore('@'), email))
            } else {
                Result.failure(IllegalStateException("unsupported credential"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Cancelled())
        } catch (e: NoCredentialException) {
            Result.failure(NoAccount())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(activity: Activity) {
        try {
            CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
        }
    }
}

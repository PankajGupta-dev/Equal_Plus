package com.example.equal_plus.data.auth

import android.app.Activity
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationAPIClient
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.result.Credentials
import com.example.equal_plus.data.local.AuthDataStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Repository wrapping Auth0 Passwordless SMS (Scribe OTP) flow.
 *
 * Auth0 credentials come exclusively from res/values/auth0.xml — never
 * hardcoded in Kotlin.
 *
 * Flow:
 *   1. [startPasswordlessSms] → triggers Auth0 to send the OTP via SMS
 *   2. [loginWithOtp]         → exchanges the OTP for an access token
 *   3. On success, persists is_verified + token to [AuthDataStore]
 */
class AuthRepository(
    private val auth0: Auth0,
    private val authDataStore: AuthDataStore
) {

    private val apiClient: AuthenticationAPIClient = AuthenticationAPIClient(auth0)

    /**
     * Step 1 – request an OTP SMS to [phoneNumber] (E.164 format, e.g. "+14155552671").
     * Suspends until Auth0 acknowledges the request or throws [AuthenticationException].
     */
    suspend fun startPasswordlessSms(phoneNumber: String): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            apiClient.passwordlessWithSMS(phoneNumber, com.auth0.android.authentication.PasswordlessType.CODE)
                .start(object : Callback<Void?, AuthenticationException> {
                    override fun onSuccess(result: Void?) {
                        cont.resume(Result.success(Unit))
                    }
                    override fun onFailure(error: AuthenticationException) {
                        cont.resume(Result.failure(error))
                    }
                })
            cont.invokeOnCancellation { /* nothing to cancel at SDK level */ }
        }

    /**
     * Step 2 – exchange OTP [code] for credentials. On success persists is_verified.
     * Returns the raw access token string on success.
     */
    suspend fun loginWithOtp(phoneNumber: String, code: String): Result<String> =
        suspendCancellableCoroutine { cont ->
            apiClient.loginWithPhoneNumber(phoneNumber, code, "sms")
                .start(object : Callback<Credentials, AuthenticationException> {
                    override fun onSuccess(result: Credentials) {
                        cont.resume(Result.success(result.accessToken))
                    }
                    override fun onFailure(error: AuthenticationException) {
                        cont.resume(Result.failure(error))
                    }
                })
            cont.invokeOnCancellation { /* nothing to cancel at SDK level */ }
        }

    /** Persist verification state — called after [loginWithOtp] succeeds. */
    suspend fun markVerified(phoneNumber: String, accessToken: String) {
        authDataStore.setVerified(phoneNumber, accessToken)
    }

    /** Returns a cold Flow of the is_verified flag for the app to observe on startup. */
    fun isVerifiedFlow() = authDataStore.isVerified

    /** Clear the verification state (logout). */
    suspend fun clearVerification() = authDataStore.clearVerification()
}

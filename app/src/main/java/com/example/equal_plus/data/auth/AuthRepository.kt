package com.example.equal_plus.data.auth

import android.util.Log
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationAPIClient
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.authentication.PasswordlessType
import com.auth0.android.callback.Callback
import com.auth0.android.result.Credentials
import com.example.equal_plus.data.local.AuthDataStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "AuthRepository"

/**
 * Repository wrapping Auth0 Passwordless SMS OTP flow.
 *
 * Auth0 credentials come exclusively from res/values/auth0.xml — never hardcoded in Kotlin.
 *
 * Pre-requisites on Auth0 Dashboard (configured by user):
 *   ✅ Authentication → Passwordless → SMS → ON (Twilio credentials added)
 *   ✅ Applications → your app → Connections → sms → ON
 *   ✅ Applications → your app → Advanced Settings → Grant Types → Passwordless OTP ✅
 *
 * Flow:
 *   1. [startPasswordlessSms] → Auth0 calls Twilio → SMS delivered
 *   2. [loginWithOtp]         → exchanges OTP for Auth0 access token
 *   3. [markVerified]         → persists is_verified + token to AuthDataStore
 */
class AuthRepository(
    private val auth0: Auth0,
    private val authDataStore: AuthDataStore
) {

    private val apiClient: AuthenticationAPIClient = AuthenticationAPIClient(auth0)
    
    // Set to true to bypass SMS delivery and OTP verification requirements
    private val bypassOtp: Boolean = true

    /**
     * Step 1 — Request OTP SMS via Auth0 Passwordless (routes through "sms" connection → Twilio).
     */
    suspend fun startPasswordlessSms(phoneNumber: String): Result<Unit> {
        if (bypassOtp) {
            Log.d(TAG, "Bypassing Auth0 /passwordless/start for $phoneNumber")
            return Result.success(Unit)
        }
        return suspendCancellableCoroutine { cont ->
            Log.d(TAG, "Calling Auth0 /passwordless/start for $phoneNumber")
            apiClient
                .passwordlessWithSMS(phoneNumber, PasswordlessType.CODE)
                .addParameter("connection", "sms") // explicitly target the Twilio SMS connection
                .start(object : Callback<Void?, AuthenticationException> {
                    override fun onSuccess(result: Void?) {
                        Log.d(TAG, "Auth0 /passwordless/start succeeded")
                        cont.resume(Result.success(Unit))
                    }
                    override fun onFailure(error: AuthenticationException) {
                        Log.e(TAG, "Auth0 /passwordless/start failed — " +
                                "code=${error.getCode()} " +
                                "description=${error.getDescription()}", error)
                        cont.resume(Result.failure(Exception(buildReadableError(error))))
                    }
                })
            cont.invokeOnCancellation { /* SDK callbacks cannot be cancelled */ }
        }
    }

    /**
     * Step 2 — Exchange OTP code for Auth0 credentials.
     * Connection "sms" must match the connection used in step 1.
     */
    suspend fun loginWithOtp(phoneNumber: String, code: String): Result<String> {
        if (bypassOtp) {
            Log.d(TAG, "Bypassing Auth0 OTP login for $phoneNumber")
            return Result.success("bypassed_access_token")
        }
        return suspendCancellableCoroutine { cont ->
            Log.d(TAG, "Calling Auth0 /oauth/token (passwordless) for $phoneNumber")
            apiClient
                .loginWithPhoneNumber(phoneNumber, code, "sms")
                .start(object : Callback<Credentials, AuthenticationException> {
                    override fun onSuccess(result: Credentials) {
                        Log.d(TAG, "Auth0 OTP login succeeded")
                        cont.resume(Result.success(result.accessToken))
                    }
                    override fun onFailure(error: AuthenticationException) {
                        Log.e(TAG, "Auth0 OTP login failed — " +
                                "code=${error.getCode()} " +
                                "description=${error.getDescription()}", error)
                        cont.resume(Result.failure(Exception(buildReadableError(error))))
                    }
                })
            cont.invokeOnCancellation { /* SDK callbacks cannot be cancelled */ }
        }
    }

    /** Persist verification state after successful OTP login. */
    suspend fun markVerified(phoneNumber: String, accessToken: String) {
        authDataStore.setVerified(phoneNumber, accessToken)
    }

    fun isVerifiedFlow() = authDataStore.isVerified

    suspend fun clearVerification() = authDataStore.clearVerification()

    /**
     * Translate Auth0 error codes into readable user-facing messages.
     */
    private fun buildReadableError(error: AuthenticationException): String {
        return when (error.getCode()) {
            "bad.connection"   -> "SMS connection not enabled. Check Auth0 dashboard → Connections."
            "too_many_requests"-> "Too many attempts. Please wait a minute and try again."
            "bad.phone_number" -> "Invalid phone number format. Use +91XXXXXXXXXX."
            "unauthorized"     -> "Unauthorized. Verify Client ID and Grant Types in Auth0."
            "invalid_grant"    -> "Incorrect or expired OTP code. Please try again."
            else               -> error.getDescription()?.takeIf { it.isNotBlank() }
                ?: error.message
                ?: "An unexpected error occurred."
        }
    }
}

package com.example.equal_plus.telephony

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

/**
 * Custom InCallService to maintain background control over screened calls.
 * Ensures the call remains active in background audio mode without launching
 * system dialer UI until the AI screening engine resolves or escalates the call.
 */
class EqualInCallService : InCallService() {

    companion object {
        private const val TAG = "EqualInCallService"
        var activeCall: Call? = null
            private set
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCall = call
        Log.i(TAG, "InCallService call added: ${call.details?.handle}")

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                Log.d(TAG, "Call state changed: $state")
                if (state == Call.STATE_DISCONNECTED) {
                    activeCall = null
                }
            }
        })
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (activeCall == call) {
            activeCall = null
        }
        Log.i(TAG, "InCallService call removed")
    }

    fun endActiveCall() {
        activeCall?.disconnect()
    }
}

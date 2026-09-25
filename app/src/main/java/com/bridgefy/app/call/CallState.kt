package com.bridgefy.app.call

/**
 * Voice call state machine.
 *
 * State transitions:
 * ```
 * IDLE → CALLING        (caller taps call button)
 * IDLE → RINGING        (incoming CALL_REQUEST received)
 * CALLING → CONNECTED   (CALL_ACCEPT received from remote)
 * CALLING → ENDED       (CALL_REJECT received, or timeout)
 * RINGING → CONNECTED   (local user accepts call)
 * RINGING → ENDED       (local user rejects call)
 * CONNECTED → ENDED     (either party ends call, or connection lost)
 * ENDED → IDLE          (UI dismissed, ready for next call)
 * ```
 */
enum class CallState {
    /** No active call. Ready to initiate or receive. */
    IDLE,
    /** Outgoing call initiated. Waiting for remote to accept/reject. */
    CALLING,
    /** Incoming call received. Showing accept/reject dialog to user. */
    RINGING,
    /** Call connected. Audio streaming active in both directions. */
    CONNECTED,
    /** Call ended. Transitional state before returning to IDLE. */
    ENDED
}

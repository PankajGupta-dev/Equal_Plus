package com.example.equal_plus.callscreening

import com.example.equal_plus.data.local.entity.KnownContactEntity

/**
 * Screening decision emitted when inspecting an incoming call against known contacts.
 */
sealed class CallDecision {
    /**
     * Incoming number matches an existing entry in the verified `known_contacts` database.
     * The call is allowed to ring directly to the user without interception.
     */
    data class KnownCaller(val contact: KnownContactEntity) : CallDecision()

    /**
     * Incoming number was not found in `known_contacts`.
     * The call is silenced/intercepted and handed off to the AI screening assistant.
     */
    data class UnknownCaller(val rawNumber: String) : CallDecision()
}

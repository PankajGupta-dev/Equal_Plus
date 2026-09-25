package com.example.equal_plus.onboarding

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.Log
import com.example.equal_plus.data.local.dao.KnownContactDao
import com.example.equal_plus.data.local.entity.KnownContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Helper to query device contacts via ContactsContract, normalize phone numbers
 * to E.164, and sync them into the Room [KnownContactDao] table.
 */
class ContactSyncHelper(
    private val context: Context,
    private val knownContactDao: KnownContactDao
) {
    companion object {
        private const val TAG = "ContactSyncHelper"
    }

    /**
     * Reads contacts with phone numbers from [ContactsContract.CommonDataKinds.Phone],
     * normalizes each number to E.164 format, and inserts them into Room.
     *
     * @return Number of contacts successfully synced.
     */
    suspend fun syncContacts(): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val countryIso = Locale.getDefault().country.ifEmpty { "US" }
        val contactsMap = mutableMapOf<String, KnownContactEntity>()
        val currentTime = System.currentTimeMillis()

        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.let {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val rawName = if (nameIndex >= 0) it.getString(nameIndex) ?: "Unknown" else "Unknown"
                    val rawNumber = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""

                    if (rawNumber.isNotBlank()) {
                        val normalizedNumber = normalizeToE164(rawNumber, countryIso)
                        if (normalizedNumber.isNotBlank() && !contactsMap.containsKey(normalizedNumber)) {
                            contactsMap[normalizedNumber] = KnownContactEntity(
                                name = rawName.trim(),
                                number = normalizedNumber,
                                syncedAt = currentTime
                            )
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing READ_CONTACTS permission while syncing contacts", e)
            return@withContext 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query contacts", e)
            return@withContext 0
        } finally {
            cursor?.close()
        }

        if (contactsMap.isNotEmpty()) {
            knownContactDao.insertContacts(contactsMap.values.toList())
            Log.d(TAG, "Synced ${contactsMap.size} unique contacts into known_contacts table")
        }

        contactsMap.size
    }

    /**
     * Normalizes a phone number to standard E.164.
     * Uses [PhoneNumberUtils.formatNumberToE164] if possible,
     * falling back to stripping non-digits and ensuring standard format.
     */
    fun normalizeToE164(rawNumber: String, countryIso: String): String {
        val e164 = PhoneNumberUtils.formatNumberToE164(rawNumber, countryIso)
        if (!e164.isNullOrBlank()) {
            return e164
        }

        // Fallback: strip punctuation/spaces
        val cleaned = rawNumber.replace(Regex("[^0-9+]"), "")
        return if (cleaned.startsWith("+")) {
            cleaned
        } else if (cleaned.length == 10 && countryIso.equals("US", ignoreCase = true)) {
            "+1$cleaned"
        } else if (cleaned.length == 10 && countryIso.equals("IN", ignoreCase = true)) {
            "+91$cleaned"
        } else {
            cleaned
        }
    }
}

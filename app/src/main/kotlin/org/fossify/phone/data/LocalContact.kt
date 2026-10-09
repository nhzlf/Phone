package org.fossify.phone.data

import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact

data class LocalContact(
    val id: Long = 0L,
    val displayName: String,
    val phoneNumber: String,
    val phoneNormalized: String = phoneNumber.filter { it.isDigit() || it == '+' },
    val pinyin: String = "",
    val initials: String = "",
    val remoteId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
) {
    fun toCommonsContact(): Contact {
        val safeId = id.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        val phone = PhoneNumber(
            value = phoneNumber,
            type = 0,
            label = "",
            normalizedNumber = phoneNormalized.ifBlank { phoneNumber },
            isPrimary = true
        )
        return Contact(
            id = safeId,
            firstName = displayName,
            photoUri = "",
            phoneNumbers = arrayListOf(phone),
            contactId = safeId,
            source = SOURCE_LOCAL_DB
        )
    }

    companion object {
        const val SOURCE_LOCAL_DB = "local_db"
    }
}

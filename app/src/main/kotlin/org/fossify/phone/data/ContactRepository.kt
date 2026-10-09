package org.fossify.phone.data

import android.content.Context
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.getDisplayName
import org.fossify.phone.helpers.ContactInitialsHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Local SQLite contacts. Search and dial use this DB only, not the system ContactsProvider.
 * The system phone book is read once to seed the local DB when it is empty.
 */
class ContactRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabaseHelper.getInstance(appContext)
    private val io = Executors.newSingleThreadExecutor()

    fun getAllContacts(): List<LocalContact> = db.getAllActive()

    fun getAllAsCommonsContacts(): ArrayList<Contact> {
        return ArrayList(getAllContacts().map { it.toCommonsContact() })
    }

    fun search(query: String): List<LocalContact> = db.searchByQuery(query)

    fun count(): Int = db.countActive()

    fun replaceAll(contacts: List<LocalContact>) {
        db.replaceAll(contacts)
    }

    fun upsertAll(contacts: List<LocalContact>) {
        db.upsertAll(contacts)
    }

    fun clearAll() {
        db.clearAll()
        appContext.config.localContactsSeeded = false
    }

    fun loadForApp(callback: (List<LocalContact>) -> Unit) {
        io.execute {
            ensureSeededFromSystemIfNeeded()
            callback(getAllContacts())
        }
    }

    fun importFromSystem(callback: (Int) -> Unit) {
        io.execute {
            val count = seedFromSystemContacts()
            callback(count)
        }
    }

    fun ensureSeededFromSystemIfNeeded() {
        if (appContext.config.localContactsSeeded && count() > 0) {
            return
        }
        if (count() > 0) {
            appContext.config.localContactsSeeded = true
            return
        }
        seedFromSystemContacts()
    }

    fun seedFromSystemContacts(): Int {
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<ArrayList<Contact>>(1)

        ContactsHelper(appContext).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contacts ->
            holder[0] = contacts
            latch.countDown()
        }
        latch.await(20, TimeUnit.SECONDS)

        val systemContacts = holder[0] ?: arrayListOf()
        val privateCursor = appContext.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        if (SMT_PRIVATE !in appContext.config.ignoredContactSources) {
            val privateContacts = MyContactsContentProvider.getContacts(appContext, privateCursor)
            systemContacts.addAll(privateContacts)
        }

        val imported = ArrayList<LocalContact>()
        val seenNumbers = HashSet<String>()
        systemContacts.forEach { contact ->
            val displayName = contact.getDisplayName().ifBlank { contact.getNameToDisplay() }
            contact.phoneNumbers.forEach { phone ->
                val number = phone.value.ifBlank { phone.normalizedNumber }
                val normalized = number.filter { it.isDigit() || it == '+' }
                if (number.isBlank() || normalized in seenNumbers) {
                    return@forEach
                }
                seenNumbers.add(normalized)
                imported.add(
                    LocalContact(
                        displayName = displayName,
                        phoneNumber = number,
                        phoneNormalized = normalized,
                        pinyin = ContactInitialsHelper.getFullPinyin(displayName),
                        initials = ContactInitialsHelper.getInitials(displayName),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        db.replaceAll(imported)
        appContext.config.localContactsSeeded = true
        return imported.size
    }

    companion object {
        @Volatile
        private var instance: ContactRepository? = null

        fun getInstance(context: Context): ContactRepository {
            return instance ?: synchronized(this) {
                instance ?: ContactRepository(context).also { instance = it }
            }
        }
    }
}

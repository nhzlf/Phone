package org.fossify.phone.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_CONTACTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DISPLAY_NAME TEXT NOT NULL,
                $COL_PHONE_NUMBER TEXT NOT NULL,
                $COL_PHONE_NORMALIZED TEXT NOT NULL,
                $COL_PINYIN TEXT NOT NULL DEFAULT '',
                $COL_INITIALS TEXT NOT NULL DEFAULT '',
                $COL_REMOTE_ID TEXT,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_DELETED INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_contacts_initials ON $TABLE_CONTACTS($COL_INITIALS)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_contacts_pinyin ON $TABLE_CONTACTS($COL_PINYIN)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_contacts_phone ON $TABLE_CONTACTS($COL_PHONE_NORMALIZED)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future migrations
    }

    fun getAllActive(): List<LocalContact> {
        readableDatabase.query(
            TABLE_CONTACTS,
            null,
            "$COL_DELETED=0",
            null,
            null,
            null,
            "$COL_INITIALS COLLATE NOCASE ASC, $COL_DISPLAY_NAME COLLATE NOCASE ASC"
        ).use { cursor ->
            val result = ArrayList<LocalContact>(cursor.count)
            while (cursor.moveToNext()) {
                result.add(cursorToContact(cursor))
            }
            return result
        }
    }

    fun searchByQuery(query: String): List<LocalContact> {
        val q = query.lowercase().filter { it in 'a'..'z' }
        if (q.isEmpty()) {
            return emptyList()
        }
        val likeContains = "%$q%"
        readableDatabase.query(
            TABLE_CONTACTS,
            null,
            """
            $COL_DELETED=0 AND (
                $COL_INITIALS LIKE ? OR
                $COL_PINYIN LIKE ? OR
                lower($COL_DISPLAY_NAME) LIKE ?
            )
            """.trimIndent(),
            arrayOf(likeContains, likeContains, likeContains),
            null,
            null,
            "$COL_INITIALS COLLATE NOCASE ASC"
        ).use { cursor ->
            val result = ArrayList<LocalContact>(cursor.count)
            while (cursor.moveToNext()) {
                result.add(cursorToContact(cursor))
            }
            return result
        }
    }

    fun countActive(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_CONTACTS WHERE $COL_DELETED=0",
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun replaceAll(contacts: List<LocalContact>) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(TABLE_CONTACTS, null, null)
            contacts.forEach { insert(it, writableDatabase) }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun upsertAll(contacts: List<LocalContact>) {
        writableDatabase.beginTransaction()
        try {
            contacts.forEach { contact ->
                val existingId = findIdByPhone(contact.phoneNormalized)
                if (existingId != null) {
                    update(contact.copy(id = existingId), writableDatabase)
                } else {
                    insert(contact, writableDatabase)
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun clearAll() {
        writableDatabase.delete(TABLE_CONTACTS, null, null)
    }

    private fun findIdByPhone(phoneNormalized: String): Long? {
        if (phoneNormalized.isBlank()) {
            return null
        }
        readableDatabase.query(
            TABLE_CONTACTS,
            arrayOf(COL_ID),
            "$COL_PHONE_NORMALIZED=? AND $COL_DELETED=0",
            arrayOf(phoneNormalized),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    private fun insert(contact: LocalContact, db: SQLiteDatabase = writableDatabase): Long {
        return db.insert(TABLE_CONTACTS, null, toValues(contact, includeId = false))
    }

    private fun update(contact: LocalContact, db: SQLiteDatabase = writableDatabase) {
        db.update(
            TABLE_CONTACTS,
            toValues(contact, includeId = false),
            "$COL_ID=?",
            arrayOf(contact.id.toString())
        )
    }

    private fun toValues(contact: LocalContact, includeId: Boolean): ContentValues {
        return ContentValues().apply {
            if (includeId && contact.id > 0) {
                put(COL_ID, contact.id)
            }
            put(COL_DISPLAY_NAME, contact.displayName)
            put(COL_PHONE_NUMBER, contact.phoneNumber)
            put(COL_PHONE_NORMALIZED, contact.phoneNormalized)
            put(COL_PINYIN, contact.pinyin)
            put(COL_INITIALS, contact.initials)
            put(COL_REMOTE_ID, contact.remoteId)
            put(COL_UPDATED_AT, contact.updatedAt)
            put(COL_DELETED, if (contact.deleted) 1 else 0)
        }
    }

    private fun cursorToContact(cursor: android.database.Cursor): LocalContact {
        return LocalContact(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            displayName = cursor.getString(cursor.getColumnIndexOrThrow(COL_DISPLAY_NAME)),
            phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow(COL_PHONE_NUMBER)),
            phoneNormalized = cursor.getString(cursor.getColumnIndexOrThrow(COL_PHONE_NORMALIZED)),
            pinyin = cursor.getString(cursor.getColumnIndexOrThrow(COL_PINYIN)),
            initials = cursor.getString(cursor.getColumnIndexOrThrow(COL_INITIALS)),
            remoteId = cursor.getString(cursor.getColumnIndexOrThrow(COL_REMOTE_ID)),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED_AT)),
            deleted = cursor.getInt(cursor.getColumnIndexOrThrow(COL_DELETED)) == 1
        )
    }

    companion object {
        private const val DB_NAME = "phone_local.db"
        private const val DB_VERSION = 1

        const val TABLE_CONTACTS = "contacts"
        const val COL_ID = "id"
        const val COL_DISPLAY_NAME = "display_name"
        const val COL_PHONE_NUMBER = "phone_number"
        const val COL_PHONE_NORMALIZED = "phone_normalized"
        const val COL_PINYIN = "pinyin"
        const val COL_INITIALS = "initials"
        const val COL_REMOTE_ID = "remote_id"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_DELETED = "deleted"

        @Volatile
        private var instance: AppDatabaseHelper? = null

        fun getInstance(context: Context): AppDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: AppDatabaseHelper(context).also { instance = it }
            }
        }
    }
}

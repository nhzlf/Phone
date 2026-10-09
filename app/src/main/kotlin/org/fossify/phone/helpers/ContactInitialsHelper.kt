package org.fossify.phone.helpers

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.extensions.getDisplayName
import org.fossify.phone.models.RecentCall
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object ContactInitialsHelper {

    data class SearchEntry(
        val contact: Contact,
        /** Preferred initials from the visible display name. */
        val primaryInitials: String,
        /** All initials variants used for matching (display name / name only). */
        val initialsKeys: List<String>,
        /** Full pinyin of the display name (prefix match only). */
        val pinyin: String
    )

    /**
     * Sort rank for query "zlf":
     * 0 = ZLF* / ZLF** (initials start with query; shorter first)
     * 1 = *ZLF / **ZLF (initials contain query; earlier index first)
     * 2 = full pinyin starts with query
     */
    data class MatchRank(
        val tier: Int,
        val matchIndex: Int,
        val keyLength: Int
    ) : Comparable<MatchRank> {
        override fun compareTo(other: MatchRank): Int {
            return compareValuesBy(this, other, { it.tier }, { it.matchIndex }, { it.keyLength })
        }
    }

    private val pinyinFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
    }

    private val charPinyinCache = ConcurrentHashMap<Char, Array<String>>()

    fun buildSearchEntries(
        contacts: List<Contact>,
        recentCalls: List<RecentCall> = emptyList()
    ): List<SearchEntry> {
        val extraNamesByNumber = HashMap<String, MutableSet<String>>()
        recentCalls.forEach { recent ->
            if (recent.isUnknownNumber || recent.name.isBlank() || recent.name == recent.phoneNumber) {
                return@forEach
            }
            val numberKey = normalizeNumber(recent.phoneNumber)
            if (numberKey.isEmpty()) {
                return@forEach
            }
            extraNamesByNumber.getOrPut(numberKey) { linkedSetOf() }.add(recent.name.trim())
        }

        val entries = ArrayList<SearchEntry>(contacts.size + recentCalls.size)
        val indexedNumbers = HashSet<String>()

        contacts.forEach { contact ->
            val extraNames = linkedSetOf<String>()
            contact.getPrimaryNumber()?.let { number ->
                val key = normalizeNumber(number)
                indexedNumbers.add(key)
                extraNamesByNumber[key]?.let { extraNames.addAll(it) }
            }
            contact.phoneNumbers.forEach { phone ->
                val key = normalizeNumber(phone.normalizedNumber)
                indexedNumbers.add(key)
                extraNamesByNumber[key]?.let { extraNames.addAll(it) }
            }
            entries.add(buildEntry(contact, extraNames))
        }

        // Call-log-only names (e.g. 赵利访) that are not saved contacts.
        recentCalls.forEach { recent ->
            if (recent.isUnknownNumber || recent.name.isBlank() || recent.name == recent.phoneNumber) {
                return@forEach
            }
            val numberKey = normalizeNumber(recent.phoneNumber)
            if (numberKey.isEmpty() || numberKey in indexedNumbers) {
                return@forEach
            }
            indexedNumbers.add(numberKey)
            entries.add(buildEntry(syntheticContactFromRecent(recent)))
        }

        return entries
    }

    fun matchRank(entry: SearchEntry, query: String): MatchRank? {
        val q = normalizeQuery(query) ?: return null
        var best: MatchRank? = null

        entry.initialsKeys.forEach { initials ->
            when {
                initials.startsWith(q) -> {
                    best = minOfRank(best, MatchRank(tier = 0, matchIndex = 0, keyLength = initials.length))
                }

                initials.contains(q) -> {
                    best = minOfRank(
                        best,
                        MatchRank(tier = 1, matchIndex = initials.indexOf(q), keyLength = initials.length)
                    )
                }
            }
        }

        if (entry.pinyin.startsWith(q)) {
            best = minOfRank(best, MatchRank(tier = 2, matchIndex = 0, keyLength = entry.pinyin.length))
        }

        return best
    }

    fun matches(entry: SearchEntry, query: String): Boolean = matchRank(entry, query) != null

    fun getInitials(name: String): String {
        val result = StringBuilder()
        var expectingWordStart = true

        name.forEach { char ->
            val readings = readingsOf(char)
            when {
                readings.isNotEmpty() -> {
                    result.append(readings.first().first())
                    expectingWordStart = true
                }

                isAsciiLetter(char) -> {
                    if (expectingWordStart) {
                        result.append(char.lowercaseChar())
                        expectingWordStart = false
                    }
                }

                else -> expectingWordStart = true
            }
        }

        return result.toString()
    }

    fun getFullPinyin(name: String): String {
        val result = StringBuilder()
        name.forEach { char ->
            val readings = readingsOf(char)
            when {
                readings.isNotEmpty() -> result.append(readings.first())
                isAsciiLetter(char) || char.isDigit() -> result.append(char.lowercaseChar())
            }
        }
        return result.toString()
    }

    private fun buildEntry(
        contact: Contact,
        extraNames: Collection<String> = emptyList()
    ): SearchEntry {
        // Only index person-name fields. Nickname/company caused false hits like 易投 for "ZLF".
        val sourceNames = linkedSetOf<String>().apply {
            addIfNotBlank(contact.getDisplayName())
            addIfNotBlank(contact.firstName)
            addIfNotBlank(contact.surname)
            addIfNotBlank(contact.middleName)
            val joined = listOf(contact.surname, contact.firstName, contact.middleName)
                .filter { it.isNotBlank() }
                .joinToString("")
            addIfNotBlank(joined)
            extraNames.forEach { addIfNotBlank(it) }
        }

        val initialsKeys = linkedSetOf<String>()
        var primaryInitials = ""
        var pinyin = ""

        sourceNames.forEachIndexed { index, source ->
            val initials = getInitials(source)
            if (initials.isNotEmpty()) {
                initialsKeys.add(initials)
            }
            initialsKeys.addAll(getInitialVariants(source))
            if (index == 0) {
                primaryInitials = initials
                pinyin = getFullPinyin(source)
            }
        }

        if (primaryInitials.isEmpty()) {
            primaryInitials = initialsKeys.firstOrNull().orEmpty()
        }
        if (pinyin.isEmpty()) {
            pinyin = getFullPinyin(contact.getDisplayName())
        }

        return SearchEntry(
            contact = contact,
            primaryInitials = primaryInitials,
            initialsKeys = initialsKeys.toList(),
            pinyin = pinyin
        )
    }

    private fun syntheticContactFromRecent(recent: RecentCall): Contact {
        val phone = PhoneNumber(
            value = recent.phoneNumber,
            type = 0,
            label = "",
            normalizedNumber = recent.phoneNumber,
            isPrimary = true
        )
        return Contact(
            id = -1_000_000 - recent.id,
            firstName = recent.name,
            photoUri = recent.photoUri,
            phoneNumbers = arrayListOf(phone),
            contactId = -1_000_000 - recent.id,
            thumbnailUri = recent.photoUri,
            source = "call_log"
        )
    }

    private fun getInitialVariants(name: String): List<String> {
        val charOptions = ArrayList<List<Char>>()
        var expectingWordStart = true

        name.forEach { char ->
            val readings = readingsOf(char)
            when {
                readings.isNotEmpty() -> {
                    charOptions.add(readings.map { it.first() }.distinct())
                    expectingWordStart = true
                }

                isAsciiLetter(char) -> {
                    if (expectingWordStart) {
                        charOptions.add(listOf(char.lowercaseChar()))
                        expectingWordStart = false
                    }
                }

                else -> expectingWordStart = true
            }
        }

        if (charOptions.isEmpty() || charOptions.size > 6) {
            return emptyList()
        }
        if (charOptions.all { it.size == 1 }) {
            return emptyList()
        }

        val variants = linkedSetOf<String>()
        fun dfs(index: Int, current: StringBuilder) {
            if (variants.size >= 32) {
                return
            }
            if (index == charOptions.size) {
                variants.add(current.toString())
                return
            }
            charOptions[index].forEach { option ->
                current.append(option)
                dfs(index + 1, current)
                current.deleteCharAt(current.lastIndex)
            }
        }
        dfs(0, StringBuilder())
        return variants.toList()
    }

    private fun readingsOf(char: Char): Array<String> {
        return charPinyinCache.getOrPut(char) {
            try {
                val withFormat = PinyinHelper.toHanyuPinyinStringArray(char, pinyinFormat)
                if (!withFormat.isNullOrEmpty()) {
                    return@getOrPut withFormat.map { it.lowercase(Locale.US) }.distinct().toTypedArray()
                }
            } catch (_: BadHanyuPinyinOutputFormatCombination) {
            }

            // Fallback without format for uncommon CJK chars.
            PinyinHelper.toHanyuPinyinStringArray(char)
                ?.map { it.lowercase(Locale.US).replace(Regex("[^a-z]"), "") }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?.toTypedArray()
                ?: emptyArray()
        }
    }

    private fun normalizeQuery(query: String): String? {
        val normalized = query.lowercase(Locale.US).filter { isAsciiLetter(it) }
        return normalized.ifEmpty { null }
    }

    private fun normalizeNumber(number: String): String {
        return number.filter { it.isDigit() || it == '+' }
    }

    private fun isAsciiLetter(char: Char): Boolean {
        return char in 'a'..'z' || char in 'A'..'Z'
    }

    private fun minOfRank(current: MatchRank?, candidate: MatchRank): MatchRank {
        return if (current == null || candidate < current) candidate else current
    }

    private fun MutableSet<String>.addIfNotBlank(value: String?) {
        if (!value.isNullOrBlank()) {
            add(value.trim())
        }
    }
}

package org.fossify.phone.helpers

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination
import org.fossify.commons.models.contacts.Contact
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object ContactInitialsHelper {

    data class SearchEntry(
        val contact: Contact,
        val keys: List<String>
    )

    private val pinyinFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
    }

    private val charPinyinCache = ConcurrentHashMap<Char, Array<String>>()

    fun buildSearchEntries(contacts: List<Contact>): List<SearchEntry> {
        return contacts.map { contact ->
            val sourceNames = linkedSetOf<String>().apply {
                addIfNotBlank(contact.getNameToDisplay())
                addIfNotBlank(contact.name)
                addIfNotBlank(contact.firstName)
                addIfNotBlank(contact.surname)
                addIfNotBlank(contact.middleName)
                addIfNotBlank(contact.nickname)
                addIfNotBlank(contact.organization.company)
            }

            val keys = linkedSetOf<String>()
            sourceNames.forEach { source ->
                val initials = getInitials(source)
                val pinyin = getFullPinyin(source)
                val letters = source.lowercase(Locale.US).filter { it.isLetter() }

                if (initials.isNotEmpty()) {
                    keys.add(initials)
                }
                if (pinyin.isNotEmpty()) {
                    keys.add(pinyin)
                }
                if (letters.isNotEmpty()) {
                    keys.add(letters)
                }
                keys.addAll(getInitialVariants(source))
            }

            SearchEntry(contact = contact, keys = keys.toList())
        }
    }

    fun matches(entry: SearchEntry, query: String): Boolean {
        val normalizedQuery = query.lowercase(Locale.US).filter { it.isLetter() }
        if (normalizedQuery.isEmpty()) {
            return false
        }

        return entry.keys.any { key ->
            key.startsWith(normalizedQuery) || key.contains(normalizedQuery)
        }
    }

    fun sortKey(entry: SearchEntry): String {
        return entry.keys.firstOrNull().orEmpty()
    }

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

                char.isLetter() -> {
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
                char.isLetterOrDigit() -> result.append(char.lowercaseChar())
            }
        }
        return result.toString()
    }

    /**
     * Alternate initials for polyphones (e.g. 单 shan/dan). Limited to short names.
     */
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

                char.isLetter() -> {
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
        if (Character.UnicodeScript.of(char.code) != Character.UnicodeScript.HAN) {
            return emptyArray()
        }

        return charPinyinCache.getOrPut(char) {
            try {
                PinyinHelper.toHanyuPinyinStringArray(char, pinyinFormat)
                    ?.map { it.lowercase(Locale.US) }
                    ?.distinct()
                    ?.toTypedArray()
                    ?: emptyArray()
            } catch (_: BadHanyuPinyinOutputFormatCombination) {
                emptyArray()
            }
        }
    }

    private fun MutableSet<String>.addIfNotBlank(value: String?) {
        if (!value.isNullOrBlank()) {
            add(value.trim())
        }
    }
}

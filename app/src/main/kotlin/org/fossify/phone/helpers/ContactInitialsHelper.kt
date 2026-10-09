package org.fossify.phone.helpers

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination
import java.util.Locale

object ContactInitialsHelper {

    private val pinyinFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
    }

    fun getInitials(name: String): String {
        val result = StringBuilder()
        var expectingWordStart = true

        name.forEach { char ->
            val pinyin = toPinyin(char)
            when {
                pinyin != null -> {
                    if (pinyin.isNotEmpty()) {
                        result.append(pinyin.first())
                    }
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
            val pinyin = toPinyin(char)
            when {
                pinyin != null -> result.append(pinyin)
                char.isLetterOrDigit() -> result.append(char.lowercaseChar())
            }
        }
        return result.toString()
    }

    fun matchesInitials(name: String, query: String): Boolean {
        val normalizedQuery = query.lowercase(Locale.US).filter { it.isLetter() }
        if (normalizedQuery.isEmpty()) {
            return false
        }

        val initials = getInitials(name)
        val pinyin = getFullPinyin(name)
        return initials.startsWith(normalizedQuery) ||
            pinyin.startsWith(normalizedQuery) ||
            initials.contains(normalizedQuery)
    }

    private fun toPinyin(char: Char): String? {
        if (Character.UnicodeScript.of(char.toInt()) != Character.UnicodeScript.HAN) {
            return null
        }

        return try {
            PinyinHelper.toHanyuPinyinStringArray(char, pinyinFormat)?.firstOrNull()
        } catch (_: BadHanyuPinyinOutputFormatCombination) {
            null
        }
    }
}

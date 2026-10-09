package org.fossify.phone.helpers

import com.github.promeg.pinyinhelper.Pinyin
import java.util.Locale

object ContactInitialsHelper {

    fun getInitials(name: String): String {
        val result = StringBuilder()
        var expectingWordStart = true

        name.forEach { char ->
            when {
                Pinyin.isChinese(char) -> {
                    val pinyin = Pinyin.toPinyin(char)
                    if (pinyin.isNotEmpty()) {
                        result.append(pinyin.first().lowercaseChar())
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
            when {
                Pinyin.isChinese(char) -> {
                    result.append(Pinyin.toPinyin(char).lowercase(Locale.US))
                }

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
}

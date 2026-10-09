package org.fossify.phone.extensions

import org.fossify.commons.models.contacts.Contact

/**
 * Display name without Fossify's surname-first comma ("赵, 利访" -> "赵利访").
 */
fun Contact.getDisplayName(): String {
    val firstMiddle = "$firstName $middleName".trim()
    if (Contact.startWithSurname && surname.isNotBlank() && firstMiddle.isNotBlank()) {
        val given = firstMiddle.replace(" ", "")
        val isCjk = (surname + given).any {
            Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN
        }
        return if (isCjk) {
            buildString {
                if (prefix.isNotBlank()) {
                    append(prefix)
                }
                append(surname)
                append(given)
                if (suffix.isNotBlank()) {
                    append(suffix)
                }
            }
        } else {
            listOf(prefix, surname, firstMiddle, suffix)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }
    }

    return getNameToDisplay().withoutSurnameComma()
}

fun String.withoutSurnameComma(): String {
    // CJK: "赵, 利访" -> "赵利访"
    val cjkFixed = replace(Regex("([\\u4e00-\\u9fff])\\s*,\\s*([\\u4e00-\\u9fff])"), "$1$2")
    // Latin: "Smith, John" -> "Smith John"
    return cjkFixed.replace(Regex(",\\s*"), " ").trim().replace(Regex("\\s+"), " ")
}

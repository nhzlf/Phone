package org.fossify.phone.extensions

/**
 * 修改时间：2026-10-10 17:12:58（本机）
 * 修改原因：通话记录/搜索列表需脱敏显示号码，节省行宽并保护隐私。
 * 功能说明：仅保留数字后，格式化为「前3位 + ** + 后4位」；过短号码原样返回。
 * 例：13800138000 → 138**8000
 */
fun String.toMaskedPhoneDisplay(): String {
    val digits = filter { it.isDigit() }
    if (digits.length <= 7) {
        return if (digits.isNotEmpty()) digits else this
    }
    return digits.take(3) + "**" + digits.takeLast(4)
}

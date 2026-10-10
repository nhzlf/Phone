package org.fossify.phone.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.data.ContactRepository
import org.fossify.phone.data.LocalContact
import org.fossify.phone.databinding.DialogAddLocalContactBinding
import org.fossify.phone.helpers.ContactInitialsHelper

/**
 * 修改时间：2026-10-10 16:34:28（本机）
 * 修改原因：主界面联系人页被隐藏后，缺少向本地库录入测试数据的入口。
 * 功能说明：弹出姓名+号码表单，校验非空后 upsert 到 phone_local.db（不写系统通讯录），
 *           成功后通过 callback 通知界面刷新字母搜索索引。
 */
class AddLocalContactDialog(
    val activity: SimpleActivity,
    private val onSaved: (() -> Unit)? = null
) {
    init {
        val binding = DialogAddLocalContactBinding.inflate(activity.layoutInflater)

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.add_local_contact) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = binding.addLocalContactName.value.trim()
                        val phone = binding.addLocalContactPhone.value.trim()
                        when {
                            name.isEmpty() -> activity.toast(R.string.add_local_contact_name_required)
                            phone.isEmpty() -> activity.toast(R.string.add_local_contact_phone_required)
                            phone.filter { it.isDigit() || it == '+' }.isEmpty() -> {
                                activity.toast(R.string.add_local_contact_phone_invalid)
                            }
                            else -> {
                                ContactRepository.getInstance(activity).addContact(
                                    LocalContact(
                                        displayName = name,
                                        phoneNumber = phone,
                                        phoneNormalized = phone.filter { it.isDigit() || it == '+' },
                                        pinyin = ContactInitialsHelper.getFullPinyin(name),
                                        initials = ContactInitialsHelper.getInitials(name),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                ) {
                                    activity.runOnUiThread {
                                        activity.toast(R.string.add_local_contact_done)
                                        onSaved?.invoke()
                                        alertDialog.dismiss()
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
}

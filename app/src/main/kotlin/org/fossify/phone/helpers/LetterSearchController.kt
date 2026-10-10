package org.fossify.phone.helpers

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.ContactsAdapter
import org.fossify.phone.databinding.LayoutLetterSearchPanelBinding
import org.fossify.phone.extensions.callContactWithSimWithConfirmationCheck
import org.fossify.phone.extensions.getDisplayName
import org.fossify.phone.data.LocalContact
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.views.SwipeDismissLinearLayout
import org.fossify.phone.views.SwipeDownHideLayout
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.sign

class LetterSearchController(
    private val activity: SimpleActivity,
    private val panelBinding: LayoutLetterSearchPanelBinding
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val indexExecutor = Executors.newSingleThreadExecutor()
    private var letterQuery = StringBuilder()
    private var numberInput = StringBuilder()
    private var isNumberMode = false
    private var isPanelVisible = false
    private var searchIndex: List<ContactInitialsHelper.SearchEntry> = emptyList()
    private var currentResults: List<Contact> = emptyList()
    private var listAdapter: ContactsAdapter? = null
    private var filterGeneration = 0L
    private var systemImeMode = false
    /** Whether the app's 26-key / number pad is shown on the search page. */
    private var customKeyboardVisible = true

    val isVisible: Boolean
        get() = isPanelVisible

    val isUsingSystemIme: Boolean
        get() = systemImeMode

    init {
        setupKeyboard()
        setupSwipeToHide()
        setupRestoreKeyboardButton()
        updateKeyboardUi()
        updateQueryUi()
        renderResults(emptyList(), "")
    }

    fun toggle() {
        if (isPanelVisible) {
            hide()
        } else {
            show()
        }
    }

    fun show() {
        if (isPanelVisible) {
            return
        }

        isPanelVisible = true
        val panel = panelBinding.root
        panel.animate().cancel()
        panel.beVisible()
        panel.post {
            val width = panelWidth()
            panel.translationX = width
            panel.animate()
                .translationX(0f)
                .setDuration(220)
                .start()
        }
        systemImeMode = false
        customKeyboardVisible = true
        updateKeyboardUi()
        if (isNumberMode) {
            updateQueryUi()
            updateNumberModePlaceholder()
        } else {
            filterContacts()
        }
    }

    fun hide(animated: Boolean = true) {
        if (!isPanelVisible) {
            return
        }

        isPanelVisible = false
        systemImeMode = false
        customKeyboardVisible = true
        updateKeyboardUi()
        val panel = panelBinding.root
        panel.animate().cancel()
        if (!animated) {
            panel.translationX = 0f
            panel.beGone()
            return
        }

        val direction = if (panel.translationX == 0f) 1f else sign(panel.translationX)
        val target = panelWidth() * (if (direction == 0f) 1f else direction)
        panel.animate()
            .translationX(target)
            .setDuration(200)
            .withEndAction {
                panel.translationX = 0f
                panel.beGone()
            }
            .start()
    }

    /** Top yellow search bar focused: use system IME, hide app letter keyboard. */
    fun enterSystemImeMode() {
        if (!isPanelVisible) {
            return
        }
        systemImeMode = true
        if (isNumberMode) {
            isNumberMode = false
        }
        customKeyboardVisible = false
        updateKeyboardUi()
        updateQueryUi()
        filterContacts()
    }

    /**
     * Top search closed: leave custom keyboard hidden and keep the restore button
     * on the results page (user can tap it to bring the 26-key keyboard back).
     */
    fun exitSystemImeMode() {
        systemImeMode = false
        if (!isPanelVisible) {
            return
        }
        customKeyboardVisible = false
        updateKeyboardUi()
        updateQueryUi()
        filterContacts()
    }

    /** Query from system IME (top search box). Supports initials and Chinese name text. */
    fun setExternalQuery(text: String) {
        if (isNumberMode) {
            isNumberMode = false
            if (customKeyboardVisible) {
                updateKeyboardUi()
            }
        }
        letterQuery.setLength(0)
        letterQuery.append(text)
        updateQueryUi()
        filterContacts()
    }

    fun hideCustomKeyboard() {
        if (!isPanelVisible || !customKeyboardVisible) {
            return
        }
        customKeyboardVisible = false
        updateKeyboardUi()
    }

    fun showCustomKeyboard() {
        if (!isPanelVisible) {
            return
        }
        customKeyboardVisible = true
        systemImeMode = false
        activity.currentFocus?.clearFocus()
        activity.hideKeyboard()
        updateKeyboardUi()
        updateQueryUi()
        if (isNumberMode) {
            updateNumberModePlaceholder()
        } else {
            filterContacts()
        }
    }

    fun clearQuery() {
        if (isNumberMode) {
            numberInput.clear()
        } else {
            letterQuery.clear()
        }
        updateQueryUi()
        if (isNumberMode) {
            updateNumberModePlaceholder()
        } else {
            filterContacts()
        }
    }

    fun updateLocalContacts(contacts: List<LocalContact>) {
        indexExecutor.execute {
            val indexed = ContactInitialsHelper.buildSearchEntriesFromLocal(contacts)
            mainHandler.post {
                searchIndex = indexed
                if (!isNumberMode && isPanelVisible) {
                    filterContacts()
                }
            }
        }
    }

    fun refreshResults() {
        if (isPanelVisible && !isNumberMode) {
            filterContacts()
        }
    }

    private fun setupSwipeToHide() {
        val panel = panelBinding.root as? SwipeDismissLinearLayout ?: return
        panel.onSwipeDismiss = {
            hide()
        }

        val keyboardHost = panelBinding.letterKeyboardSwipeHost as? SwipeDownHideLayout
        keyboardHost?.onSwipeDownHide = {
            hideCustomKeyboard()
        }
    }

    private fun setupRestoreKeyboardButton() {
        val button = panelBinding.letterKeyboardRestoreButton
        button.setOnClickListener {
            showCustomKeyboard()
        }
        DraggableViewHelper.attach(
            view = button,
            store = DraggableViewHelper.PositionStore(
                loadX = { activity.config.keyboardRestorePosX },
                loadY = { activity.config.keyboardRestorePosY },
                save = { x, y ->
                    activity.config.keyboardRestorePosX = x
                    activity.config.keyboardRestorePosY = y
                }
            )
        )
    }

    private fun setupKeyboard() {
        val keyboard = panelBinding.letterKeyboardInclude
        bindLetterKeys(keyboard.letterKeysSection)
        bindDigitKeys(keyboard.numberKeysSection)

        val deleteOne = View.OnClickListener { deleteOneChar() }
        val clearAll = View.OnLongClickListener {
            clearQuery()
            true
        }

        listOf(
            keyboard.letterKeyDeleteLeft,
            keyboard.letterKeyBackspace,
            keyboard.numberKeyDeleteLeft,
            keyboard.numberKeyBackspace
        ).forEach { button ->
            button.setOnClickListener(deleteOne)
            button.setOnLongClickListener(clearAll)
        }

        keyboard.letterKeyModeToggle.setOnClickListener {
            isNumberMode = !isNumberMode
            updateKeyboardUi()
            updateQueryUi()
            if (isNumberMode) {
                updateNumberModePlaceholder()
            } else {
                filterContacts()
            }
        }

        keyboard.letterKeySim1.setOnClickListener {
            callWithSim(useMainSim = true)
        }
        keyboard.letterKeySim2.setOnClickListener {
            callWithSim(useMainSim = false)
        }
    }

    private fun updateKeyboardUi() {
        val keyboardHost = panelBinding.letterKeyboardSwipeHost
        val keyboard = panelBinding.letterKeyboardInclude
        val restoreButton = panelBinding.letterKeyboardRestoreButton

        if (!isPanelVisible) {
            keyboardHost.beGone()
            restoreButton.beGone()
            return
        }

        // Restore button sits on the results layer (under keyboard host).
        // Always show it while the custom keyboard is hidden — with or without system IME.
        restoreButton.beVisibleIf(!customKeyboardVisible)

        if (customKeyboardVisible) {
            keyboardHost.beVisible()
            keyboardHost.translationY = 0f
            keyboard.root.beVisible()
            keyboard.letterKeysSection.beVisibleIf(!isNumberMode)
            keyboard.numberKeysSection.beVisibleIf(isNumberMode)
            keyboard.letterKeyModeToggle.text = if (isNumberMode) {
                activity.getString(R.string.letter_key_mode_abc)
            } else {
                activity.getString(R.string.letter_key_mode_123)
            }
            panelBinding.letterSearchList.beVisibleIf(!isNumberMode && currentResults.isNotEmpty())
        } else {
            keyboardHost.beGone()
            panelBinding.letterSearchList.beVisibleIf(!isNumberMode && currentResults.isNotEmpty())
        }
    }

    private fun deleteOneChar() {
        val buffer = activeBuffer()
        if (buffer.isNotEmpty()) {
            buffer.deleteCharAt(buffer.lastIndex)
            updateQueryUi()
            if (isNumberMode) {
                updateNumberModePlaceholder()
            } else {
                filterContacts()
            }
        }
    }

    private fun callWithSim(useMainSim: Boolean) {
        if (isNumberMode) {
            // Number pad: dial the digits currently entered.
            val number = numberInput.toString().trim()
            if (number.isEmpty()) {
                activity.toast(R.string.letter_key_no_number_to_call)
                return
            }
            activity.callContactWithSimWithConfirmationCheck(
                recipient = number,
                name = number,
                useMainSIM = useMainSim
            )
            return
        }

        // Letter search: dial the first number in the result list.
        val contact = currentResults.firstOrNull()
        if (contact == null) {
            activity.toast(R.string.letter_key_no_result_to_call)
            return
        }

        val number = contact.getPrimaryNumber()
            ?: contact.phoneNumbers.firstOrNull()?.normalizedNumber
            ?: contact.phoneNumbers.firstOrNull()?.value
        if (number.isNullOrBlank()) {
            activity.toast(R.string.letter_key_no_result_to_call)
            return
        }

        activity.callContactWithSimWithConfirmationCheck(
            recipient = number,
            name = contact.getDisplayName(),
            useMainSIM = useMainSim
        )
    }

    private fun bindLetterKeys(root: ViewGroup) {
        traverseKeys(root) { view, label ->
            if (label.length == 1 && label[0].isLetter()) {
                view.setOnClickListener {
                    letterQuery.append(label[0].lowercaseChar())
                    updateQueryUi()
                    filterContacts()
                }
            }
        }
    }

    private fun bindDigitKeys(root: ViewGroup) {
        traverseKeys(root) { view, label ->
            if (label.length == 1 && (label[0].isDigit() || label[0] == '*' || label[0] == '#')) {
                view.setOnClickListener {
                    numberInput.append(label[0])
                    updateQueryUi()
                    updateNumberModePlaceholder()
                }
            }
        }
    }

    private fun traverseKeys(view: View, onKey: (TextView, String) -> Unit) {
        when (view) {
            is TextView -> {
                val label = view.text?.toString().orEmpty()
                // Skip action buttons that already have ids / dedicated handlers.
                if (view.id == View.NO_ID) {
                    onKey(view, label)
                }
            }

            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    traverseKeys(view.getChildAt(i), onKey)
                }
            }
        }
    }

    private fun activeBuffer(): StringBuilder {
        return if (isNumberMode) numberInput else letterQuery
    }

    private fun updateQueryUi() {
        val textColor = activity.getProperTextColor()
        panelBinding.letterSearchQuery.setTextColor(textColor)
        panelBinding.letterSearchPlaceholder.setTextColor(textColor)
        val text = activeBuffer().toString()
        panelBinding.letterSearchQuery.text = when {
            isNumberMode -> text
            // System IME / Chinese text: keep as typed; letter keys: show uppercase initials.
            systemImeMode || text.any { !it.isLetter() || Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN } -> text
            else -> text.uppercase(Locale.getDefault())
        }
    }

    private fun updateNumberModePlaceholder() {
        panelBinding.letterSearchList.beGone()
        panelBinding.letterSearchPlaceholder.beVisible()
        panelBinding.letterSearchPlaceholder.text = activity.getString(R.string.letter_key_number_hint)
        currentResults = emptyList()
    }

    private fun filterContacts() {
        val currentQuery = letterQuery.toString().trim()
        val generation = ++filterGeneration
        val snapshot = searchIndex

        indexExecutor.execute {
            val filtered = if (currentQuery.isEmpty()) {
                // Entering search: show everyone from local DB.
                snapshot.sortedWith(
                    compareBy<ContactInitialsHelper.SearchEntry> { it.primaryInitials.lowercase(Locale.US) }
                        .thenBy { it.contact.getDisplayName() }
                )
            } else {
                snapshot
                    .mapNotNull { entry ->
                        val rank = ContactInitialsHelper.matchRank(entry, currentQuery) ?: return@mapNotNull null
                        entry to rank
                    }
                    .sortedWith(
                        compareBy<Pair<ContactInitialsHelper.SearchEntry, ContactInitialsHelper.MatchRank>> { it.second }
                            .thenBy { it.first.primaryInitials }
                            .thenBy { it.first.contact.getDisplayName() }
                    )
                    .map { it.first }
            }

            mainHandler.post {
                if (generation != filterGeneration || isNumberMode) {
                    return@post
                }
                renderResults(filtered, currentQuery)
            }
        }
    }

    private fun renderResults(
        filtered: List<ContactInitialsHelper.SearchEntry>,
        currentQuery: String
    ) {
        if (isNumberMode) {
            updateNumberModePlaceholder()
            return
        }

        panelBinding.letterSearchPlaceholder.beVisibleIf(filtered.isEmpty())
        panelBinding.letterSearchPlaceholder.text = when {
            searchIndex.isEmpty() -> activity.getString(R.string.letter_search_indexing)
            currentQuery.isNotEmpty() -> activity.getString(R.string.no_contacts_match_initials)
            else -> activity.getString(R.string.letter_search_hint)
        }
        panelBinding.letterSearchList.beVisibleIf(filtered.isNotEmpty())

        val contacts = filtered.map { it.contact }
        currentResults = contacts

        // 修改时间：2026-10-10 17:12:58 — 搜索结果取消首字母后缀与姓氏圆标，右侧显示脱敏号码
        val adapter = listAdapter
        if (adapter == null) {
            listAdapter = ContactsAdapter(
                activity = activity,
                contacts = ArrayList(contacts),
                recyclerView = panelBinding.letterSearchList,
                highlightText = currentQuery,
                allowLongClick = false,
                itemClick = {
                    activity.startCallWithConfirmationCheck(it as Contact)
                },
                profileIconClick = null,
                showMaskedPhoneNoAvatar = true
            ).also {
                panelBinding.letterSearchList.adapter = it
            }
        } else {
            adapter.replaceItems(contacts, currentQuery)
        }
    }

    private fun panelWidth(): Float {
        val panel = panelBinding.root
        return if (panel.width > 0) {
            panel.width.toFloat()
        } else {
            panel.resources.displayMetrics.widthPixels.toFloat()
        }
    }
}

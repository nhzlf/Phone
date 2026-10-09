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
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.ContactsAdapter
import org.fossify.phone.databinding.LayoutLetterSearchPanelBinding
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.extensions.startContactDetailsIntent
import org.fossify.phone.views.SwipeDismissLinearLayout
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.sign

class LetterSearchController(
    private val activity: SimpleActivity,
    private val panelBinding: LayoutLetterSearchPanelBinding
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val indexExecutor = Executors.newSingleThreadExecutor()
    private var query = StringBuilder()
    private var isPanelVisible = false
    private var searchIndex: List<ContactInitialsHelper.SearchEntry> = emptyList()
    private var listAdapter: ContactsAdapter? = null
    private var filterGeneration = 0L

    val isVisible: Boolean
        get() = isPanelVisible

    init {
        setupKeyboard()
        setupSwipeToHide()
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
        filterContacts()
    }

    fun hide(animated: Boolean = true) {
        if (!isPanelVisible) {
            return
        }

        isPanelVisible = false
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

    fun clearQuery() {
        query.clear()
        updateQueryUi()
        filterContacts()
    }

    fun updateContacts(contacts: List<Contact>) {
        indexExecutor.execute {
            val indexed = ContactInitialsHelper.buildSearchEntries(contacts)
            mainHandler.post {
                searchIndex = indexed
                if (isPanelVisible || query.isNotEmpty()) {
                    filterContacts()
                }
            }
        }
    }

    fun refreshResults() {
        if (isPanelVisible) {
            filterContacts()
        }
    }

    private fun setupSwipeToHide() {
        val panel = panelBinding.root as? SwipeDismissLinearLayout ?: return
        panel.onSwipeDismiss = {
            hide()
        }
    }

    private fun setupKeyboard() {
        val keyboardRoot = panelBinding.letterKeyboardInclude.root as ViewGroup
        bindLetterKeys(keyboardRoot)

        panelBinding.letterKeyboardInclude.letterKeyClear.setOnClickListener {
            clearQuery()
        }
        panelBinding.letterKeyboardInclude.letterKeyBackspace.setOnClickListener {
            if (query.isNotEmpty()) {
                query.deleteCharAt(query.lastIndex)
                updateQueryUi()
                filterContacts()
            }
        }
        panelBinding.letterKeyboardInclude.letterKeyBackspace.setOnLongClickListener {
            clearQuery()
            true
        }
        panelBinding.letterKeyboardInclude.letterKeySpace.setOnClickListener {
            // ignored for initials search
        }
        panelBinding.letterKeyboardInclude.letterKeySearch.setOnClickListener {
            filterContacts()
        }
    }

    private fun bindLetterKeys(view: View) {
        when (view) {
            is TextView -> {
                val label = view.text?.toString().orEmpty()
                if (label.length == 1 && label[0].isLetter()) {
                    view.setOnClickListener {
                        query.append(label[0].lowercaseChar())
                        updateQueryUi()
                        filterContacts()
                    }
                }
            }

            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    bindLetterKeys(view.getChildAt(i))
                }
            }
        }
    }

    private fun updateQueryUi() {
        val textColor = activity.getProperTextColor()
        panelBinding.letterSearchQuery.setTextColor(textColor)
        panelBinding.letterSearchPlaceholder.setTextColor(textColor)
        panelBinding.letterSearchQuery.text = query.toString().uppercase(Locale.getDefault())
    }

    private fun filterContacts() {
        val currentQuery = query.toString()
        val generation = ++filterGeneration
        val snapshot = searchIndex

        if (currentQuery.isEmpty()) {
            renderResults(emptyList(), currentQuery)
            return
        }

        indexExecutor.execute {
            val filtered = snapshot
                .asSequence()
                .filter { ContactInitialsHelper.matches(it, currentQuery) }
                .sortedBy { ContactInitialsHelper.sortKey(it) }
                .toList()

            mainHandler.post {
                if (generation != filterGeneration) {
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
        panelBinding.letterSearchPlaceholder.beVisibleIf(currentQuery.isEmpty() || filtered.isEmpty())
        panelBinding.letterSearchPlaceholder.text = when {
            currentQuery.isEmpty() -> activity.getString(R.string.letter_search_hint)
            searchIndex.isEmpty() -> activity.getString(R.string.letter_search_indexing)
            else -> activity.getString(R.string.no_contacts_match_initials)
        }
        panelBinding.letterSearchList.beVisibleIf(filtered.isNotEmpty())

        val contacts = filtered.map { it.contact }
        val suffixByRawId = HashMap<Int, String>(filtered.size)
        filtered.forEach { entry ->
            val displayInitials = ContactInitialsHelper.getInitials(entry.contact.getNameToDisplay())
                .ifEmpty { entry.keys.firstOrNull().orEmpty() }
            val extraKeys = entry.keys
                .filter { it != displayInitials }
                .take(2)
            val suffix = buildString {
                append(displayInitials.uppercase(Locale.US))
                if (extraKeys.isNotEmpty()) {
                    append(" · ")
                    append(extraKeys.joinToString(" · "))
                }
            }
            suffixByRawId[entry.contact.rawId] = suffix
        }

        val suffixProvider: (Contact) -> String = { contact ->
            suffixByRawId[contact.rawId].orEmpty()
        }

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
                profileIconClick = {
                    activity.startContactDetailsIntent(it as Contact)
                },
                nameSuffixProvider = suffixProvider
            ).also {
                panelBinding.letterSearchList.adapter = it
            }
        } else {
            adapter.nameSuffixProvider = suffixProvider
            adapter.updateItems(contacts, currentQuery)
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

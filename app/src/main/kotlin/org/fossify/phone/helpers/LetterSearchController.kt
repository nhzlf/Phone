package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.view.MotionEvent
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
import java.util.Locale
import kotlin.math.abs

class LetterSearchController(
    private val activity: SimpleActivity,
    private val panelBinding: LayoutLetterSearchPanelBinding,
    private val contactsProvider: () -> List<Contact>
) {
    private val swipeThresholdPx = activity.resources.displayMetrics.density * 80
    private var query = StringBuilder()
    private var isPanelVisible = false
    private var downX = 0f
    private var downY = 0f
    private var isDraggingHorizontally = false

    val isVisible: Boolean
        get() = isPanelVisible

    init {
        setupKeyboard()
        setupSwipeToHide(panelBinding.letterSearchDragHandle)
        setupSwipeToHide(panelBinding.letterSearchQuery)
        updateQueryUi()
        filterContacts()
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
        panel.beVisible()
        panel.post {
            val width = if (panel.width > 0) {
                panel.width.toFloat()
            } else {
                panel.resources.displayMetrics.widthPixels.toFloat()
            }
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
        if (!animated) {
            panel.translationX = 0f
            panel.beGone()
            return
        }

        val width = if (panel.width > 0) {
            panel.width.toFloat()
        } else {
            panel.resources.displayMetrics.widthPixels.toFloat()
        }
        panel.animate()
            .translationX(width)
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

    fun refreshResults() {
        if (isPanelVisible) {
            filterContacts()
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
            // Space is ignored for initials matching; keep UI feedback only.
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeToHide(target: View) {
        target.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    isDraggingHorizontally = false
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!isDraggingHorizontally && abs(dx) > abs(dy) && abs(dx) > 24) {
                        isDraggingHorizontally = true
                    }
                    if (isDraggingHorizontally) {
                        panelBinding.root.translationX = dx
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingHorizontally) {
                        val dx = event.rawX - downX
                        if (abs(dx) >= swipeThresholdPx) {
                            hide()
                        } else {
                            panelBinding.root.animate().translationX(0f).setDuration(150).start()
                        }
                        isDraggingHorizontally = false
                        true
                    } else {
                        false
                    }
                }

                else -> false
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
        val filtered = if (currentQuery.isEmpty()) {
            ArrayList()
        } else {
            ArrayList(
                contactsProvider()
                    .filter { ContactInitialsHelper.matchesInitials(it.name, currentQuery) }
                    .sortedBy { ContactInitialsHelper.getInitials(it.name) }
            )
        }

        panelBinding.letterSearchPlaceholder.beVisibleIf(currentQuery.isEmpty() || filtered.isEmpty())
        panelBinding.letterSearchPlaceholder.text = when {
            currentQuery.isEmpty() -> activity.getString(R.string.letter_search_hint)
            else -> activity.getString(R.string.no_contacts_match_initials)
        }
        panelBinding.letterSearchList.beVisibleIf(filtered.isNotEmpty())

        ContactsAdapter(
            activity = activity,
            contacts = filtered,
            recyclerView = panelBinding.letterSearchList,
            highlightText = currentQuery,
            allowLongClick = false,
            itemClick = {
                activity.startCallWithConfirmationCheck(it as Contact)
            },
            profileIconClick = {
                activity.startContactDetailsIntent(it as Contact)
            }
        ).apply {
            panelBinding.letterSearchList.adapter = this
        }
    }
}

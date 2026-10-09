package org.fossify.phone.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Vertical LinearLayout that intercepts dominant horizontal swipes
 * so the letter-search panel can be dismissed from anywhere on it.
 */
class SwipeDismissLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onSwipeDismiss: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val dismissDistance = resources.displayMetrics.density * 64
    private var downRawX = 0f
    private var downRawY = 0f
    private var isDragging = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                downRawX = ev.rawX
                downRawY = ev.rawY
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val index = ev.findPointerIndex(activePointerId)
                if (index < 0) {
                    return false
                }
                val dx = ev.rawX - downRawX
                val dy = ev.rawY - downRawY
                if (!isDragging && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.15f) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isDragging = false
            }
        }
        return isDragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                downRawX = ev.rawX
                downRawY = ev.rawY
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == MotionEvent.INVALID_POINTER_ID) {
                    activePointerId = ev.getPointerId(0)
                    downRawX = ev.rawX - translationX
                    downRawY = ev.rawY
                    isDragging = true
                }
                val dx = ev.rawX - downRawX
                translationX = dx
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = translationX
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isDragging = false
                if (abs(dx) >= dismissDistance) {
                    onSwipeDismiss?.invoke()
                } else {
                    animate().translationX(0f).setDuration(150).start()
                }
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}

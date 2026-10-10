package org.fossify.phone.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Intercepts a dominant downward swipe so the custom letter keyboard can be hidden.
 */
class SwipeDownHideLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onSwipeDownHide: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val hideDistance = resources.displayMetrics.density * 56
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
                // Dominant downward swipe (not horizontal panel dismiss).
                if (!isDragging && dy > touchSlop && dy > abs(dx) * 1.2f) {
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
                    downRawX = ev.rawX
                    downRawY = ev.rawY - translationY
                    isDragging = true
                }
                val dy = (ev.rawY - downRawY).coerceAtLeast(0f)
                translationY = dy
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dy = translationY
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isDragging = false
                if (dy >= hideDistance) {
                    animate().translationY(height.toFloat().coerceAtLeast(hideDistance))
                        .setDuration(120)
                        .withEndAction {
                            translationY = 0f
                            onSwipeDownHide?.invoke()
                        }
                        .start()
                } else {
                    animate().translationY(0f).setDuration(120).start()
                }
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}

package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Makes a floating button draggable within its parent and persists position as ratios (0..1).
 * Tap (no meaningful move) still fires [View.performClick].
 */
object DraggableViewHelper {

    data class PositionStore(
        val loadX: () -> Float,
        val loadY: () -> Float,
        val save: (ratioX: Float, ratioY: Float) -> Unit
    ) {
        fun hasSaved(): Boolean = loadX() in 0f..1f && loadY() in 0f..1f
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(view: View, store: PositionStore) {
        val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false

        fun parentSize(): Pair<Float, Float>? {
            val parent = view.parent as? View ?: return null
            if (parent.width <= 0 || parent.height <= 0 || view.width <= 0 || view.height <= 0) {
                return null
            }
            val maxX = (parent.width - view.width).toFloat().coerceAtLeast(0f)
            val maxY = (parent.height - view.height).toFloat().coerceAtLeast(0f)
            return maxX to maxY
        }

        fun applySavedPosition() {
            if (!store.hasSaved()) {
                return
            }
            val (maxX, maxY) = parentSize() ?: return
            val targetX = store.loadX().coerceIn(0f, 1f) * maxX
            val targetY = store.loadY().coerceIn(0f, 1f) * maxY
            view.translationX = targetX - view.left
            view.translationY = targetY - view.top
        }

        fun saveCurrentPosition() {
            val (maxX, maxY) = parentSize() ?: return
            val absX = (view.left + view.translationX).coerceIn(0f, maxX)
            val absY = (view.top + view.translationY).coerceIn(0f, maxY)
            val ratioX = if (maxX <= 0f) 1f else absX / maxX
            val ratioY = if (maxY <= 0f) 1f else absY / maxY
            store.save(ratioX.coerceIn(0f, 1f), ratioY.coerceIn(0f, 1f))
        }

        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!dragging) {
                applySavedPosition()
            }
        }

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.post { applySavedPosition() }
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = v.x
                    startY = v.y
                    dragging = false
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        val parent = v.parent as? View ?: return@setOnTouchListener true
                        val maxX = (parent.width - v.width).toFloat().coerceAtLeast(0f)
                        val maxY = (parent.height - v.height).toFloat().coerceAtLeast(0f)
                        val newX = (startX + dx).coerceIn(0f, maxX)
                        val newY = (startY + dy).coerceIn(0f, maxY)
                        v.translationX = newX - v.left
                        v.translationY = newY - v.top
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    if (dragging) {
                        saveCurrentPosition()
                    } else {
                        v.performClick()
                    }
                    dragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    dragging = false
                    true
                }

                else -> false
            }
        }

        view.post { applySavedPosition() }
    }
}

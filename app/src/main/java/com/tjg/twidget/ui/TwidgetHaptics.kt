package com.tjg.twidget.ui

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import com.tjg.twidget.R

/** Action-based feedback: the platform chooses the effect and honours touch-feedback settings. */
internal object TwidgetHaptics {
    fun longPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Older versions supply LONG_PRESS through View.performLongClick instead. */
    fun dragPickup(view: View) {
        if (Build.VERSION.SDK_INT >= 34) {
            view.performHapticFeedback(HapticFeedbackConstants.DRAG_START)
        }
    }

    /** Soft repeated feedback while a dashboard tile is held. */
    fun dragHold(view: View) {
        // Older platforms cannot request this soft effect; retain their pickup and reorder ticks.
        if (Build.VERSION.SDK_INT >= 34) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        }
    }

    fun selection(view: View) {
        // Position changes can arrive on every drag frame. Keep ticks distinct, with no queued effects.
        val now = SystemClock.uptimeMillis()
        val previous = view.getTag(R.id.haptic_last_selection_time) as? Long
        if (previous != null && now - previous < 60L) return
        view.setTag(R.id.haptic_last_selection_time, now)
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
            else HapticFeedbackConstants.CLOCK_TICK,
        )
    }

    fun confirm(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }

    fun reject(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS,
        )
    }

    fun refresh(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_START
            else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }
}

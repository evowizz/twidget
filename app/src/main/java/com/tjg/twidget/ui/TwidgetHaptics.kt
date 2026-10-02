package com.tjg.twidget.ui

import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibrationAttributes
import android.media.AudioAttributes
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import com.tjg.twidget.R

/** Action-based feedback: the platform chooses the effect and honours touch-feedback settings. */
internal object TwidgetHaptics {
    /** Cancellable, gentle amplitude build-up; the mode transition supplies the final pop. */
    @Suppress("DEPRECATION")
    fun startHoldRamp(view: View, durationMs: Long): () -> Unit {
        if (!view.isHapticFeedbackEnabled || Settings.System.getInt(view.context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return {}
        val vibrator = view.context.getSystemService(Vibrator::class.java)
        if (vibrator == null || !vibrator.hasVibrator()) return {}
        if (vibrator.hasAmplitudeControl()) {
            val steps = 14
            val timings = LongArray(steps) { durationMs / steps }
            timings[steps - 1] += durationMs % steps
            val amplitudes = IntArray(steps) { index ->
                val progress = index.toFloat() / (steps - 1)
                (12f + 56f * progress * progress).toInt()
            }
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build())
            } else {
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
            }
            return { vibrator.cancel() }
        }
        // Without amplitude control, use brief platform ticks rather than a full-strength buzz.
        val pulses = listOf(0.25f, 0.55f, 0.8f).mapIndexed { index, progress ->
            Runnable {
                if (view.isAttachedToWindow) view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 34) {
                        if (index < 2) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.SEGMENT_TICK
                    } else HapticFeedbackConstants.CLOCK_TICK,
                )
            }.also { view.postDelayed(it, (durationMs * progress).toLong()) }
        }
        return { pulses.forEach { view.removeCallbacks(it) } }
    }

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

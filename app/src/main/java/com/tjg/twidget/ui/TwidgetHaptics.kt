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

/** Primitive hold feedback and platform action effects, respecting touch-feedback settings. */
internal object TwidgetHaptics {
    fun supportsPrimitive(view: View, primitive: Int): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val vibrator = view.context.getSystemService(Vibrator::class.java) ?: return false
        return vibrator.hasVibrator() && vibrator.arePrimitivesSupported(primitive).all { it }
    }

    /** Debug previews use the same touch settings and attributes as production feedback. */
    fun previewPrimitive(view: View, primitive: Int): () -> Unit {
        if (Build.VERSION.SDK_INT < 30 || !supportsPrimitive(view, primitive) ||
            !view.isHapticFeedbackEnabled || Settings.System.getInt(view.context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return {}
        val vibrator = view.context.getSystemService(Vibrator::class.java) ?: return {}
        vibrateTouch(vibrator, VibrationEffect.startComposition().addPrimitive(primitive, 0.7f).compose())
        return { vibrator.cancel() }
    }

    /** Cancellable primitive pulses build in strength; the transition supplies the final pop. */
    @Suppress("DEPRECATION")
    fun startHoldRamp(view: View, durationMs: Long): () -> Unit {
        if (!view.isHapticFeedbackEnabled || Settings.System.getInt(view.context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return {}
        val vibrator = view.context.getSystemService(Vibrator::class.java)
        if (vibrator == null || !vibrator.hasVibrator()) return {}
        if (Build.VERSION.SDK_INT >= 30) {
            val primitive = if (Build.VERSION.SDK_INT >= 31 &&
                vibrator.arePrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK).all { it }) {
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK
            } else VibrationEffect.Composition.PRIMITIVE_TICK
            if (vibrator.arePrimitivesSupported(primitive).all { it }) {
                val primitiveDuration = if (Build.VERSION.SDK_INT >= 31)
                    vibrator.getPrimitiveDurations(primitive).first().coerceAtLeast(1) else 12
                val steps = (durationMs / maxOf(primitiveDuration, 35)).toInt().coerceIn(2, 8)
                val interval = (durationMs / steps).toInt()
                val composition = VibrationEffect.startComposition()
                repeat(steps) { index ->
                    val progress = index.toFloat() / (steps - 1)
                    composition.addPrimitive(primitive, 0.04f + 0.48f * progress * progress,
                        if (index == 0) 0 else (interval - primitiveDuration).coerceAtLeast(0))
                }
                vibrateTouch(vibrator, composition.compose())
                return { vibrator.cancel() }
            }
        }
        // Devices without composition support retain brief platform ticks.
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

    fun editModePop(view: View, entering: Boolean) {
        if (!view.isHapticFeedbackEnabled || Settings.System.getInt(view.context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
        if (Build.VERSION.SDK_INT >= 30) {
            val vibrator = view.context.getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator() &&
                vibrator.arePrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK).all { it }) {
                vibrateTouch(vibrator, VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, if (entering) 0.8f else 0.5f)
                    .compose())
                return
            }
        }
        if (entering) longPress(view) else confirm(view)
    }

    @Suppress("DEPRECATION")
    private fun vibrateTouch(vibrator: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build())
        } else {
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
        }
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

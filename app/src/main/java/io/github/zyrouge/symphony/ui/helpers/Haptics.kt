package io.github.zyrouge.symphony.ui.helpers

import android.view.HapticFeedbackConstants
import android.os.Build

/**
 * Light haptic cues for the interactions that otherwise give no physical
 * confirmation. Goes through the window's decor view so it works from any
 * callback (click handlers, non-composable code) without needing a
 * LocalHapticFeedback in scope, and respects the system "touch feedback"
 * setting automatically.
 */
enum class Haptic { LongPress, Toggle, Success }

fun ViewContext.haptic(kind: Haptic) {
    val view = activity.window?.decorView ?: return
    val constant = when (kind) {
        Haptic.LongPress -> HapticFeedbackConstants.LONG_PRESS
        Haptic.Toggle -> when {
            Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.CONFIRM
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        }
        Haptic.Success -> when {
            Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.CONFIRM
            else -> HapticFeedbackConstants.CONTEXT_CLICK
        }
    }
    view.performHapticFeedback(constant)
}

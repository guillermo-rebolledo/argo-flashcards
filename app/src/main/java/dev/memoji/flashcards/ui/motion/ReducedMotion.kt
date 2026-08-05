package dev.memoji.flashcards.ui.motion

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the user has asked the system to remove animations. Motion that is pleasant to most
 * people is nauseating or unreadable to some, and the setting they already changed once, in
 * Accessibility, is the answer for the whole app — not something to ask them again per screen.
 *
 * Re-read while the screen is up, so turning it on takes effect without restarting the app.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val reduced by produceState(initialValue = isReducedMotion(resolver), resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = isReducedMotion(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        awaitDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/**
 * Android has no single "reduce motion" flag: the Accessibility toggle that removes animations
 * is the one that sets the animator duration scale to zero, so that is what to read.
 */
internal fun isReducedMotion(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

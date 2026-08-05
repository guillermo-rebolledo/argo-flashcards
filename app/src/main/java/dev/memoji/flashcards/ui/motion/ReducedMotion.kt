package dev.memoji.flashcards.ui.motion

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Whether this app is animating, as decided once at the root and read by every screen below
 * it. A screen asks whether to animate; it has no business knowing that the answer comes from
 * an Accessibility setting, a preference, or both.
 *
 * Defaults to animating, so a preview or a test that provides nothing still renders.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * The rule the two sources combine by. The system setting is not something this app may
 * overrule: a user who told Android to remove animations has already answered, and the
 * in-app toggle only exists to turn motion off for someone the system setting has not.
 */
fun reducedMotion(systemSetting: Boolean, userOverride: Boolean): Boolean =
    systemSetting || userOverride

/**
 * Whether the user has asked the system to remove animations. Motion that is pleasant to most
 * people is nauseating or unreadable to some, and the setting they already changed once, in
 * Accessibility, is the answer for the whole app — not something to ask them again per screen.
 *
 * Re-read while the screen is up, so turning it on takes effect without restarting the app.
 */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    // Read once, then only when it changes: this crosses to the settings provider, and a Card
    // being dragged recomposes every frame.
    val initial = remember(resolver) { isReducedMotion(resolver) }
    val reduced by produceState(initialValue = initial, resolver) {
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

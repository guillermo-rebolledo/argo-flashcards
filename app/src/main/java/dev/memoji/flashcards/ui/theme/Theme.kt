package dev.memoji.flashcards.ui.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Wallpaper-derived colour needs the Material You APIs added in Android 12. Taking the SDK
 * level as a parameter keeps the rule itself testable off-device; the annotation is what
 * tells lint that the calls it guards are safe.
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicColor(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt >= Build.VERSION_CODES.S

/**
 * Typography is the Compose Material 3 default — Roboto. The design specifies Google Sans,
 * which is proprietary and deliberately not used; see the spec's Theming section.
 *
 * [dynamicColor] permits wallpaper colour rather than forcing it — below Android 12 there is
 * none to take, so the teal scheme is used whatever the caller asks for.
 */
@Composable
fun FlashcardsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && supportsDynamicColor() ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> TealDarkColorScheme
        else -> TealLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

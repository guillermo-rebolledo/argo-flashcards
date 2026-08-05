package dev.memoji.flashcards.core.model

/**
 * Every preference the user has, read as one value.
 *
 * They are stored together and almost always wanted together — the theme and the motion
 * setting are both needed before the first frame — so they are handed out together rather
 * than as a flow each, which would have the app waiting on three reads of the same file.
 *
 * The defaults here are what the app does before the user has chosen anything, which is also
 * what it does when a stored value is one it no longer understands.
 */
data class UserSettings(
    val sessionLength: SessionLength = SessionLength.DEFAULT,
    val theme: ThemePreference = ThemePreference.DEFAULT,
    /**
     * An override on top of the system accessibility setting, never a replacement: the system
     * having animations off wins whatever this says. Off means "no opinion", not "animate".
     */
    val reducedMotion: Boolean = false,
    /**
     * Takes the day streak and the seven-day grid off the Progress screen. For the user who
     * finds a counter something to lose rather than something to keep — the app is not allowed
     * to become another thing keeping score.
     */
    val hideStreak: Boolean = false,
) {
    companion object {
        val DEFAULT = UserSettings()
    }
}

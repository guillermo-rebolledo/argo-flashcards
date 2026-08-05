package dev.memoji.flashcards.core.model

/**
 * Whether the user has overridden light and dark, and which way.
 *
 * The system setting is the answer until they say otherwise: someone who has already told
 * Android they want dark at night should not have to tell this app too. [LIGHT] and [DARK] are
 * for the user who wants this one app to disagree with the rest of the phone.
 */
enum class ThemePreference {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
    ;

    /**
     * The only question the theme actually asks. [systemInDarkTheme] is what Android reports
     * right now, and it is what an unset preference resolves to — including when the user
     * changes it while the app is open.
     */
    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        FOLLOW_SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** Following the system is not a choice the user made; it is the absence of one. */
        val DEFAULT = FOLLOW_SYSTEM

        /**
         * Stored by name, so an unfamiliar value — a preference written by a later version,
         * read by an earlier one — falls back to the system rather than to a guess.
         */
        fun ofName(name: String): ThemePreference =
            entries.find { it.name == name } ?: DEFAULT
    }
}

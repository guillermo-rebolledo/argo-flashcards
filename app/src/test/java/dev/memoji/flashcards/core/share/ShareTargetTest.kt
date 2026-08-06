package dev.memoji.flashcards.core.share

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The half of the share that is not code: without the filter in the manifest the app is not in
 * the share sheet at all, and nothing else here would ever run. The intents are resolved
 * against the merged manifest, so this is the same question the system asks.
 */
@RunWith(RobolectricTestRunner::class)
class ShareTargetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the app is offered for shared text`() {
        assertTrue(resolves(share("A selection worth remembering.")))
    }

    @Test
    fun `the app is offered for a shared link`() {
        assertTrue(resolves(share("https://example.com/big-o")))
    }

    /**
     * One activity, so a share lands in the app the user left rather than in a second copy of
     * it — the flow is opened once however many times something is shared into it.
     */
    @Test
    fun `a share resolves to the one activity the app has`() {
        val activities = context.packageManager.queryIntentActivities(
            share("A selection worth remembering."),
            PackageManager.MATCH_DEFAULT_ONLY,
        )

        assertEquals(1, activities.size)
        assertEquals(
            "dev.memoji.flashcards.MainActivity",
            activities.single().activityInfo.name,
        )
    }

    private fun resolves(intent: Intent): Boolean =
        context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .isNotEmpty()

    /** Aimed at this app, the way the share sheet aims the user's choice at it. */
    private fun share(text: String) = sharedTextIntent(text).setPackage(context.packageName)
}

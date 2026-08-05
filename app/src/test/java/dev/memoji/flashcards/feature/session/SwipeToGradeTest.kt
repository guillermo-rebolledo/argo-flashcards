package dev.memoji.flashcards.feature.session

import dev.memoji.flashcards.core.model.Grade
import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeToGradeTest {

    @Test
    fun `dragging right past the commit distance Grades Knew it`() {
        assertEquals(SwipeRelease.GradeCard(Grade.KNEW_IT), releaseOf(120f))
    }

    @Test
    fun `dragging left past the commit distance Grades Again`() {
        assertEquals(SwipeRelease.GradeCard(Grade.AGAIN), releaseOf(-120f))
    }

    @Test
    fun `releasing short of the commit distance springs the Card back`() {
        assertEquals(SwipeRelease.SpringBack, releaseOf(60f))
        assertEquals(SwipeRelease.SpringBack, releaseOf(-60f))
    }

    @Test
    fun `the commit distance itself is not far enough`() {
        assertEquals(SwipeRelease.SpringBack, releaseOf(CommitDistance))
        assertEquals(SwipeRelease.SpringBack, releaseOf(-CommitDistance))
    }

    @Test
    fun `a drag under the tap distance is a tap`() {
        assertEquals(SwipeRelease.Tap, releaseOf(4f))
        assertEquals(SwipeRelease.Tap, releaseOf(-4f))
        assertEquals(SwipeRelease.Tap, releaseOf(0f))
    }

    @Test
    fun `the tap distance itself is a drag, not a tap`() {
        assertEquals(SwipeRelease.SpringBack, releaseOf(TapDistance))
    }

    @Test
    fun `the Grade hint fades in over the drag towards it`() {
        assertEquals(0f, gradeHintAlpha(0f, Grade.KNEW_IT, CommitDistance), Tolerance)
        assertEquals(0.5f, gradeHintAlpha(45f, Grade.KNEW_IT, CommitDistance), Tolerance)
        assertEquals(1f, gradeHintAlpha(90f, Grade.KNEW_IT, CommitDistance), Tolerance)
    }

    @Test
    fun `the Grade hint does not fade past fully shown`() {
        assertEquals(1f, gradeHintAlpha(400f, Grade.KNEW_IT, CommitDistance), Tolerance)
        assertEquals(1f, gradeHintAlpha(-400f, Grade.AGAIN, CommitDistance), Tolerance)
    }

    @Test
    fun `only the hint the drag is heading towards shows`() {
        assertEquals(0f, gradeHintAlpha(60f, Grade.AGAIN, CommitDistance), Tolerance)
        assertEquals(0f, gradeHintAlpha(-60f, Grade.KNEW_IT, CommitDistance), Tolerance)
        assertEquals(0.5f, gradeHintAlpha(-45f, Grade.AGAIN, CommitDistance), Tolerance)
    }

    private fun releaseOf(distance: Float) =
        swipeRelease(distance, tapDistance = TapDistance, commitDistance = CommitDistance)

    private companion object {
        /** The thresholds are dp in the UI; the rules only ever see them already in pixels. */
        const val TapDistance = 10f
        const val CommitDistance = 90f
        const val Tolerance = 0.001f
    }
}

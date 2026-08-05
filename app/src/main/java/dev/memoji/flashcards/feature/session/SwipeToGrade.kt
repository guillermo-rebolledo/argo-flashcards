package dev.memoji.flashcards.feature.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.memoji.flashcards.core.model.Grade
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What letting go of a horizontal drag on a Card means. Kept apart from the gesture plumbing so
 * the thresholds — the part a user can actually feel — can be read and tested on their own.
 */
internal sealed interface SwipeRelease {

    /** Too small to have been meant as a drag at all: the user tapped the Card. */
    data object Tap : SwipeRelease

    /** A real drag, but not far enough to mean anything. The Card returns to where it was. */
    data object SpringBack : SwipeRelease

    /** Far enough to be a verdict. */
    data class GradeCard(val grade: Grade) : SwipeRelease
}

/**
 * How far right a Card has to be dragged before releasing it Grades `Knew it`, and how far left
 * before it Grades `Again`. Short enough to be a flick, far enough that no one arrives here by
 * accident.
 */
internal val SwipeCommitDistance: Dp = 90.dp

/** Under this, the finger never really moved and the user meant to tap the Card. */
internal val SwipeTapDistance: Dp = 10.dp

/**
 * Degrees of tilt per dp dragged, so the Card leans the way it is being thrown. Per dp, not per
 * pixel: the same drag has to look the same on a dense screen as on a coarse one.
 */
internal const val SwipeRotationPerDp: Float = 0.03f

/** [distance] is signed: positive is a drag to the right. All distances are in pixels. */
internal fun swipeRelease(distance: Float, tapDistance: Float, commitDistance: Float): SwipeRelease =
    when {
        abs(distance) < tapDistance -> SwipeRelease.Tap
        abs(distance) > commitDistance -> SwipeRelease.GradeCard(gradeOf(distance))
        else -> SwipeRelease.SpringBack
    }

/**
 * How far in the user is towards [grade], as an opacity for its hint. The hint is the whole
 * reason a swipe is safe: it says which Grade is coming while there is still time to change
 * course, and reaches full strength exactly where the drag would commit.
 */
internal fun hintAlpha(distance: Float, grade: Grade, commitDistance: Float): Float =
    if (gradeOf(distance) != grade || distance == 0f) 0f
    else min(1f, abs(distance) / commitDistance)

private fun gradeOf(distance: Float): Grade = if (distance > 0f) Grade.KNEW_IT else Grade.AGAIN

/** How long the Card takes to leave once a Grade is committed. */
private const val CommitMillis: Int = 200

/** Slow to leave the finger, quick to go — the Card is thrown, not slid. */
private val CommitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** With nothing measured yet, far enough that the Card is gone whatever size it turned out. */
private const val OffscreenFallback: Float = 4f

/**
 * One Card's worth of swipe: where it has been dragged to, and what that has committed to.
 *
 * A Card is graded once and once only. Rapid drags, a drag that lands while the Card is already
 * on its way out, a button pressed mid-flight — all of them find [graded] already true and do
 * nothing, so no Card is ever double-graded and none is skipped.
 */
@Stable
internal class CardSwipe(
    private val scope: CoroutineScope,
    private val tapDistance: Float,
    private val commitDistance: Float,
    private val rotationPerPixel: Float,
    private val reducedMotion: Boolean,
    private val onGrade: (Grade) -> Unit,
) {

    private val offset = Animatable(0f)

    /** Set by the Card as it is measured, so it is thrown exactly as far as it needs to go. */
    var cardWidth: Float by mutableFloatStateOf(0f)

    var graded: Boolean by mutableStateOf(false)
        private set

    /** How far the Card has been dragged, signed: positive is towards `Knew it`. */
    val distance: Float get() = offset.value

    /** Under reduced motion the Card does not follow the finger — only the hints answer it. */
    val translation: Float get() = if (reducedMotion) 0f else offset.value

    val rotation: Float get() = if (reducedMotion) 0f else offset.value * rotationPerPixel

    /** The Card fades as it leaves, and only as it leaves. */
    val alpha: Float
        get() = if (!graded || cardWidth <= 0f) 1f
        else (1f - abs(offset.value) / cardWidth).coerceIn(0f, 1f)

    fun hintAlpha(grade: Grade): Float = hintAlpha(offset.value, grade, commitDistance)

    fun drag(delta: Float) {
        if (graded) return
        scope.launch { offset.snapTo(offset.value + delta) }
    }

    /** [onTap] is for a drag so short it was a tap — the release of an actual tap on the Card. */
    fun release(onTap: () -> Unit) {
        if (graded) return
        when (val release = swipeRelease(offset.value, tapDistance, commitDistance)) {
            SwipeRelease.Tap -> {
                springBack()
                onTap()
            }
            SwipeRelease.SpringBack -> springBack()
            is SwipeRelease.GradeCard -> grade(release.grade)
        }
    }

    fun cancel() {
        if (!graded) springBack()
    }

    /** The one way a Grade is given, whether it came from a swipe or from a button. */
    fun grade(grade: Grade) {
        if (graded) return
        graded = true
        scope.launch {
            if (!reducedMotion) {
                val offscreen = max(cardWidth, commitDistance * OffscreenFallback)
                offset.animateTo(
                    targetValue = if (grade == Grade.KNEW_IT) offscreen else -offscreen,
                    animationSpec = tween(durationMillis = CommitMillis, easing = CommitEasing),
                )
            }
            onGrade(grade)
        }
    }

    private fun springBack() {
        scope.launch { offset.animateTo(targetValue = 0f, animationSpec = spring()) }
    }
}

@Composable
internal fun rememberCardSwipe(reducedMotion: Boolean, onGrade: (Grade) -> Unit): CardSwipe {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val tapDistance = with(density) { SwipeTapDistance.toPx() }
    val commitDistance = with(density) { SwipeCommitDistance.toPx() }
    val rotationPerPixel = SwipeRotationPerDp / density.density
    val currentOnGrade by rememberUpdatedState(onGrade)
    return remember(scope, tapDistance, commitDistance, rotationPerPixel, reducedMotion) {
        CardSwipe(
            scope = scope,
            tapDistance = tapDistance,
            commitDistance = commitDistance,
            rotationPerPixel = rotationPerPixel,
            reducedMotion = reducedMotion,
            onGrade = { currentOnGrade(it) },
        )
    }
}

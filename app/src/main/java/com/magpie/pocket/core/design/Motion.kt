package com.magpie.pocket.core.design

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The page's whole motion budget comes from here: either the system's animator switch (the
 * accessibility "remove animations" setting) or the app's own reduce-motion preference turns
 * every duration to zero, so nothing keeps moving behind a reader who asked it to stop.
 */
@Composable
internal fun motionDuration(baseMillis: Int): Int = motionDuration(
    baseMillis = baseMillis,
    animationsEnabled = !MagpieTheme.preferences.reduceMotion && systemAnimationsEnabled()
)

/** The budget itself, split out so a test can ask what a switched-off animator returns. */
internal fun motionDuration(baseMillis: Int, animationsEnabled: Boolean): Int =
    if (animationsEnabled) baseMillis else 0

/** `ValueAnimator.areAnimatorsEnabled` arrived in Oreo; older builds keep their animations. */
private fun systemAnimationsEnabled(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

/** The ease-out cubic every transition here shares: fast at the start, settling, never overshoot. */
internal val motionEaseOutCubic: Easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/**
 * False on a composition's first frame, true afterwards. Pairs with animate*AsState when a
 * value has to grow out of zero on entry instead of snapping to its first reading.
 */
@Composable
internal fun rememberEntered(): Boolean {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    return entered
}

/**
 * The 0..1 progress of a page that has just entered, played once per composition and never
 * again on recomposition. [delayMillis] staggers the newcomers of a list; callers pass it
 * through [motionDuration] so turning motion off removes the wait as well as the travel.
 *
 * The state comes back unread on purpose: read it inside a graphicsLayer or draw block, so a
 * frame of the entrance invalidates one layer instead of recomposing the whole screen.
 */
@Composable
internal fun rememberEntranceProgress(durationMillis: Int, delayMillis: Int = 0): State<Float> {
    val entered = rememberEntered()
    return animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis, delayMillis, motionEaseOutCubic),
        label = "entranceProgress"
    )
}

/** A layer opens in 340ms: a column grows from its top edge while it fades in. */
@Composable
internal fun motionExpandEnter(): EnterTransition =
    expandVertically(tween(motionDuration(340), easing = motionEaseOutCubic)) +
        fadeIn(tween(motionDuration(200)))

/** The closing half of [motionExpandEnter]; closing is deliberately the quicker direction. */
@Composable
internal fun motionExpandExit(): ExitTransition =
    shrinkVertically(tween(motionDuration(260), easing = motionEaseOutCubic)) +
        fadeOut(tween(motionDuration(120)))

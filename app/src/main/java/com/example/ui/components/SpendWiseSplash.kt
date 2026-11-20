package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SpendWise animated launch splash:
 * Displays the exact SpendWise rupee-wallet logo on a clean white background, then animates
 * its two opposing curved arrows (top green arrow to the right side, bottom orange arrow to
 * the left side of the screen) before smoothly opening the app.
 */
@Composable
fun SpendWiseLaunchSplash(
    onAnimationFinished: () -> Unit
) {
    val emblemScale = remember { Animatable(0.78f) }
    val emblemAlpha = remember { Animatable(0f) }
    val arrowSplitProgress = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Phase 1: Crisp logo appearance
        launch { emblemAlpha.animateTo(1f, tween(340, easing = FastOutSlowInEasing)) }
        emblemScale.animateTo(1f, tween(380, easing = FastOutSlowInEasing))

        delay(220)

        // Phase 2: The two logo arrows detach and glide to the left and right sides of the screen
        arrowSplitProgress.animateTo(1f, tween(680, easing = FastOutSlowInEasing))

        delay(180)

        // Phase 3: Smooth reveal into the application
        exitAlpha.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        onAnimationFinished()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exitAlpha.value }
            .background(MaterialTheme.colorScheme.background)
            .testTag("spendwise_splash"),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val logoSize = 188.dp
        val maxTravelPx = with(density) { (maxWidth * 0.56f).toPx() }
        val verticalArcPx = with(density) { 18.dp.toPx() }

        val split = arrowSplitProgress.value
        val baseScale = emblemScale.value
        val baseAlpha = emblemAlpha.value
        val arrowAlpha = (baseAlpha * (1f - split * 0.25f)).coerceIn(0f, 1f)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(logoSize),
                contentAlignment = Alignment.Center
            ) {
                // Layer 1 (behind center wallet): Top curved green arrow -> glides to the RIGHT side
                Image(
                    painter = painterResource(id = R.drawable.spendwise_logo_arrow_top),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = baseScale
                            scaleY = baseScale
                            translationX = maxTravelPx * split
                            translationY = -verticalArcPx * split
                            rotationZ = 8f * split
                            alpha = arrowAlpha
                        }
                )

                // Layer 2 (behind center wallet & receipt): Bottom curved orange arrow -> glides to the LEFT side
                Image(
                    painter = painterResource(id = R.drawable.spendwise_logo_arrow_bottom),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = baseScale
                            scaleY = baseScale
                            translationX = -maxTravelPx * split
                            translationY = verticalArcPx * split
                            rotationZ = 8f * split
                            alpha = arrowAlpha
                        }
                )

                // Layer 3 (foreground): Central blue rupee wallet, growth bars, coins & receipt
                Image(
                    painter = painterResource(id = R.drawable.spendwise_logo_center),
                    contentDescription = "SpendWise Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val walletScale = baseScale * (1f + 0.04f * split)
                            scaleX = walletScale
                            scaleY = walletScale
                            alpha = baseAlpha
                        }
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "SpendWise",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { alpha = baseAlpha }
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Private · Local-First Personal Finance",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = baseAlpha }
            )
        }
    }
}

package com.janbina.swipeablecards

import androidx.compose.animation.core.*
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Float.max
import java.lang.Float.min
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Preview
@Composable
fun SwipeableCards() {
    val colors = listOf(
        Color(0xff90caf9),
        Color(0xfffafafa),
        Color(0xffef9a9a),
        Color(0xfffff59d),
    )
    var colorsInOrder by remember { mutableStateOf(colors) }

    Box(
        Modifier
            .background(Color.Black)
            .padding(vertical = 32.dp)
            .fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        colors.forEach { color ->
            SwipeableCard(
                order = colorsInOrder.indexOf(color),
                totalCount = colors.size,
                backgroundColor = color,
                onSwipe = {
                    colorsInOrder = colorsInOrder.toMutableList().apply {
                        remove(color)
                        add(color)
                    }
                }
            )
        }
    }
}

@Composable
fun SwipeableCard(
    order: Int,
    totalCount: Int,
    backgroundColor: Color = Color.White,
    onSwipe: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = 1f - order * 0.05f,
    )
    val animatedYOffset by animateDpAsState(
        targetValue = ((order + 1) * -36).dp,
    )

    Box(
        modifier = Modifier
            .zIndex((totalCount - order).toFloat())
            .offset { IntOffset(x = 0, y = animatedYOffset.roundToPx()) }
            .scale(animatedScale)
            .swipeToBack { onSwipe() }
    ) {
        SampleCard(backgroundColor = backgroundColor)
    }
}

@Composable
fun SampleCard(backgroundColor: Color = Color.White) {
    Card(
        modifier = Modifier
            .height(220.dp)
            .fillMaxWidth(.8f),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier.padding(vertical = 24.dp, horizontal = 32.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Row(
                Modifier.fillMaxWidth(0.5f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .pillShape()
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Box(
                        Modifier
                            .height(12.dp)
                            .fillMaxWidth()
                            .pillShape()
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .height(12.dp)
                            .fillMaxWidth(0.6f)
                            .pillShape()
                    )
                }
            }
        }
    }
}

fun Modifier.pillShape() =
    this.then(
        background(Color.Black.copy(0.3f), CircleShape)
    )

fun Modifier.swipeToBack(
    onSwipe: () -> Unit
): Modifier = composed {
    val offsetY = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    var leftSide by remember { mutableStateOf(true) }

    pointerInput(Unit) {
        val decay = splineBasedDecay<Float>(this)

        coroutineScope {
            while (true) {
                val pointerId = awaitPointerEventScope { awaitFirstDown().id }
                offsetY.stop()

                val velocityTracker = VelocityTracker()

                awaitPointerEventScope {
                    verticalDrag(pointerId) { change ->
                        val verticalDragOffset = offsetY.value + change.positionChange().y
                        val horizontalPosition = change.previousPosition.x

                        leftSide = horizontalPosition <= size.width / 2
                        val offsetXRatioFromMiddle = if (leftSide) {
                            horizontalPosition / (size.width / 2)
                        } else {
                            (size.width - horizontalPosition) / (size.width / 2)
                        }
                        val rotationalOffset = max(1f, (1f - offsetXRatioFromMiddle) * 4f)

                        launch {
                            offsetY.snapTo(verticalDragOffset)
                            rotation.snapTo(if (leftSide) rotationalOffset else -rotationalOffset)
                        }

                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        change.consumePositionChange()
                    }
                }

                val velocity = velocityTracker.calculateVelocity().y
                val targetOffsetY = decay.calculateTargetValue(offsetY.value, velocity)

                if (targetOffsetY.absoluteValue <= size.height) {
                    // Not enough velocity; Reset.
                    coroutineScope {
                        launch { offsetY.animateTo(targetValue = 0f, initialVelocity = velocity) }
                        launch { rotation.animateTo(targetValue = 0f, initialVelocity = velocity) }
                    }
                } else {
                    // Enough velocity to fling the card to the back
                    val boomerangDuration = 600
                    val maxDistanceToFling = (size.height * 4).toFloat()
                    val maxRotations = 3
                    val EaseInOutEasing = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)

                    val distanceToFling = min(
                        targetOffsetY.absoluteValue + (size.height / 2), maxDistanceToFling
                    )
                    val rotationToFling = min(
                        360f * (targetOffsetY.absoluteValue / size.height).roundToInt(),
                        360f * maxRotations
                    )
                    val rotationOvershoot = rotationToFling + 12f

                    coroutineScope {
                        launch {
                            rotation.animateTo(targetValue = if (leftSide) rotationToFling else -rotationToFling,
                                initialVelocity = velocity,
                                animationSpec = keyframes {
                                    durationMillis = boomerangDuration
                                    0f at 0 with EaseInOutEasing
                                    (if (leftSide) rotationOvershoot else -rotationOvershoot) at boomerangDuration - 50 with LinearOutSlowInEasing
                                    (if (leftSide) rotationToFling else -rotationToFling) at boomerangDuration
                                })
                            rotation.snapTo(0f)
                        }
                        launch {
                            offsetY.animateTo(targetValue = 0f,
                                initialVelocity = velocity,
                                animationSpec = keyframes {
                                    durationMillis = boomerangDuration
                                    -distanceToFling at (boomerangDuration / 2) with EaseInOutEasing
                                    40f at boomerangDuration - 70
                                })
                        }
                        delay(100)
                        onSwipe()
                    }
                }
            }
        }
    }
        .offset { IntOffset(0, offsetY.value.roundToInt()) }
        .graphicsLayer {
            transformOrigin = TransformOrigin.Center
            rotationZ = rotation.value
        }
}

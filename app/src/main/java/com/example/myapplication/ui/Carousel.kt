package com.example.myapplication.ui

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random


internal enum class SwipeDirection { Forward, Backward }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <T> Carousel(
    content: CarouselContent<T>,
    modifier: Modifier = Modifier,
    centerItemHorizontalPadding: Dp = 0.dp,
    itemSpacing: Dp = 0.dp,
    item: @Composable (item: T) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (constraints.isZero) return@BoxWithConstraints
        // saving carousel state
        var contentWithItems by remember {
            mutableStateOf(ContentWithComposables.from(item, content))
        }

        val updatingContent by rememberUpdatedState(content)

        val confirmValueChange = remember {
            { position: Position ->
                when (position) {
                    Position.Left -> updatingContent.left != null
                    Position.Center -> true
                    Position.Right -> updatingContent.right != null
                }
            }
        }
        val density = LocalDensity.current
        val positionalThreshold = { distance: Float -> distance * 0.5f }
        val velocityThreshold = { with(density) { 20.dp.toPx() } }
        val snapAnimationSpec = tween<Float>()
        val decayAnimationSpec = exponentialDecay<Float>(10f)

        val anchoredDraggableState = rememberSaveable(
            saver = AnchoredDraggableState.Saver(
                snapAnimationSpec = snapAnimationSpec,
                decayAnimationSpec = decayAnimationSpec,
                positionalThreshold = positionalThreshold,
                velocityThreshold = velocityThreshold,
                confirmValueChange = confirmValueChange,
            )
        ) {
            AnchoredDraggableState(
                initialValue = Position.Center,
                snapAnimationSpec = snapAnimationSpec,
                decayAnimationSpec = decayAnimationSpec,
                positionalThreshold = positionalThreshold,
                velocityThreshold = velocityThreshold,
                confirmValueChange = confirmValueChange
            )
        }
        val maxWidth = constraints.maxWidth.toFloat()
        SideEffect {
            val offsetCenterItem = with(density) { centerItemHorizontalPadding.toPx() }
            val itemWidth = with(density) { maxWidth - (centerItemHorizontalPadding.toPx() * 2)}
            val positionLeftItem = with(density) { itemWidth + itemSpacing.toPx() + offsetCenterItem }
            val positionRightItem = with(density) { - itemWidth - itemSpacing.toPx() + offsetCenterItem }
            anchoredDraggableState.updateAnchors(
                DraggableAnchors {
                    Position.Right at positionRightItem
                    Position.Center at offsetCenterItem
                    Position.Left at positionLeftItem
                }
            )
        }

        LaunchedEffect(Unit) {
            var programmaticAnimationWasInProgress = false
            var previousContentWithItems = contentWithItems
            snapshotFlow { updatingContent }
                .filterNot { previousContentWithItems.sameContent(it) }
                .collectLatest { content ->
                    // saving previous content in case of interruption
                    val prev = previousContentWithItems
                    val (direction, newContent) = ContentWithComposables.basedOnPrevious(
                        item,
                        content,
                        prev,
                    )
                    previousContentWithItems = newContent

                    if (anchoredDraggableState.currentValue == Position.Center && direction != null) {
                        val position = when (direction) {
                            SwipeDirection.Forward -> Position.Right
                            SwipeDirection.Backward -> Position.Left
                        }
                        if (!programmaticAnimationWasInProgress) {
                            programmaticAnimationWasInProgress = true
                            anchoredDraggableState.animateTo(position)
                        } else {
                            // previous animateTo was interrupted, so we re-center and animate to side
                            contentWithItems = prev
                            anchoredDraggableState.snapTo(Position.Center)
                            anchoredDraggableState.animateTo(position)
                        }
                        programmaticAnimationWasInProgress = false
                    }

                    withContext(NonCancellable) {
                        anchoredDraggableState.snapTo(Position.Center)
                    }
                    contentWithItems = newContent
                }
        }

        Layout(
            modifier = Modifier.anchoredDraggable(
                state = anchoredDraggableState,
                orientation = Orientation.Horizontal,
                enabled = false,
            ),
            content = {
                Box(
                    modifier = Modifier
                        .layoutId(Position.Left)
                ) {
                    contentWithItems.left?.composable?.invoke()
                }

                Box(
                    modifier = Modifier
                        .layoutId(Position.Center)
                ) {
                    contentWithItems.center.composable.invoke()
                }

                Box(
                    modifier = Modifier
                        .layoutId(Position.Right)
                ) {
                    contentWithItems.right?.composable?.invoke()
                }
            }
        ) { measurables, constraints ->
            val offset = anchoredDraggableState.offset

            // calculating items size
            val itemWidth = (constraints.maxWidth - 2 * centerItemHorizontalPadding.toPx()).roundToInt()
            val constraintsOfItems = constraints.copy(maxWidth = itemWidth, minWidth = itemWidth)

            val centerPlaceable = measurables.first { it.layoutId == Position.Center }.measure(constraintsOfItems)
            val leftPlaceable = measurables.first { it.layoutId == Position.Left }.measure(constraintsOfItems)
            val rightPlaceable = measurables.first { it.layoutId == Position.Right }.measure(constraintsOfItems)

            val centerX = offset.roundToInt()

            layout(constraints.maxWidth, constraints.maxHeight) {
                //placing items
                leftPlaceable.place(
                    x = centerX - itemSpacing.roundToPx() - leftPlaceable.width,
                    y = 0,
                )
                rightPlaceable.place(
                    x = centerX + itemSpacing.roundToPx() + centerPlaceable.width,
                    y = 0,
                )
                centerPlaceable.place(
                    x = centerX,
                    y = 0,
                )
            }
        }
    }
}

@Immutable
internal data class CarouselContent<T>(
    val left: T?,
    val center: T,
    val right: T?
)

// helper class to save data and compose functions corresponding to this data in order to reuse them after swipes
@Immutable
private class ContentWithComposables<T>(
    val left: Item<T>?,
    val center: Item<T>,
    val right: Item<T>?
) {

    class Item<T>(
        val data: T,
        val composable: @Composable () -> Unit
    )

    fun sameContent(content: CarouselContent<T>): Boolean {
        return content.left == left?.data && content.center == center.data && content.right == right?.data
    }

    companion object {

        fun <T> from(
            item: @Composable (item: T) -> Unit,
            content: CarouselContent<T>,
        ): ContentWithComposables<T> {

            return ContentWithComposables(
                left = content.left?.let { Item(it, movableContentOf { item(it) }) },
                center = Item(content.center, movableContentOf { item(content.center) }),
                right = content.right?.let { Item(it, movableContentOf { item(it) }) },
            )
        }

        fun <T> basedOnPrevious(
            item: @Composable (item: T) -> Unit,
            content: CarouselContent<T>,
            previous: ContentWithComposables<T>,
        ): Pair<SwipeDirection?, ContentWithComposables<T>> {
            val direction = when {
                previous.center.data == content.right && previous.left?.data == content.center -> SwipeDirection.Backward
                previous.center.data == content.left && previous.right?.data == content.center -> SwipeDirection.Forward
                else -> null
            }
            val newContent = when (direction) {
                SwipeDirection.Forward -> {
                    ContentWithComposables(
                        left = previous.center,
                        center = previous.right!!, // cannot be null because of direction initializing conditions
                        right = content.right?.let { Item(it, movableContentOf { item(it) }) },
                    )
                }

                SwipeDirection.Backward -> {
                    ContentWithComposables(
                        left = content.left?.let { Item(it, movableContentOf { item(it) }) },
                        center = previous.left!!, // cannot be null because of direction initializing conditions
                        right = previous.center,
                    )
                }

                else -> {
                    from(item, content)
                }
            }
            return direction to newContent
        }
    }
}

internal enum class Position { Left, Center, Right }

@Preview(widthDp = 360, heightDp = 250)
@Composable
fun CarouselPreview(
    modifier: Modifier = Modifier
) {
    val list = remember {
        listOf(
            Color.DarkGray,
            Color.Yellow,
            Color.Green,
            Color.Magenta,
            Color.Blue,
            Color.Cyan,
            Color.Red,
            Color.White
        )
    }
    var idx by remember { mutableStateOf(3) }
    Surface(color = MaterialTheme.colors.background) {
        Column(
            modifier
                .wrapContentSize()
                .requiredSize(width = 360.dp, height = 250.dp)
        ) {
            Text("Current index = $idx")
            Carousel(
                centerItemHorizontalPadding = 40.dp,
                itemSpacing = 10.dp,
                content = CarouselContent(
                    left = list.getOrNull(idx - 1),
                    center = list[idx],
                    right = list.getOrNull(idx + 1),
                ),
                modifier = Modifier.weight(0.8f)
            ) { color ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Button(
                    onClick = { idx = (idx - 1).coerceAtLeast(0) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 5.dp)
                ) {
                    Text("prev")
                }
                Button(
                    onClick = { idx = Random.nextInt(list.size) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 5.dp)
                ) {
                    Text("random")
                }
                Button(
                    onClick = { idx = (idx + 1).coerceAtMost(list.size - 1) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 5.dp)
                ) {
                    Text("next")
                }
            }
        }
    }
}

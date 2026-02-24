package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.PositionIndicatorState
import androidx.wear.compose.material.PositionIndicatorVisibility

/**
 * Position indicator that remains visible while the list can scroll.
 */
@Composable
fun AlwaysOnScalingPositionIndicator(
    listState: ScalingLazyListState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.onBackground,
) {
    val state = remember(listState) { AlwaysOnScalingIndicatorState(listState) }
    PositionIndicator(
        state = state,
        indicatorHeight = 50.dp,
        indicatorWidth = 4.dp,
        paddingHorizontal = 2.dp,
        modifier = modifier,
        color = color,
        background = color.copy(alpha = 0.28f),
        fadeInAnimationSpec = snap(),
        fadeOutAnimationSpec = snap(),
        positionAnimationSpec = snap(),
    )
}

private class AlwaysOnScalingIndicatorState(
    private val listState: ScalingLazyListState,
) : PositionIndicatorState {

    override val positionFraction: Float
        get() {
            val total = listState.layoutInfo.totalItemsCount
            if (total <= 1) return 0f
            val centered = listState.centerItemIndex.coerceIn(0, total - 1)
            return centered.toFloat() / (total - 1).toFloat()
        }

    override fun sizeFraction(scrollableContainerSizePx: Float): Float {
        val layoutInfo = listState.layoutInfo
        val total = layoutInfo.totalItemsCount
        if (total <= 0) return 1f
        val visible = layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        return (visible.toFloat() / total.toFloat()).coerceIn(0.08f, 1f)
    }

    override fun visibility(scrollableContainerSizePx: Float): PositionIndicatorVisibility {
        val layoutInfo = listState.layoutInfo
        val total = layoutInfo.totalItemsCount
        val visible = layoutInfo.visibleItemsInfo.size
        return if (total > visible && total > 1) {
            PositionIndicatorVisibility.Show
        } else {
            PositionIndicatorVisibility.Hide
        }
    }
}

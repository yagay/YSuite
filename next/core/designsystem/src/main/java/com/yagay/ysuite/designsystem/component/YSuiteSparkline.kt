package com.yagay.ysuite.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun YSuiteSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    val lineColor =
        MaterialTheme.colorScheme.primary
    val guideColor =
        MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp),
    ) {
        drawLine(
            color = guideColor,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1f,
        )
        if (values.size < 2) {
            return@Canvas
        }
        val maxIndex =
            (values.size - 1).coerceAtLeast(1)
        var previous =
            point(
                index = 0,
                maxIndex = maxIndex,
                value = values.first(),
            )
        values.drop(1).forEachIndexed {
                index,
                value,
                ->
            val next =
                point(
                    index = index + 1,
                    maxIndex = maxIndex,
                    value = value,
                )
            drawLine(
                color = lineColor,
                start = previous,
                end = next,
                strokeWidth = 3f,
            )
            previous = next
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope
    .point(
        index: Int,
        maxIndex: Int,
        value: Float,
    ): Offset =
    Offset(
        x =
            size.width *
                index.toFloat() /
                maxIndex.toFloat(),
        y =
            size.height *
                (
                    1f -
                        value.coerceIn(
                            0f,
                            100f,
                        ) / 100f
                    ),
    )

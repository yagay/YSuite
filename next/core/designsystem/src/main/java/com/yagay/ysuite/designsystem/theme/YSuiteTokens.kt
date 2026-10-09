package com.yagay.ysuite.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object YSuiteSpacing {
    val XSmall = 4.dp
    val Small = 6.dp
    val Medium = 12.dp
    val Large = 16.dp
    val XLarge = 24.dp
}

val YSuiteShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(20.dp),
    )

private val MaterialTypography = Typography()

val YSuiteTypography =
    MaterialTypography.copy(
        titleLarge =
            MaterialTypography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        titleMedium =
            MaterialTypography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        labelLarge =
            MaterialTypography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
            ),
    )

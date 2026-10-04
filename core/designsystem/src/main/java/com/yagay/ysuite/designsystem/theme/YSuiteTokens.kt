package com.yagay.ysuite.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object YSuiteSpacing {
    val XXSmall = 2.dp
    val XSmall = 6.dp
    val Small = 10.dp
    val Medium = 16.dp
    val Large = 24.dp
    val XLarge = 32.dp
    val XXLarge = 48.dp
}

val YSuiteShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val YSuiteTypography = Typography(
    headlineLarge = Typography().headlineLarge.copy(
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = Typography().headlineMedium.copy(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = Typography().headlineSmall.copy(
        fontSize = 23.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = Typography().titleLarge.copy(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = Typography().titleMedium.copy(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelLarge = Typography().labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
    ),
)

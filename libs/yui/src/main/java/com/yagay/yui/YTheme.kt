package com.yagay.yui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// AndroidX Material 3 owns component corner shapes. No second per-feature shape system.
private val YShapes = Shapes()

private val YLightColors = lightColorScheme(
    primary = YUiPalette.LightPrimary,
    onPrimary = YUiPalette.LightOnPrimary,
    primaryContainer = YUiPalette.LightPrimaryContainer,
    onPrimaryContainer = YUiPalette.LightOnPrimaryContainer,
    secondary = YUiPalette.LightSecondary,
    onSecondary = YUiPalette.LightOnSecondary,
    secondaryContainer = YUiPalette.LightSecondaryContainer,
    onSecondaryContainer = YUiPalette.LightOnSecondaryContainer,
    background = YUiPalette.LightBackground,
    onBackground = YUiPalette.LightOnBackground,
    surface = YUiPalette.LightSurface,
    onSurface = YUiPalette.LightOnSurface,
    surfaceVariant = YUiPalette.LightSurfaceVariant,
    onSurfaceVariant = YUiPalette.LightOnSurfaceVariant,
    outline = YUiPalette.LightOutline,
    // Never fall back to Material 3's default purple/error/tertiary/surface roles.
    tertiary = YUiPalette.LightTertiary,
    onTertiary = YUiPalette.LightOnTertiary,
    tertiaryContainer = YUiPalette.LightTertiaryContainer,
    onTertiaryContainer = YUiPalette.LightOnTertiaryContainer,
    error = YUiPalette.LightError,
    onError = YUiPalette.LightOnError,
    errorContainer = YUiPalette.LightErrorContainer,
    onErrorContainer = YUiPalette.LightOnErrorContainer,
    outlineVariant = YUiPalette.LightOutlineVariant,
    surfaceTint = YUiPalette.LightPrimary,
    inverseSurface = YUiPalette.DarkSurface,
    inverseOnSurface = YUiPalette.DarkOnSurface,
    inversePrimary = YUiPalette.DarkPrimary,
    surfaceDim = lerp(YUiPalette.LightSurface, YUiPalette.LightSurfaceVariant, 0.25f),
    surfaceBright = YUiPalette.LightSurface,
    surfaceContainerLowest = YUiPalette.LightSurface,
    surfaceContainerLow = lerp(YUiPalette.LightSurface, YUiPalette.LightSurfaceVariant, 0.16f),
    surfaceContainer = lerp(YUiPalette.LightSurface, YUiPalette.LightSurfaceVariant, 0.26f),
    surfaceContainerHigh = lerp(YUiPalette.LightSurface, YUiPalette.LightSurfaceVariant, 0.38f),
    surfaceContainerHighest = lerp(YUiPalette.LightSurface, YUiPalette.LightSurfaceVariant, 0.55f),
    primaryFixed = YUiPalette.LightPrimaryContainer,
    primaryFixedDim = YUiPalette.DarkPrimary,
    onPrimaryFixed = YUiPalette.LightOnPrimaryContainer,
    onPrimaryFixedVariant = YUiPalette.LightPrimary,
    secondaryFixed = YUiPalette.LightSecondaryContainer,
    secondaryFixedDim = YUiPalette.DarkSecondary,
    onSecondaryFixed = YUiPalette.LightOnSecondaryContainer,
    onSecondaryFixedVariant = YUiPalette.LightSecondary,
    tertiaryFixed = YUiPalette.LightTertiaryContainer,
    tertiaryFixedDim = YUiPalette.DarkTertiary,
    onTertiaryFixed = YUiPalette.LightOnTertiaryContainer,
    onTertiaryFixedVariant = YUiPalette.LightTertiary,
)

private val YDarkColors = darkColorScheme(
    primary = YUiPalette.DarkPrimary,
    onPrimary = YUiPalette.DarkOnPrimary,
    primaryContainer = YUiPalette.DarkPrimaryContainer,
    onPrimaryContainer = YUiPalette.DarkOnPrimaryContainer,
    secondary = YUiPalette.DarkSecondary,
    onSecondary = YUiPalette.DarkOnSecondary,
    secondaryContainer = YUiPalette.DarkSecondaryContainer,
    onSecondaryContainer = YUiPalette.DarkOnSecondaryContainer,
    background = YUiPalette.DarkBackground,
    onBackground = YUiPalette.DarkOnBackground,
    surface = YUiPalette.DarkSurface,
    onSurface = YUiPalette.DarkOnSurface,
    surfaceVariant = YUiPalette.DarkSurfaceVariant,
    onSurfaceVariant = YUiPalette.DarkOnSurfaceVariant,
    outline = YUiPalette.DarkOutline,
    // Never fall back to Material 3's default purple/error/tertiary/surface roles.
    tertiary = YUiPalette.DarkTertiary,
    onTertiary = YUiPalette.DarkOnTertiary,
    tertiaryContainer = YUiPalette.DarkTertiaryContainer,
    onTertiaryContainer = YUiPalette.DarkOnTertiaryContainer,
    error = YUiPalette.DarkError,
    onError = YUiPalette.DarkOnError,
    errorContainer = YUiPalette.DarkErrorContainer,
    onErrorContainer = YUiPalette.DarkOnErrorContainer,
    outlineVariant = YUiPalette.DarkOutlineVariant,
    surfaceTint = YUiPalette.DarkPrimary,
    inverseSurface = YUiPalette.LightSurface,
    inverseOnSurface = YUiPalette.LightOnSurface,
    inversePrimary = YUiPalette.LightPrimary,
    surfaceDim = YUiPalette.DarkSurface,
    surfaceBright = lerp(YUiPalette.DarkSurface, YUiPalette.DarkSurfaceVariant, 0.60f),
    surfaceContainerLowest = YUiPalette.DarkSurface,
    surfaceContainerLow = lerp(YUiPalette.DarkSurface, YUiPalette.DarkSurfaceVariant, 0.16f),
    surfaceContainer = lerp(YUiPalette.DarkSurface, YUiPalette.DarkSurfaceVariant, 0.26f),
    surfaceContainerHigh = lerp(YUiPalette.DarkSurface, YUiPalette.DarkSurfaceVariant, 0.38f),
    surfaceContainerHighest = lerp(YUiPalette.DarkSurface, YUiPalette.DarkSurfaceVariant, 0.55f),
    primaryFixed = YUiPalette.LightPrimaryContainer,
    primaryFixedDim = YUiPalette.DarkPrimary,
    onPrimaryFixed = YUiPalette.LightOnPrimaryContainer,
    onPrimaryFixedVariant = YUiPalette.LightPrimary,
    secondaryFixed = YUiPalette.LightSecondaryContainer,
    secondaryFixedDim = YUiPalette.DarkSecondary,
    onSecondaryFixed = YUiPalette.LightOnSecondaryContainer,
    onSecondaryFixedVariant = YUiPalette.LightSecondary,
    tertiaryFixed = YUiPalette.LightTertiaryContainer,
    tertiaryFixedDim = YUiPalette.DarkTertiary,
    onTertiaryFixed = YUiPalette.LightOnTertiaryContainer,
    onTertiaryFixedVariant = YUiPalette.LightTertiary,
)

// Keep upstream Material 3 typography unmodified so every feature has matching text metrics.
private val YTypography = Typography()

@Composable
fun YTheme(
    dynamicColor: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // A deterministic shared palette is essential for matching Compose and legacy View screens.
    // Android dynamic colors remain available only via an explicit opt-in.
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        darkTheme -> YDarkColors
        else -> YLightColors
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = YTypography,
        shapes = YShapes,
    ) {
        CompositionLocalProvider(
            LocalYSemanticColors provides if (darkTheme) YSemanticPalette.Dark else YSemanticPalette.Light,
            content = content,
        )
    }
}

/** Material 3 app bar selected by the current page template. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YTopBar(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    when (LocalYPageRole.current.template().topBarStyle) {
        YTopBarStyle.PROMINENT -> MediumTopAppBar(
            title = { YTopBarCopy(title, subtitle) },
            actions = actions,
            colors = yTopBarColors(),
        )
        YTopBarStyle.COMPACT -> TopAppBar(
            title = { YTopBarCopy(title, subtitle) },
            actions = actions,
            colors = yTopBarColors(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YCustomTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = yTopBarColors(),
    )
}

/** Single color policy for the suite host and all product workspaces. */
@Composable
private fun yTopBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)

@Composable
private fun YTopBarCopy(title: String, subtitle: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Universal normal-screen container used by YUI pages and product-specific workspaces.
 * Insets, background and edge-to-edge behavior must not be reimplemented in features.
 */
@Composable
fun YUiScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing.only(
        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
    ),
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        contentWindowInsets = contentWindowInsets,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

@Composable
fun YScaffold(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    YUiScaffold(
        modifier = modifier,
        topBar = { YTopBar(title = title, subtitle = subtitle, actions = actions) },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

@Composable
fun YCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(YDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        ) { content() }
    }
}

@Composable
fun YPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    YUiButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun YSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    YUiOutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun YSectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun YEmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(vertical = YDimens.ScreenVertical),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

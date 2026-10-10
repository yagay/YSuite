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
        )
        YTopBarStyle.COMPACT -> TopAppBar(
            title = { YTopBarCopy(title, subtitle) },
            actions = actions,
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
    )
}

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
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        topBar = { YTopBar(title = title, subtitle = subtitle, actions = actions) },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background,
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
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = YDimens.ButtonVisualHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = YDimens.ButtonPaddingHorizontal, vertical = YDimens.ButtonPaddingVertical),
    ) { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
fun YSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = YDimens.ButtonVisualHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(),
        contentPadding = PaddingValues(horizontal = YDimens.ButtonPaddingHorizontal, vertical = YDimens.ButtonPaddingVertical),
    ) { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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

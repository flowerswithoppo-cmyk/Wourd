package com.wourd.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

private fun lightScheme(): ColorScheme = lightColorScheme(
    primary = OrangePrimary,
    secondary = OrangeSecondary,
    tertiary = OrangeSecondary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
)

private fun darkScheme(): ColorScheme = darkColorScheme(
    primary = OrangePrimary,
    secondary = OrangeSecondary,
    tertiary = OrangeSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
)

@Composable
fun WourdTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val target = if (darkTheme) darkScheme() else lightScheme()

    // Smooth scheme transition for the most noticeable fields
    val primary by animateColorAsState(target.primary, label = "primary")
    val secondary by animateColorAsState(target.secondary, label = "secondary")
    val background by animateColorAsState(target.background, label = "background")
    val surface by animateColorAsState(target.surface, label = "surface")
    val onPrimary by animateColorAsState(target.onPrimary, label = "onPrimary")
    val onBackground by animateColorAsState(target.onBackground, label = "onBackground")
    val onSurface by animateColorAsState(target.onSurface, label = "onSurface")

    val animated = target.copy(
        primary = primary,
        secondary = secondary,
        background = background,
        surface = surface,
        onPrimary = onPrimary,
        onBackground = onBackground,
        onSurface = onSurface,
    )

    MaterialTheme(
        colorScheme = animated,
        typography = WourdTypography,
        content = content,
    )
}


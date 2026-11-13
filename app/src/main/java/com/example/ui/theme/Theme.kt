package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
  darkColorScheme(
    primary = LagoSkyDark,
    onPrimary = Color(0xFF002533),
    primaryContainer = Color(0xFF0B4F6C),
    onPrimaryContainer = Color(0xFFD0ECF7),
    secondary = SolGoldDark,
    onSecondary = Color(0xFF3A2200),
    secondaryContainer = Color(0xFF5C3A00),
    onSecondaryContainer = SolAmberSoft,
    tertiary = Color(0xFF34D399),
    background = SlateDarkBg,
    onBackground = Color(0xFFF1F5F9),
    surface = SlateDarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = SlateDarkVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    error = Color(0xFFF87171),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LagoDeepTeal,
    onPrimary = Color.White,
    primaryContainer = LagoContainerLight,
    onPrimaryContainer = LagoOnContainerLight,
    secondary = SolMarabinoGold,
    onSecondary = Color.White,
    secondaryContainer = SolAmberSoft,
    onSecondaryContainer = SolOnContainer,
    tertiary = SolventeGreen,
    onTertiary = Color.White,
    background = SandstoneBgLight,
    onBackground = SlateInk,
    surface = SandstoneSurfaceLight,
    onSurface = SlateInk,
    surfaceVariant = SandstoneVariantLight,
    onSurfaceVariant = SlateMuted,
    outline = Color(0xFFD5CEC2),
    error = MoraRed,
    onError = Color.White,
  )

val CondoShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
  )

@Composable
fun CondoZuliaTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = CondoTypography,
    shapes = CondoShapes,
    content = content,
  )
}

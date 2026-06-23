package dev.ipf.darkmatter.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun ColorScheme.isAmoledSurfaceTheme(): Boolean =
    surface == Color.Black &&
        surfaceVariant == Color.Black &&
        surfaceContainer == Color.Black &&
        surfaceContainerHigh == Color.Black &&
        surfaceContainerHighest == Color.Black &&
        surfaceTint == Color.Transparent

@Composable
internal fun amoledSurfaceBorderStroke(width: Dp = 1.dp): BorderStroke? {
    val colorScheme = MaterialTheme.colorScheme
    return if (colorScheme.isAmoledSurfaceTheme()) {
        BorderStroke(width, colorScheme.outlineVariant)
    } else {
        null
    }
}

@Composable
internal fun Modifier.amoledSurfaceBorder(
    shape: Shape,
    width: Dp = 1.dp,
): Modifier {
    val colorScheme = MaterialTheme.colorScheme
    return if (colorScheme.isAmoledSurfaceTheme()) {
        border(BorderStroke(width, colorScheme.outlineVariant), shape)
    } else {
        this
    }
}

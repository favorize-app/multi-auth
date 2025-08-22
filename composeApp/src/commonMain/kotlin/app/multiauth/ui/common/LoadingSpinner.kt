package app.multiauth.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LoadingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: androidx.compose.ui.graphics.Color? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(size),
            color = color ?: MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp
        )
    }
}

@Composable
fun LoadingSpinnerLarge(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null
) {
    LoadingSpinner(
        modifier = modifier,
        size = 48.dp,
        color = color
    )
}

@Composable
fun LoadingSpinnerSmall(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null
) {
    LoadingSpinner(
        modifier = modifier,
        size = 16.dp,
        color = color
    )
}
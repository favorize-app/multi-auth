package app.multiauth.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ErrorMessage(
    message: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.errorContainer,
    textColor: Color = MaterialTheme.colorScheme.onErrorContainer
) {
    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = message,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun WarningMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    ErrorMessage(
        message = message,
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
        textColor = MaterialTheme.colorScheme.onTertiaryContainer
    )
}

@Composable
fun InfoMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    ErrorMessage(
        message = message,
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
        textColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
}

@Composable
fun SuccessMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    ErrorMessage(
        message = message,
        modifier = modifier,
        backgroundColor = androidx.compose.ui.graphics.Color(0xFFE8F5E8),
        textColor = androidx.compose.ui.graphics.Color(0xFF2E7D32)
    )
}
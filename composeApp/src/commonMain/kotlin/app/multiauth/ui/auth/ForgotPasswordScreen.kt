package app.multiauth.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.multiauth.ui.common.EmailField
import app.multiauth.ui.common.ErrorMessage
import app.multiauth.ui.common.PrimaryButton
import app.multiauth.ui.common.SuccessMessage
import app.multiauth.ui.common.TextButton

@Composable
fun ForgotPasswordScreen(
    onResetPasswordClick: (String) -> Unit,
    onBackToLoginClick: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    successMessage: String? = null,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header
            Text(
                text = "Reset Password",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Enter your email address and we'll send you a link to reset your password",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Success Message
            if (successMessage != null) {
                SuccessMessage(
                    message = successMessage,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Error Message
            if (errorMessage != null) {
                ErrorMessage(
                    message = errorMessage,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Email Field
            EmailField(
                value = email,
                onValueChange = { 
                    email = it
                    emailError = null
                },
                isError = emailError != null,
                errorMessage = emailError,
                enabled = !isLoading
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Reset Password Button
            PrimaryButton(
                onClick = {
                    // Validate inputs
                    if (email.isBlank()) {
                        emailError = "Email is required"
                    } else if (!isValidEmail(email)) {
                        emailError = "Please enter a valid email"
                    } else {
                        onResetPasswordClick(email)
                    }
                },
                text = "Send Reset Link",
                isLoading = isLoading,
                enabled = email.isNotBlank()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Back to Login Link
            TextButton(
                onClick = onBackToLoginClick,
                text = "Back to Sign In",
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

private fun isValidEmail(email: String): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}
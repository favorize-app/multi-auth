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
import app.multiauth.ui.common.Button
import app.multiauth.ui.common.EmailField
import app.multiauth.ui.common.ErrorMessage
import app.multiauth.ui.common.PasswordField
import app.multiauth.ui.common.PrimaryButton
import app.multiauth.ui.common.TextButton

@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    onForgotPasswordClick: () -> Unit,
    onRegisterClick: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    
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
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Sign in to your account",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Password Field
            PasswordField(
                value = password,
                onValueChange = { 
                    password = it
                    passwordError = null
                },
                isError = passwordError != null,
                errorMessage = passwordError,
                enabled = !isLoading
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Forgot Password Link
            TextButton(
                onClick = onForgotPasswordClick,
                text = "Forgot Password?",
                modifier = Modifier.align(Alignment.End)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Login Button
            PrimaryButton(
                onClick = {
                    // Validate inputs
                    var hasError = false
                    
                    if (email.isBlank()) {
                        emailError = "Email is required"
                        hasError = true
                    } else if (!isValidEmail(email)) {
                        emailError = "Please enter a valid email"
                        hasError = true
                    }
                    
                    if (password.isBlank()) {
                        passwordError = "Password is required"
                        hasError = true
                    }
                    
                    if (!hasError) {
                        onLoginClick(email, password)
                    }
                },
                text = "Sign In",
                isLoading = isLoading,
                enabled = email.isNotBlank() && password.isNotBlank()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Register Link
            TextButton(
                onClick = onRegisterClick,
                text = "Don't have an account? Sign Up",
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

private fun isValidEmail(email: String): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}
package app.multiauth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import app.multiauth.ui.auth.ForgotPasswordScreen
import app.multiauth.ui.auth.LoginScreen
import app.multiauth.ui.auth.RegisterScreen
import app.multiauth.ui.oauth.OAuthProvider
import app.multiauth.ui.oauth.OAuthProviderSelection
import app.multiauth.ui.theme.MultiAuthTheme
import app.multiauth.util.Logger

enum class AuthScreen {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD
}

@Composable
fun MultiAuthApp() {
    MultiAuthTheme {
        var currentScreen by remember { mutableStateOf(AuthScreen.LOGIN) }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var successMessage by remember { mutableStateOf<String?>(null) }
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (currentScreen) {
                AuthScreen.LOGIN -> {
                    LoginScreen(
                        onLoginClick = { email, password ->
                            // Simulate login
                            isLoading = true
                            errorMessage = null
                            
                            // In a real app, this would call the AuthEngine
                            // For demo purposes, we'll just simulate a delay
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                kotlinx.coroutines.delay(2000)
                                isLoading = false
                                errorMessage = "Invalid email or password"
                            }
                        },
                        onForgotPasswordClick = {
                            currentScreen = AuthScreen.FORGOT_PASSWORD
                        },
                        onRegisterClick = {
                            currentScreen = AuthScreen.REGISTER
                        },
                        isLoading = isLoading,
                        errorMessage = errorMessage
                    )
                }
                
                AuthScreen.REGISTER -> {
                    RegisterScreen(
                        onRegisterClick = { displayName, email, password ->
                            // Simulate registration
                            isLoading = true
                            errorMessage = null
                            
                            // In a real app, this would call the AuthEngine
                            // For demo purposes, we'll just simulate a delay
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                kotlinx.coroutines.delay(2000)
                                isLoading = false
                                errorMessage = "Email already exists"
                            }
                        },
                        onLoginClick = {
                            currentScreen = AuthScreen.LOGIN
                        },
                        isLoading = isLoading,
                        errorMessage = errorMessage
                    )
                }
                
                AuthScreen.FORGOT_PASSWORD -> {
                    ForgotPasswordScreen(
                        onResetPasswordClick = { email ->
                            // Simulate password reset
                            isLoading = true
                            errorMessage = null
                            successMessage = null
                            
                            // In a real app, this would call the EmailProvider
                            // For demo purposes, we'll just simulate a delay
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                kotlinx.coroutines.delay(2000)
                                isLoading = false
                                successMessage = "Password reset link sent to $email"
                            }
                        },
                        onBackToLoginClick = {
                            currentScreen = AuthScreen.LOGIN
                        },
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        successMessage = successMessage
                    )
                }
            }
        }
    }
}

@Composable
fun OAuthDemo() {
    MultiAuthTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "OAuth Provider Selection",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                OAuthProviderSelection(
                    onProviderSelected = { provider ->
                        // Handle OAuth provider selection
                        // In a real app, this would call the OAuthManager
                        Logger.info("OAuth", "Selected OAuth provider: ${provider.displayName}")
                    }
                )
            }
        }
    }
}
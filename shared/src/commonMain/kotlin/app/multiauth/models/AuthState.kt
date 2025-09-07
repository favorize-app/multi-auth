package app.multiauth.models

sealed class AuthState {
    object Initial : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: User, val tokens: TokenPair) : AuthState()
    data class VerificationRequired(
        val method: VerificationMethod,
        val user: User? = null
    ) : AuthState()
    data class Error(val error: AuthError, val previousState: AuthState) : AuthState()
}

sealed class VerificationMethod {
    data class Email(val email: String) : VerificationMethod()
    data class Phone(val phoneNumber: String) : VerificationMethod()
    data class TwoFactor(val method: String) : VerificationMethod()
}

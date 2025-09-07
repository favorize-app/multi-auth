package app.multiauth.events

// UserSession will be imported when needed to avoid circular dependencies
import app.multiauth.models.AuthState
import app.multiauth.models.UserPreferences
import app.multiauth.models.AuthError
import app.multiauth.models.TokenPair
import app.multiauth.oauth.OAuthProvider
import app.multiauth.auth.AnonymousUser
import app.multiauth.biometric.BiometricType
import app.multiauth.models.User
import app.multiauth.mfa.MfaMethod

sealed interface AuthEvent

sealed interface Authentication : AuthEvent {
    data object SignInRequested : Authentication
    data object SignUpRequested : Authentication
    data object SignOutRequested : Authentication
    data object SignOutCompleted : Authentication
    data class SignInCompleted(val user: User, val tokens: TokenPair) : Authentication
    data class SignUpCompleted(val user: User, val tokens: TokenPair) : Authentication
    data class SignInFailed(val error: AuthError) : Authentication
    data class SignUpFailed(val error: AuthError) : Authentication
    data class SignOutFailed(val error: AuthError) : Authentication
}

sealed interface OAuth : AuthEvent {
    data class OAuthFlowStarted(val provider: OAuthProvider, val state: String) : OAuth
    data class OAuthFlowCompleted(val provider: OAuthProvider, val user: User, val tokens: TokenPair) : OAuth
    data class OAuthFlowFailed(val provider: OAuthProvider, val error: AuthError) : OAuth
    data class AccountLinked(val user: User, val provider: OAuthProvider) : OAuth
    data class AccountUnlinked(val user: User, val provider: OAuthProvider) : OAuth
    data class AccountLinkFailed(val user: User, val provider: OAuthProvider, val error: AuthError) : OAuth
    data class AccountUnlinkFailed(val user: User, val provider: OAuthProvider, val error: AuthError) : OAuth
    data class OAuthTokenRefreshCompleted(val provider: OAuthProvider, val tokens: TokenPair) : OAuth
    data class OAuthTokenRefreshFailed(val provider: OAuthProvider, val error: AuthError) : OAuth
}

sealed interface Verification : AuthEvent {
    data class EmailVerificationCodeSent(val email: String) : Verification
    data class PhoneVerificationRequested(val phoneNumber: String) : Verification
    data class PhoneVerificationCodeSent(val phoneNumber: String) : Verification
    data class PhoneVerificationCompleted(val phoneNumber: String) : Verification
    data class PhoneVerificationFailed(val error: AuthError) : Verification
}

sealed interface Validation : AuthEvent {
    data class TokenValidationCompleted(val token: String, val isValid: Boolean, val metadata: Map<String, String> = emptyMap()) : Validation
    data class PermissionValidationCompleted(val userId: String, val permission: String, val granted: Boolean, val metadata: Map<String, String> = emptyMap()) : Validation
    data class EmailValidationCompleted(val email: String, val isValid: Boolean, val metadata: Map<String, String> = emptyMap()) : Validation
    data class PhoneValidationCompleted(val phoneNumber: String, val isValid: Boolean, val metadata: Map<String, String> = emptyMap()) : Validation
    data class PasswordValidationCompleted(val strength: String, val isValid: Boolean, val metadata: Map<String, String> = emptyMap()) : Validation
}

sealed interface Session : AuthEvent {
    data class Created(val sessionId: String, val userId: String) : Session
    data class SessionExpired(val sessionId: String, val userId: String) : Session  
    data class SessionRefreshed(val sessionId: String, val userId: String) : Session
    data class SessionRefreshFailed(val error: AuthError) : Session
    data class TokensRefreshed(val tokens: TokenPair, val sessionId: String? = null) : Session
    data class Error(val error: AuthError) : Session
    data class SessionError(val error: AuthError) : Session
}

sealed interface Anonymous : AuthEvent {
    data class AnonymousSessionCreated(val user: User, val anonymousUser: AnonymousUser) : Anonymous
    data class AnonymousUserConverted(val anonymousUser: User, val permanentUser: User) : Anonymous
    data class AnonymousSessionExtended(val user: User, val additionalHours: Long) : Anonymous
    data class AnonymousSessionTerminated(val user: User) : Anonymous
}

sealed interface Biometric : AuthEvent {
    data class BiometricAvailable(val supportedTypes: List<BiometricType>) : Biometric
    data class BiometricAuthenticationCompleted(val user: User) : Biometric
    data class BiometricAuthenticationFailed(val error: Throwable) : Biometric
    data class BiometricEnabled(val user: User) : Biometric
    data class BiometricEnableFailed(val error: Throwable) : Biometric
    data class BiometricDisabled(val user: User) : Biometric
    data class BiometricDisableFailed(val error: Throwable) : Biometric
}

sealed interface Mfa : AuthEvent {
    data class MfaMethodEnabled(val user: User, val method: MfaMethod, val metadata: Map<String, String> = emptyMap()) : Mfa
    data class MfaMethodEnabledFailed(val user: User, val method: MfaMethod, val error: AuthError, val metadata: Map<String, String> = emptyMap()) : Mfa
    data class MfaMethodDisabled(val user: User, val method: MfaMethod, val metadata: Map<String, String> = emptyMap()) : Mfa
    data class MfaMethodDisabledFailed(val user: User, val method: MfaMethod, val error: AuthError, val metadata: Map<String, String> = emptyMap()) : Mfa
    data class MfaVerificationCompleted(val user: User, val method: MfaMethod, val metadata: Map<String, String> = emptyMap()) : Mfa
    data class MfaVerificationFailed(val user: User, val method: MfaMethod, val error: AuthError, val metadata: Map<String, String> = emptyMap()) : Mfa
    data class MfaBackupCodesGenerated(val user: User, val codes: List<String>, val metadata: Map<String, String> = emptyMap()) : Mfa
}

sealed interface State : AuthEvent {
    data class StateChanged(val previousState: AuthState, val newState: AuthState) : State
    data class OperationStarted(val operation: String) : State
    data class OperationCompleted(val operation: String) : State
    data class ErrorOccurred(val error: AuthError) : State
    data object ErrorCleared : State
    data class PreferencesUpdated(val preferences: UserPreferences) : State
    data object StateReset : State
}

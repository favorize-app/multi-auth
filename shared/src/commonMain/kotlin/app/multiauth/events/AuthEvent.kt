package app.multiauth.events

import kotlinx.datetime.Clock
import app.multiauth.models.*
import app.multiauth.oauth.OAuthProvider

/**
 * Base class for all authentication events.
 * All authentication operations are represented as events that can be dispatched and handled.
 */
sealed class AuthEvent {
    /**
     * Events related to user authentication.
     */
    sealed class Authentication : AuthEvent() {
        object SignInRequested : Authentication()
        data class SignInCompleted(val user: User, val tokens: TokenPair) : Authentication()
        data class SignInFailed(val error: AuthError) : Authentication()
        
        object SignUpRequested : Authentication()
        data class SignUpCompleted(val user: User, val tokens: TokenPair) : Authentication()
        data class SignUpFailed(val error: AuthError) : Authentication()
        
        object SignOutRequested : Authentication()
        object SignOutCompleted : Authentication()
        data class SignOutFailed(val error: AuthError) : Authentication()
        
        object RefreshTokenRequested : Authentication()
        data class RefreshTokenCompleted(val tokens: TokenPair) : Authentication()
        data class RefreshTokenFailed(val error: AuthError) : Authentication()
    }
    
    /**
     * Events related to user verification.
     */
    sealed class Verification : AuthEvent() {
        data class EmailVerificationRequested(val email: String) : Verification()
        data class EmailVerificationCodeSent(val email: String) : Verification()
        data class EmailVerificationCompleted(val email: String) : Verification()
        data class EmailVerificationFailed(val error: AuthError) : Verification()
        
        data class PhoneVerificationRequested(val phoneNumber: String) : Verification()
        data class PhoneVerificationCodeSent(val phoneNumber: String) : Verification()
        data class PhoneVerificationCompleted(val phoneNumber: String) : Verification()
        data class PhoneVerificationFailed(val error: AuthError) : Verification()
        
        data class ResendVerificationRequested(val method: VerificationMethod) : Verification()
        data class ResendVerificationCompleted(val method: VerificationMethod) : Verification()
        data class ResendVerificationFailed(val error: AuthError) : Verification()
    }
    
    /**
     * Events related to password management.
     */
    sealed class Password : AuthEvent() {
        data class PasswordResetRequested(val email: String) : Password()
        data class PasswordResetEmailSent(val email: String) : Password()
        data class PasswordResetCompleted(val email: String) : Password()
        data class PasswordResetFailed(val error: AuthError) : Password()
        
        data class PasswordChangeRequested(val oldPassword: String, val newPassword: String) : Password()
        object PasswordChangeCompleted : Password()
        data class PasswordChangeFailed(val error: AuthError) : Password()
    }
    
    /**
     * Events related to OAuth authentication.
     */
    
    /**
     * Events related to user profile management.
     */
    sealed class Profile : AuthEvent() {
        data class ProfileUpdateRequested(val updateRequest: UpdateUserRequest) : Profile()
        data class ProfileUpdateCompleted(val user: User) : Profile()
        data class ProfileUpdateFailed(val error: AuthError) : Profile()
        
        object ProfileDeleteRequested : Profile()
        object ProfileDeleteCompleted : Profile()
        data class ProfileDeleteFailed(val error: AuthError) : Profile()
    }
    
    /**
     * Events related to session management.
     */
    sealed class Session : AuthEvent() {
        data class SessionCreated(val session: app.multiauth.core.UserSession) : Session()
        data class SessionUpdated(val session: app.multiauth.core.UserSession) : Session()
        data class SessionRefreshed(val session: app.multiauth.core.UserSession) : Session()
        data class SessionExpired(val session: app.multiauth.core.UserSession) : Session()
        data class SessionEnded(val session: app.multiauth.core.UserSession) : Session()
        data class SessionRefreshFailed(val error: AuthError) : Session()
        data class SessionInvalidated(val reason: String) : Session()
        data class TokensRefreshed(val tokens: TokenPair) : Session()
        
        object AutoSignInAttempted : Session()
        data class AutoSignInCompleted(val user: User, val tokens: TokenPair) : Session()
        data class AutoSignInFailed(val error: AuthError) : Session()
    }
    
    /**
     * Events related to error handling.
     */
    sealed class Error : AuthEvent() {
        data class ErrorOccurred(val error: AuthError) : Error()
        object ErrorCleared : Error()
        data class ErrorRetryRequested(val originalEvent: AuthEvent) : Error()
    }
    
    /**
     * Events related to validation.
     */
    sealed class Validation : AuthEvent() {
        data class TokenValidationCompleted(val token: String, val isValid: Boolean) : Validation()
        data class PermissionValidationCompleted(
            val userId: String,
            val permissions: List<String>,
            val hasPermission: Boolean
        ) : Validation()
        data class EmailValidationCompleted(val email: String, val isValid: Boolean) : Validation()
        data class PhoneValidationCompleted(val phoneNumber: String, val isValid: Boolean) : Validation()
        data class PasswordValidationCompleted(val password: String, val strength: app.multiauth.core.PasswordStrength) : Validation()
    }
    
    /**
     * Events related to anonymous authentication.
     */
    sealed class Anonymous : AuthEvent() {
        data class AnonymousSessionCreated(val user: User, val anonymousUser: app.multiauth.auth.AnonymousUser) : Anonymous()
        data class AnonymousUserConverted(val anonymousUser: User, val permanentUser: User) : Anonymous()
        data class AnonymousSessionExtended(val user: User, val additionalHours: Long) : Anonymous()
        data class AnonymousSessionTerminated(val user: User) : Anonymous()
        data class AnonymousSessionExpired(val user: User) : Anonymous()
        data class AnonymousActionFailed(val action: String, val error: AuthError) : Anonymous()
    }
    
    /**
     * Events related to state changes.
     */
    sealed class State : AuthEvent() {
        data class StateChanged(val from: AuthState, val to: AuthState) : State()
        object StateReset : State()
        data class OperationStarted(val operation: String) : State()
        data class OperationCompleted(val operation: String) : State()
        data class ErrorOccurred(val error: AuthError) : State()
        object ErrorCleared : State()
        data class PreferencesUpdated(val preferences: app.multiauth.core.UserPreferences) : State()
    }

    /**
     * Events related to OAuth authentication.
     */
    sealed class OAuth : AuthEvent() {
        data class OAuthFlowStarted(val provider: app.multiauth.oauth.OAuthProvider, val state: String) : OAuth()
        data class OAuthFlowCompleted(val provider: app.multiauth.oauth.OAuthProvider, val user: User, val tokens: TokenPair) : OAuth()
        data class OAuthFlowFailed(val provider: app.multiauth.oauth.OAuthProvider, val error: AuthError) : OAuth()
        
        data class AccountLinked(val user: User, val provider: app.multiauth.oauth.OAuthProvider) : OAuth()
        data class AccountLinkFailed(val user: User, val provider: app.multiauth.oauth.OAuthProvider, val error: AuthError) : OAuth()
        data class AccountUnlinked(val user: User, val provider: app.multiauth.oauth.OAuthProvider) : OAuth()
        data class AccountUnlinkFailed(val user: User, val provider: app.multiauth.oauth.OAuthProvider, val error: AuthError) : OAuth()
        
        data class OAuthTokenRefreshRequested(val provider: app.multiauth.oauth.OAuthProvider) : OAuth()
        data class OAuthTokenRefreshCompleted(val provider: app.multiauth.oauth.OAuthProvider, val tokens: TokenPair) : OAuth()
        data class OAuthTokenRefreshFailed(val provider: app.multiauth.oauth.OAuthProvider, val error: AuthError) : OAuth()
    }

    /**
     * Events related to biometric authentication.
     */
    sealed class Biometric : AuthEvent() {
        data class BiometricAvailable(val supportedTypes: List<app.multiauth.biometric.BiometricType>) : Biometric()
        data class BiometricAuthenticationCompleted(val user: app.multiauth.models.User) : Biometric()
        data class BiometricAuthenticationFailed(val error: Throwable) : Biometric()
        data class BiometricEnabled(val user: app.multiauth.models.User) : Biometric()
        data class BiometricEnableFailed(val error: Throwable) : Biometric()
        data class BiometricDisabled(val user: app.multiauth.models.User) : Biometric()
        data class BiometricDisableFailed(val error: Throwable) : Biometric()
    }
    
    /**
     * Events related to Multi-Factor Authentication (MFA).
     */
    sealed class Mfa : AuthEvent() {
        data class MfaMethodEnabled(val user: User, val method: app.multiauth.mfa.MfaMethod) : Mfa()
        data class MfaMethodDisabled(val user: User, val method: app.multiauth.mfa.MfaMethod) : Mfa()
        data class MfaMethodEnabledFailed(val user: User, val method: app.multiauth.mfa.MfaMethod, val error: AuthError) : Mfa()
        data class MfaMethodDisabledFailed(val user: User, val method: app.multiauth.mfa.MfaMethod, val error: AuthError) : Mfa()
        
        data class MfaVerificationRequested(val user: User, val method: app.multiauth.mfa.MfaMethod) : Mfa()
        data class MfaVerificationCompleted(val user: User, val method: app.multiauth.mfa.MfaMethod) : Mfa()
        data class MfaVerificationFailed(val user: User, val method: app.multiauth.mfa.MfaMethod, val error: AuthError) : Mfa()
        
        data class MfaBackupCodesGenerated(val user: User, val codes: List<String>) : Mfa()
        data class MfaBackupCodeUsed(val user: User, val code: String) : Mfa()
        data class MfaBackupCodeUsedFailed(val user: User, val code: String, val error: AuthError) : Mfa()
    }
}

/**
 * Event metadata for tracking and debugging purposes.
 */
data class EventMetadata(
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val correlationId: String? = null,
    val userId: String? = null,
    val sessionId: String? = null,
    val source: String? = null
)

/**
 * Wrapper for events with metadata.
 */
data class EventWithMetadata<T : AuthEvent>(
    val event: T,
    val metadata: EventMetadata
)
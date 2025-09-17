@file:OptIn(ExperimentalTime::class)

package app.multiauth.core


import app.multiauth.events.*
import app.multiauth.models.User
import app.multiauth.models.AuthError
import app.multiauth.models.AuthResult
import app.multiauth.models.AuthState
import app.multiauth.models.AuthMethod
import app.multiauth.models.TokenPair
import app.multiauth.models.VerificationMethod
import app.multiauth.providers.*
import app.multiauth.util.Logger
import app.multiauth.oauth.OAuthProvider
import app.multiauth.security.PasswordHasher
import app.multiauth.security.JwtTokenManager
import app.multiauth.security.RateLimiter
import app.multiauth.security.RateLimitResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import app.multiauth.util.TimeoutConstants
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Main authentication engine that orchestrates all authentication operations.
 * This is the primary interface for the multi-auth system.
 */
class AuthEngine private constructor(
    private val emailProvider: EmailProvider,
    private val smsProvider: SmsProvider,
    private val oauthProvider: OAuthProvider,
    private val eventBus: EventBus = EventBusInstance()
) {

    private val jwtTokenManager = JwtTokenManager()
    private val rateLimiter = RateLimiter()

    // Simple in-memory user storage for now (Phase 3 will add real database)
    private val userStorage = mutableMapOf<String, StoredUser>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Subscribe to authentication events
        subscribeToAuthEvents()
        Logger.info("AuthEngine", "AuthEngine initialized")
    }

    /**
     * Sign in with email and password.
     */
    suspend fun signInWithEmail(email: String, password: String): AuthResult<User> {
        Logger.debug("AuthEngine", "Sign in with email: $email")

        return try {
            _isLoading.value = true
            val metadata = EventMetadata(source = "AuthEngine")
            eventBus.dispatch(Authentication.SignInRequested, metadata)

            // Check rate limiting first
            when (val rateLimitResult = rateLimiter.checkRateLimit(email)) {
                is RateLimitResult.RateLimited -> {
                    val error = AuthError.RateLimitError(
                        "Too many failed attempts. Please try again in ${rateLimitResult.retryAfter.inWholeMinutes} minutes.",
                        rateLimitResult.retryAfter
                    )
                    eventBus.dispatch(Authentication.SignInFailed(error), metadata)
                    _isLoading.value = false
                    return AuthResult.Failure(error)
                }
                is RateLimitResult.Allowed -> {
                    Logger.debug("AuthEngine", "Rate limit check passed. ${rateLimitResult.attemptsRemaining} attempts remaining")
                }
            }

            // Validate email format
            val emailValidation = emailProvider.validateEmail(email)
            if (emailValidation.isFailure()) {
                val error = AuthError.ValidationError("Invalid email format", "email")
                eventBus.dispatch(Authentication.SignInFailed(error), metadata)
                _isLoading.value = false
                return AuthResult.Failure(error)
            }

            // Find user by email
            val storedUser = userStorage.values.find { it.user.email == email }
            if (storedUser == null) {
                // Record failed attempt for rate limiting (even for non-existent users)
                rateLimiter.recordFailedAttempt(email)

                val error = AuthError.ValidationError("User not found", "email")
                eventBus.dispatch(Authentication.SignInFailed(error), metadata)
                _isLoading.value = false
                return AuthResult.Failure(error)
            }

            // Verify password
            if (storedUser.passwordHash == null) {
                val error = AuthError.ValidationError("Password authentication not available for this user", "password")
                eventBus.dispatch(Authentication.SignInFailed(error), metadata)
                _isLoading.value = false
                return AuthResult.Failure(error)
            }

            if (!PasswordHasher.verifyPassword(password, storedUser.passwordHash)) {
                // Record failed attempt for rate limiting
                rateLimiter.recordFailedAttempt(email)

                val error = AuthError.ValidationError("Invalid password", "password")
                eventBus.dispatch(Authentication.SignInFailed(error), metadata)
                _isLoading.value = false
                return AuthResult.Failure(error)
            }

            // Record successful authentication to reset rate limiting
            rateLimiter.recordSuccessfulAttempt(email)

            // Create real JWT tokens
            val accessToken = jwtTokenManager.createAccessToken(storedUser.user.id, storedUser.user.email)
            val refreshToken = jwtTokenManager.createRefreshToken(storedUser.user.id)
            val tokens = TokenPair(accessToken, refreshToken, Clock.System.now() + 30.minutes)

            _authState.value = AuthState.Authenticated(storedUser.user, tokens)
            eventBus.dispatch(Authentication.SignInCompleted(storedUser.user, tokens), metadata)

            _isLoading.value = false
            AuthResult.Success(storedUser.user)

        } catch (e: Exception) {
            val metadata = EventMetadata(source = "AuthEngine")
            val error = AuthError.UnknownError("Sign in failed: ${e.message}", e)
            eventBus.dispatch(Authentication.SignInFailed(error), metadata)
            _authState.value = AuthState.Error(error, _authState.value)
            _isLoading.value = false
            AuthResult.Failure(error)
        }
    }

    /**
     * Sign up with email and password.
     */
    suspend fun signUpWithEmail(email: String, password: String, displayName: String? = null): AuthResult<User> {
        Logger.debug("AuthEngine", "Sign up with email: $email")

        return try {
            _isLoading.value = true
            val metadata = EventMetadata(source = "AuthEngine")
            eventBus.dispatch(Authentication.SignUpRequested, metadata)

            // Validate email format
            val emailValidation = emailProvider.validateEmail(email)
            if (emailValidation.isFailure()) {
                val error = AuthError.ValidationError("Invalid email format", "email")
                eventBus.dispatch(Authentication.SignUpFailed(error), metadata)
                _isLoading.value = false
                return AuthResult.Failure(error)
            }

            // Check if user already exists
            if (userStorage.values.any { it.user.email == email }) {
                val error = AuthError.ValidationError("User with this email already exists", "email")
                eventBus.dispatch(Authentication.SignUpFailed(error), metadata)
                _isLoading.value = false
                return AuthResult.Failure(error)
            }

            // Hash password securely
            val hashedPassword = PasswordHasher.hashPassword(password)

            // Create new user
            val user = createNewUser(email, displayName, AuthMethod.Email(email, false, null))
            val storedUser = StoredUser(user, hashedPassword)
            userStorage[user.id] = storedUser

            // Create real JWT tokens
            val accessToken = jwtTokenManager.createAccessToken(user.id, user.email)
            val refreshToken = jwtTokenManager.createRefreshToken(user.id)
            val tokens = TokenPair(accessToken, refreshToken, Clock.System.now() + TimeoutConstants.SESSION_TIMEOUT)

            // Send verification email
            val verificationResult = emailProvider.sendVerificationEmail(email)
            if (verificationResult.isFailure()) {
                Logger.warn("AuthEngine", "Failed to send verification email: ${verificationResult.getOrNull()}")
            } else {
                eventBus.dispatch(Verification.EmailVerificationCodeSent(email), metadata)
            }

            _authState.value = AuthState.VerificationRequired(
                VerificationMethod.Email(email),
                user
            )

            eventBus.dispatch(Authentication.SignUpCompleted(user, tokens), metadata)

            _isLoading.value = false
            AuthResult.Success(user)

        } catch (e: Exception) {
            val metadata = EventMetadata(source = "AuthEngine")
            val error = AuthError.UnknownError("Sign up failed: ${e.message}", e)
            eventBus.dispatch(Authentication.SignUpFailed(error), metadata)
            _authState.value = AuthState.Error(error, _authState.value)
            _isLoading.value = false
            AuthResult.Failure(error)
        }
    }

    /**
     * Sign in with OAuth provider.
     */
    suspend fun signInWithOAuth(provider: OAuthProvider): AuthResult<User> {
        Logger.debug("AuthEngine", "Sign in with OAuth: ${provider::class.simpleName}")

        return try {
            _isLoading.value = true
            val metadata = EventMetadata(source = "AuthEngine")
            eventBus.dispatch(OAuth.OAuthFlowStarted(OAuthProvider.GOOGLE, "state"), metadata)

            // TODO: Implement actual OAuth flow
            // For now, simulate successful OAuth authentication
            val user = createMockUser(
                "oauth-user@example.com",
                AuthMethod.OAuth(OAuthProvider.GOOGLE, "google-user-123", true, Clock.System.now())
            )
            val accessToken = jwtTokenManager.createAccessToken(user.id, user.email)
            val refreshToken = jwtTokenManager.createRefreshToken(user.id)
            val tokens = TokenPair(accessToken, refreshToken, Clock.System.now() + TimeoutConstants.SESSION_TIMEOUT)

            _authState.value = AuthState.Authenticated(user, tokens)
            eventBus.dispatch(OAuth.OAuthFlowCompleted(provider, user, tokens), metadata)

            _isLoading.value = false
            AuthResult.Success(user)

        } catch (e: Exception) {
            val metadata = EventMetadata(source = "AuthEngine")
            val error = AuthError.UnknownError("OAuth sign in failed: ${e.message}", e)
            eventBus.dispatch(OAuth.OAuthFlowFailed(provider, error), metadata)
            _authState.value = AuthState.Error(error, _authState.value)
            _isLoading.value = false
            AuthResult.Failure(error)
        }
    }

    /**
     * Start phone number verification.
     */
    suspend fun startPhoneVerification(phoneNumber: String): AuthResult<String> {
        Logger.debug("AuthEngine", "Starting phone verification: $phoneNumber")

        return try {
            _isLoading.value = true
            val metadata = EventMetadata(source = "AuthEngine")
            eventBus.dispatch(Verification.PhoneVerificationRequested(phoneNumber), metadata)

            // Validate phone number format
            val phoneValidation = smsProvider.validatePhoneNumber(phoneNumber)
            if (phoneValidation.isFailure()) {
                val error = AuthError.ValidationError("Invalid phone number format", "phoneNumber")
                eventBus.dispatch(Verification.PhoneVerificationFailed(error), metadata)
                _isLoading.value = false
                return AuthResult.Failure(error)
            }

            // Send verification code
            val result = smsProvider.sendVerificationCode(phoneNumber)

            _isLoading.value = false

            if (result.isSuccess()) {
                val sessionId = result.getOrThrow()
                _authState.value = AuthState.VerificationRequired(
                    VerificationMethod.Phone(phoneNumber)
                )
                eventBus.dispatch(Verification.PhoneVerificationCodeSent(phoneNumber), metadata)
                AuthResult.Success(sessionId)
            } else {
                val error = result as AuthResult.Failure
                eventBus.dispatch(Verification.PhoneVerificationFailed(error.error), metadata)
                AuthResult.Failure(error.error)
            }

        } catch (e: Exception) {
            val metadata = EventMetadata(source = "AuthEngine")
            val error = AuthError.UnknownError("Phone verification failed: ${e.message}", e)
            eventBus.dispatch(Verification.PhoneVerificationFailed(error), metadata)
            _isLoading.value = false
            AuthResult.Failure(error)
        }
    }

    /**
     * Verify phone number with SMS code.
     */
    suspend fun verifyPhoneCode(phoneNumber: String, code: String, sessionId: String): AuthResult<User> {
        Logger.debug("AuthEngine", "Verifying phone code for: $phoneNumber")

        return try {
            _isLoading.value = true
            val metadata = EventMetadata(source = "AuthEngine")

            val result = smsProvider.verifySmsCode(phoneNumber, code, sessionId)

            if (result.isSuccess()) {
                // Create or get user with verified phone number
                val user = createMockUser(
                    null,
                    AuthMethod.Phone(phoneNumber, true, Clock.System.now())
                )
                val accessToken = jwtTokenManager.createAccessToken(user.id, user.email)
            val refreshToken = jwtTokenManager.createRefreshToken(user.id)
            val tokens = TokenPair(accessToken, refreshToken, Clock.System.now() + TimeoutConstants.SESSION_TIMEOUT)

                _authState.value = AuthState.Authenticated(user, tokens)
                eventBus.dispatch(Verification.PhoneVerificationCompleted(phoneNumber), metadata)

                _isLoading.value = false
                AuthResult.Success(user)
            } else {
                val error = result as AuthResult.Failure
                eventBus.dispatch(Verification.PhoneVerificationFailed(error.error), metadata)
                _isLoading.value = false
                AuthResult.Failure(error.error)
            }

        } catch (e: Exception) {
            val metadata = EventMetadata(source = "AuthEngine")
            val error = AuthError.UnknownError("Phone verification failed: ${e.message}", e)
            eventBus.dispatch(Verification.PhoneVerificationFailed(error), metadata)
            _isLoading.value = false
            AuthResult.Failure(error)
        }
    }

    /**
     * Sign out the current user.
     */
    suspend fun signOut(): AuthResult<Unit> {
        Logger.debug("AuthEngine", "Signing out user")

        return try {
            val metadata = EventMetadata(source = "AuthEngine")
            eventBus.dispatch(Authentication.SignOutRequested, metadata)

            // TODO: Invalidate tokens on server
            // TODO: Clear secure storage

            _authState.value = AuthState.Unauthenticated
            eventBus.dispatch(Authentication.SignOutCompleted, metadata)

            AuthResult.Success(Unit)

        } catch (e: Exception) {
            val metadata = EventMetadata(source = "AuthEngine")
            val error = AuthError.UnknownError("Sign out failed: ${e.message}", e)
            eventBus.dispatch(Authentication.SignOutFailed(error), metadata)
            AuthResult.Failure(error)
        }
    }

    /**
     * Get the currently authenticated user.
     */
    fun getCurrentUser(): User? {
        return when (val state = _authState.value) {
            is AuthState.Authenticated -> state.user
            is AuthState.VerificationRequired -> state.user
            else -> null
        }
    }

    /**
     * Check if user is authenticated.
     */
    fun isAuthenticated(): Boolean {
        return _authState.value is AuthState.Authenticated
    }

    private fun subscribeToAuthEvents() {
        scope.launch {
            eventBus.events.collect { eventWithMetadata ->
                val event = eventWithMetadata.event
                Logger.debug("AuthEngine", "Received event: ${eventWithMetadata.event::class.simpleName}")
                // TODO: Handle specific events for logging, analytics, etc.
            }
        }
    }

    private fun createMockUser(email: String?, authMethod: AuthMethod): User {
        val now = Clock.System.now()
        return User(
            id = "user_${Clock.System.now().toEpochMilliseconds()}",
            email = email,
            displayName = email?.substringBefore("@"),
            emailVerified = authMethod is AuthMethod.Email && authMethod.verified,
            phoneNumber = if (authMethod is AuthMethod.Phone) authMethod.phoneNumber else null,
            phoneVerified = authMethod is AuthMethod.Phone && authMethod.verified,
            isAnonymous = false,
            createdAt = now,
            updatedAt = now,
            lastSignInAt = now,
            authMethods = listOf(authMethod)
        )
    }

    private fun createNewUser(email: String, displayName: String?, authMethod: AuthMethod): User {
        val now = Clock.System.now()
        return User(
            id = "user_${Clock.System.now().toEpochMilliseconds()}",
            email = email,
            displayName = displayName ?: email.substringBefore("@"),
            emailVerified = false,
            isAnonymous = false,
            createdAt = now,
            updatedAt = now,
            authMethods = listOf(authMethod)
        )
    }


    companion object {
        private var INSTANCE: AuthEngine? = null

        fun getInstance(
            emailProvider: EmailProvider,
            smsProvider: SmsProvider,
            oauthProvider: OAuthProvider
        ): AuthEngine {
            return INSTANCE ?: AuthEngine(emailProvider, smsProvider, oauthProvider).also { INSTANCE = it }
        }

        fun reset() {
            INSTANCE = null
        }
    }
}

/**
 * Internal storage representation of a user with authentication data.
 */
private data class StoredUser(
    val user: User,
    val passwordHash: app.multiauth.security.HashedPassword? = null,
    val createdAt: Instant = Clock.System.now()
)

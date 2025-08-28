package app.multiauth.core

import app.multiauth.events.*
import app.multiauth.models.*
import app.multiauth.util.Logger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Centralized state manager for authentication system.
 * Manages authentication state, user preferences, and provides state synchronization.
 */
class AuthStateManager private constructor(
    private val eventBus: EventBus = EventBusInstance()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Core authentication state
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    // User preferences and settings
    private val _userPreferences = MutableStateFlow<UserPreferences>(UserPreferences())
    val userPreferences: StateFlow<UserPreferences> = _userPreferences.asStateFlow()
    
    // Authentication history
    private val _authHistory = MutableStateFlow<List<AuthHistoryEntry>>(emptyList())
    val authHistory: StateFlow<List<AuthHistoryEntry>> = _authHistory.asStateFlow()
    
    // Error state
    private val _lastError = MutableStateFlow<AuthError?>(null)
    val lastError: StateFlow<AuthError?> = _lastError.asStateFlow()
    
    // Loading states
    private val _loadingStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val loadingStates: StateFlow<Map<String, Boolean>> = _loadingStates.asStateFlow()
    
    init {
        Logger.info("AuthStateManager", "AuthStateManager initialized")
        subscribeToEvents()
        loadPersistedState()
    }
    
    /**
     * Update the main authentication state.
     */
    fun updateAuthState(newState: AuthState) {
        Logger.debug("AuthStateManager", "Updating auth state: ${newState::class.simpleName}")
        
        val previousState = _authState.value
        _authState.value = newState
        
        // Add to history
        addToHistory(newState, previousState)
        
        // Clear error if moving to non-error state
        if (newState !is AuthState.Error) {
            _lastError.value = null
        }
        
        // Persist state
        persistState()
        
        scope.launch {
            eventBus.dispatch(AuthEvent.State.StateChanged(previousState, newState), "AuthStateManager")
        }
    }
    
    /**
     * Set loading state for a specific operation.
     */
    fun setLoading(operation: String, isLoading: Boolean) {
        _loadingStates.value = _loadingStates.value + (operation to isLoading)
        
        scope.launch {
            if (isLoading) {
                eventBus.dispatch(AuthEvent.State.OperationStarted(operation), "AuthStateManager")
            } else {
                eventBus.dispatch(AuthEvent.State.OperationCompleted(operation), "AuthStateManager")
            }
        }
    }
    
    /**
     * Set error state.
     */
    fun setError(error: AuthError) {
        Logger.error("AuthStateManager", "Setting error: ${error.message}")
        
        _lastError.value = error
        
        // Update auth state to error state
        val currentState = _authState.value
        if (currentState !is AuthState.Error) {
            _authState.value = AuthState.Error(error, currentState)
        }
        
        // Add to history
        addToHistory(AuthState.Error(error, currentState), currentState)
        
        scope.launch {
            eventBus.dispatch(AuthEvent.State.ErrorOccurred(error), "AuthStateManager")
        }
    }
    
    /**
     * Clear error state.
     */
    fun clearError() {
        _lastError.value = null
        
        // Restore previous state if current state is error
        val currentState = _authState.value
        if (currentState is AuthState.Error) {
            _authState.value = currentState.previousState ?: AuthState.Initial
        }
        
        scope.launch {
            eventBus.dispatch(AuthEvent.State.ErrorCleared, "AuthStateManager")
        }
    }
    
    /**
     * Update user preferences.
     */
    fun updateUserPreferences(updates: (UserPreferences) -> UserPreferences) {
        val newPreferences = updates(_userPreferences.value)
        _userPreferences.value = newPreferences
        
        persistUserPreferences()
        scope.launch {
            eventBus.dispatch(AuthEvent.State.PreferencesUpdated(newPreferences), "AuthStateManager")
        }
    }
    
    /**
     * Reset authentication state to initial.
     */
    fun resetState() {
        Logger.info("AuthStateManager", "Resetting authentication state")
        
        _authState.value = AuthState.Initial
        _lastError.value = null
        _loadingStates.value = emptyMap()
        
        // Clear history but keep preferences
        _authHistory.value = emptyList()
        
        persistState()
        scope.launch {
            eventBus.dispatch(AuthEvent.State.StateReset, "AuthStateManager")
        }
    }
    
    /**
     * Get current user from state.
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
    
    /**
     * Check if user is in verification state.
     */
    fun isVerificationRequired(): Boolean {
        return _authState.value is AuthState.VerificationRequired
    }
    
    /**
     * Check if there's an active error.
     */
    fun hasError(): Boolean {
        return _lastError.value != null
    }
    
    /**
     * Check if a specific operation is loading.
     */
    fun isLoading(operation: String): Boolean {
        return _loadingStates.value[operation] ?: false
    }
    
    /**
     * Get authentication statistics.
     */
    fun getAuthStats(): AuthStats {
        val history = _authHistory.value
        val totalAttempts = history.size
        val successfulAuths = history.count { it.state is AuthState.Authenticated }
        val failedAttempts = history.count { it.state is AuthState.Error }
        val verificationRequired = history.count { it.state is AuthState.VerificationRequired }
        
        return AuthStats(
            totalAttempts = totalAttempts,
            successfulAuths = successfulAuths,
            failedAttempts = failedAttempts,
            verificationRequired = verificationRequired,
            lastActivityAt = history.lastOrNull()?.timestamp
        )
    }
    
    private fun subscribeToEvents() {
        scope.launch {
            eventBus.events.collect { eventWithMetadata ->
                when (val event = eventWithMetadata.event) {
                    is AuthEvent.Authentication.SignInCompleted -> {
                        updateAuthState(AuthState.Authenticated(event.user, event.tokens))
                    }
                    is AuthEvent.Authentication.SignUpCompleted -> {
                        updateAuthState(AuthState.VerificationRequired(
                            VerificationMethod.Email(event.user.email ?: ""),
                            event.user
                        ))
                    }
                    is AuthEvent.Authentication.SignOutCompleted -> {
                        updateAuthState(AuthState.Unauthenticated)
                    }
                    is AuthEvent.Verification.PhoneVerificationCompleted -> {
                        // TODO: Handle phone verification completion
                    }
                    is AuthEvent.Session.SessionExpired -> {
                        updateAuthState(AuthState.Unauthenticated)
                    }
                    else -> {
                        // Handle other events as needed
                    }
                }
            }
        }
    }
    
    private fun addToHistory(newState: AuthState, previousState: AuthState?) {
        val entry = AuthHistoryEntry(
            timestamp = Clock.System.now(),
            state = newState,
            previousState = previousState
        )
        
        _authHistory.value = _authHistory.value + entry
        
        // Keep only last 100 entries
        if (_authHistory.value.size > 100) {
            _authHistory.value = _authHistory.value.takeLast(100)
        }
    }
    
    private fun loadPersistedState() {
        // TODO: Implement state persistence loading
        // For now, start with initial state
        Logger.debug("AuthStateManager", "Loading persisted state")
    }
    
    private fun persistState() {
        // TODO: Implement state persistence
        // For now, just log the action
        Logger.debug("AuthStateManager", "Persisting state")
    }
    
    private fun persistUserPreferences() {
        // TODO: Implement user preferences persistence
        Logger.debug("AuthStateManager", "Persisting user preferences")
    }
    
    companion object {
        private var INSTANCE: AuthStateManager? = null
        
        fun getInstance(): AuthStateManager {
            return INSTANCE ?: AuthStateManager().also { INSTANCE = it }
        }
        
        fun reset() {
            INSTANCE = null
        }
    }
}

/**
 * User preferences and settings.
 */
data class UserPreferences(
    val autoSignIn: Boolean = false,
    val biometricEnabled: Boolean = false,
    val rememberMe: Boolean = true,
    val sessionTimeout: Long = 30 * 60 * 1000L, // 30 minutes
    val language: String = "en",
    val theme: String = "system",
    val notifications: NotificationPreferences = NotificationPreferences()
)

/**
 * Notification preferences.
 */
data class NotificationPreferences(
    val emailNotifications: Boolean = true,
    val smsNotifications: Boolean = false,
    val pushNotifications: Boolean = true,
    val securityAlerts: Boolean = true
)

/**
 * Authentication history entry.
 */
data class AuthHistoryEntry(
    val timestamp: kotlinx.datetime.Instant,
    val state: AuthState,
    val previousState: AuthState?
)

/**
 * Authentication statistics.
 */
data class AuthStats(
    val totalAttempts: Int,
    val successfulAuths: Int,
    val failedAttempts: Int,
    val verificationRequired: Int,
    val lastActivityAt: kotlinx.datetime.Instant?
)
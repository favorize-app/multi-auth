package app.multiauth.core

import kotlinx.datetime.Clock
import app.multiauth.events.AuthEvent
import app.multiauth.events.Authentication
import app.multiauth.events.EventMetadata
import app.multiauth.models.*
import app.multiauth.util.Logger
import app.multiauth.events.EventBus
import app.multiauth.events.EventBusInstance
import app.multiauth.events.Session
import app.multiauth.events.State
import app.multiauth.events.Verification
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Centralized state manager for authentication system.
 */
class AuthStateManager private constructor(
    private val eventBus: EventBus = EventBusInstance()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    private val _userPreferences = MutableStateFlow(UserPreferences())
    val userPreferences: StateFlow<UserPreferences> = _userPreferences.asStateFlow()
    
    private val _authHistory = MutableStateFlow<List<AuthHistoryEntry>>(emptyList())
    val authHistory: StateFlow<List<AuthHistoryEntry>> = _authHistory.asStateFlow()

    private val _lastError = MutableStateFlow<AuthError?>(null)
    val lastError: StateFlow<AuthError?> = _lastError.asStateFlow()
    
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
        
        if (newState !is AuthState.Error) {
            _lastError.value = null
        }
        
        // Persist state
        persistState()
        
        scope.launch {
            val metadata = EventMetadata(source = "AuthStateManager")
            eventBus.dispatch(app.multiauth.events.State.StateChanged(previousState, newState), metadata)
        }
    }
    
    /**
     * Update user preferences.
     */
    fun updateUserPreferences(preferences: UserPreferences) {
        Logger.debug("AuthStateManager", "Updating user preferences")
        _userPreferences.value = preferences
        persistUserPreferences()
        
        scope.launch {
            val metadata = EventMetadata(source = "AuthStateManager")
            eventBus.dispatch(app.multiauth.events.State.PreferencesUpdated(preferences), metadata)
        }
    }
    
    /**
     * Set loading state for a specific operation.
     */
    fun setLoading(operation: String, isLoading: Boolean) {
        _loadingStates.value = _loadingStates.value + (operation to isLoading)
        
        scope.launch {
            val metadata = EventMetadata(source = "AuthStateManager")
            if (isLoading) {
                eventBus.dispatch(State.OperationStarted(operation), metadata)
            } else {
                eventBus.dispatch(State.OperationCompleted(operation), metadata)
            }
        }
    }
    
    /**
     * Set an error state.
     */
    fun setError(error: AuthError) {
        Logger.error("AuthStateManager", "Setting error: ${error.message}")
        _lastError.value = error
        
        scope.launch {
            val metadata = EventMetadata(source = "AuthStateManager")
            eventBus.dispatch(State.ErrorOccurred(error), metadata)
        }
    }
    
    /**
     * Clear the current error state.
     */
    fun clearError() {
        Logger.debug("AuthStateManager", "Clearing error state")
        _lastError.value = null
        
        scope.launch {
            val metadata = EventMetadata(source = "AuthStateManager")
            eventBus.dispatch(app.multiauth.events.State.ErrorCleared, metadata)
        }
    }
    
    private fun subscribeToEvents() {
        scope.launch {
            eventBus.events.collect { eventWithMetadata ->
                when (val event = eventWithMetadata.event) {
                    is Authentication.SignInCompleted -> {
                        updateAuthState(AuthState.Authenticated(event.user, event.tokens))
                    }
                    is Authentication.SignUpCompleted -> {
                        updateAuthState(AuthState.VerificationRequired(
                            VerificationMethod.Email(event.user.email ?: ""),
                            event.user
                        ))
                    }
                    is Authentication.SignOutCompleted -> {
                        updateAuthState(AuthState.Unauthenticated)
                    }
                    is Verification.PhoneVerificationCompleted -> {
                        // TODO: Handle phone verification completion
                    }
                    is Session.SessionExpired -> {
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
            previousState = previousState,
            source = "AuthStateManager"
        )
        
        val currentHistory = _authHistory.value
        val updatedHistory = if (currentHistory.size >= 100) {
            // Keep only the last 99 entries plus the new one
            currentHistory.takeLast(99) + entry
        } else {
            currentHistory + entry
        }
        
        _authHistory.value = updatedHistory
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
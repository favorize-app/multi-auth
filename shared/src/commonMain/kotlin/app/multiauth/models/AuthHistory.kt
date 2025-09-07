package app.multiauth.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents an entry in the authentication history.
 * Tracks state changes and transitions for audit and debugging purposes.
 */
@Serializable
data class AuthHistoryEntry(
    /**
     * When this state change occurred
     */
    val timestamp: Instant,
    
    /**
     * The new authentication state
     */
    @kotlinx.serialization.Contextual
    val state: AuthState,
    
    /**
     * The previous authentication state (if any)
     */
    @kotlinx.serialization.Contextual
    val previousState: AuthState? = null,
    
    /**
     * Additional context about the state change
     */
    val context: Map<String, String> = emptyMap(),
    
    /**
     * Source of the state change (e.g., "AuthStateManager", "SessionManager")
     */
    val source: String? = null
)

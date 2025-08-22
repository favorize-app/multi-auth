package app.multiauth.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import app.multiauth.util.Logger

/**
 * Central event handling system for the multi-auth module.
 * All authentication events flow through this bus, allowing for decoupled event handling.
 */
class EventBus private constructor() {
    
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _events = MutableSharedFlow<EventWithMetadata<AuthEvent>>(
        replay = 0,
        extraBufferCapacity = 64
    )
    
    /**
     * Public flow of events that subscribers can observe.
     */
    val events: SharedFlow<EventWithMetadata<AuthEvent>> = _events.asSharedFlow()
    
    /**
     * Dispatches an event to all subscribers.
     * @param event The authentication event to dispatch
     * @param metadata Optional metadata for the event
     */
    suspend fun dispatch(event: AuthEvent, metadata: EventMetadata = EventMetadata()) {
        val eventWithMetadata = EventWithMetadata(event, metadata)
        
        // Log the event for debugging
        Logger.debug("EventBus", "Dispatching event: ${event::class.simpleName}")
        
        try {
            _events.emit(eventWithMetadata)
        } catch (e: Exception) {
            Logger.error("EventBus", "Failed to dispatch event: ${event::class.simpleName}", e)
            throw e
        }
    }
    
    /**
     * Dispatches an event from a specific source.
     * @param event The authentication event to dispatch
     * @param source The source of the event
     * @param userId Optional user ID for the event
     */
    suspend fun dispatch(event: AuthEvent, source: String, userId: String? = null) {
        val metadata = EventMetadata(
            source = source,
            userId = userId
        )
        dispatch(event, metadata)
    }
    
    /**
     * Dispatches an event with correlation ID for tracking related events.
     * @param event The authentication event to dispatch
     * @param correlationId The correlation ID for tracking
     * @param source The source of the event
     */
    suspend fun dispatch(event: AuthEvent, correlationId: String, source: String) {
        val metadata = EventMetadata(
            correlationId = correlationId,
            source = source
        )
        dispatch(event, metadata)
    }
    
    /**
     * Filters events by type for specific subscribers.
     * @param T The type of events to filter
     * @return A flow containing only events of the specified type
     */
    inline fun <reified T : AuthEvent> eventsOfType(): Flow<T> {
        return events
            .filter { it.event is T }
            .map { it.event as T }
    }
    
    /**
     * Filters events by a specific event class.
     * @param eventClass The class of events to filter
     * @return A flow containing only events of the specified class
     */
    // Note: eventsOfClass is not available in common code due to Class<T> limitations
    // Use eventsOfType<T>() instead for type-safe event filtering
    
    /**
     * Subscribes to events and handles them with error handling.
     * @param handler The function to handle events
     */
    fun subscribe(handler: suspend (EventWithMetadata<AuthEvent>) -> Unit) {
        scope.launch {
            events
                .onEach { event ->
                    try {
                        handler(event)
                    } catch (e: Exception) {
                        Logger.error("EventBus", "Error in event handler", e)
                    }
                }
                .catch { e ->
                    Logger.error("EventBus", "Error in event subscription", e)
                }
                .collect { }
        }
    }
    
    /**
     * Subscribes to events of a specific type.
     * @param T The type of events to subscribe to
     * @param handler The function to handle events
     */
    inline fun <reified T : AuthEvent> subscribe(
        crossinline handler: suspend (T, EventMetadata) -> Unit
    ) {
        scope.launch {
            events
                .filter { it.event is T }
                .onEach { eventWithMetadata ->
                    try {
                        handler(eventWithMetadata.event as T, eventWithMetadata.metadata)
                    } catch (e: Exception) {
                        Logger.error("EventBus", "Error in typed event handler", e)
                    }
                }
                .catch { e ->
                    Logger.error("EventBus", "Error in typed event handler", e)
                }
                .collect { }
        }
    }
    
    /**
     * Clears all event history and resets the bus.
     * This is useful for testing or when the app is reset.
     */
    suspend fun clear() {
        Logger.debug("EventBus", "Clearing event bus")
        // The shared flow will automatically clear old events due to replay = 0
    }
    
    companion object {
        private var INSTANCE: EventBus? = null
        
        fun getInstance(): EventBus {
            return INSTANCE ?: EventBus().also { INSTANCE = it }
        }
        
        fun reset() {
            INSTANCE = null
        }
    }
}

/**
 * Extension function to dispatch events more easily.
 */
suspend fun AuthEvent.dispatch(
    eventBus: EventBus = EventBus.getInstance(),
    metadata: EventMetadata = EventMetadata()
) {
    eventBus.dispatch(this, metadata)
}

/**
 * Extension function to dispatch events with source.
 */
suspend fun AuthEvent.dispatch(
    source: String,
    eventBus: EventBus = EventBus.getInstance()
) {
    eventBus.dispatch(this, source)
}
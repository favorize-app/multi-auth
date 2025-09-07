package app.multiauth.events

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class EventWithMetadata(
    val event: AuthEvent,
    val metadata: EventMetadata
)

interface EventBus {
    val events: Flow<EventWithMetadata>
    suspend fun dispatch(event: AuthEvent, metadata: EventMetadata)
}

class EventBusInstance : EventBus {
    private val _events = MutableSharedFlow<EventWithMetadata>()
    override val events: Flow<EventWithMetadata> = _events.asSharedFlow()

    override suspend fun dispatch(event: AuthEvent, metadata: EventMetadata) {
        _events.emit(EventWithMetadata(event, metadata))
    }
}

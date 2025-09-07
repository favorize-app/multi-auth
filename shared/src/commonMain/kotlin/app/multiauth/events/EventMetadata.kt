package app.multiauth.events

data class EventMetadata(
    val source: String,
    val timestamp: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
)

@file:OptIn(ExperimentalTime::class)

package app.multiauth.events

import kotlin.time.ExperimentalTime
import kotlin.time.Clock

data class EventMetadata(
    val source: String,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

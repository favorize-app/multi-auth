package app.multiauth

import app.multiauth.events.*
import app.multiauth.models.*
import app.multiauth.util.Logger
import app.multiauth.util.MemoryLogHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class EventBusTest {
    
    private lateinit var eventBus: EventBus
    private lateinit var memoryLogger: MemoryLogHandler
    
    @BeforeTest
    fun setup() {
        EventBus.reset()
        eventBus = EventBus.getInstance()
        
        // Set up memory logger for testing
        memoryLogger = MemoryLogHandler()
        Logger.clearHandlers()
        Logger.addHandler(memoryLogger)
    }
    
    @AfterTest
    fun tearDown() {
        EventBus.reset()
        Logger.clearHandlers()
    }
    
    @Test
    fun `test event dispatch and subscription`() = runTest {
        var receivedEvent: AuthEvent? = null
        var receivedMetadata: EventMetadata? = null
        
        // Subscribe to events
        eventBus.subscribe<AuthEvent.Authentication> { event, metadata ->
            receivedEvent = event
            receivedMetadata = metadata
        }
        
        // Dispatch an event
        val testEvent = AuthEvent.Authentication.SignInRequested
        eventBus.dispatch(testEvent, "test")
        
        // Wait a bit for the event to be processed
        kotlinx.coroutines.delay(100)
        
        // Verify the event was received
        assertNotNull(receivedEvent)
        assertEquals(testEvent, receivedEvent)
        assertNotNull(receivedMetadata)
        assertEquals("test", receivedMetadata?.source)
    }
    
    @Test
    fun `test event filtering by type`() = runTest {
        var receivedEvents = mutableListOf<AuthEvent.Authentication>()
        
        // Subscribe to authentication events only
        eventBus.subscribe<AuthEvent.Authentication> { event, _ ->
            receivedEvents.add(event)
        }
        
        // Dispatch different types of events
        val authEvent = AuthEvent.Authentication.SignInRequested
        val verificationEvent = AuthEvent.Verification.EmailVerificationRequested("test@example.com")
        
        eventBus.dispatch(authEvent)
        eventBus.dispatch(verificationEvent)
        
        // Wait for events to be processed
        kotlinx.coroutines.delay(100)
        
        // Should only receive authentication events
        assertEquals(1, receivedEvents.size)
        assertEquals(authEvent, receivedEvents[0])
    }
    
    @Test
    fun `test event metadata`() = runTest {
        var receivedMetadata: EventMetadata? = null
        
        eventBus.subscribe<AuthEvent.Authentication> { _, metadata ->
            receivedMetadata = metadata
        }
        
        val metadata = EventMetadata(
            correlationId = "test-correlation",
            userId = "test-user",
            source = "test-source"
        )
        
        eventBus.dispatch(AuthEvent.Authentication.SignInRequested, metadata)
        
        // Wait for event to be processed
        kotlinx.coroutines.delay(100)
        
        assertNotNull(receivedMetadata)
        assertEquals("test-correlation", receivedMetadata?.correlationId)
        assertEquals("test-user", receivedMetadata?.userId)
        assertEquals("test-source", receivedMetadata?.source)
    }
    
    @Test
    fun `test multiple subscribers`() = runTest {
        var subscriber1Count = 0
        var subscriber2Count = 0
        
        // First subscriber
        eventBus.subscribe<AuthEvent.Authentication> { _, _ ->
            subscriber1Count++
        }
        
        // Second subscriber
        eventBus.subscribe<AuthEvent.Authentication> { _, _ ->
            subscriber2Count++
        }
        
        // Dispatch an event
        eventBus.dispatch(AuthEvent.Authentication.SignInRequested)
        
        // Wait for events to be processed
        kotlinx.coroutines.delay(100)
        
        // Both subscribers should receive the event
        assertEquals(1, subscriber1Count)
        assertEquals(1, subscriber2Count)
    }
    
    @Test
    fun `test event logging`() = runTest {
        // Dispatch an event
        eventBus.dispatch(AuthEvent.Authentication.SignInRequested, "test")
        
        // Wait for event to be processed
        kotlinx.coroutines.delay(100)
        
        // Check that the event was logged
        val logs = memoryLogger.getLogs()
        val debugLogs = memoryLogger.getLogsByLevel(Logger.Level.DEBUG)
        
        assertTrue(logs.isNotEmpty())
        assertTrue(debugLogs.any { it.message.contains("Dispatching event") })
    }
    
    @Test
    fun `test event bus singleton pattern`() {
        val instance1 = EventBus.getInstance()
        val instance2 = EventBus.getInstance()
        
        // Should return the same instance
        assertSame(instance1, instance2)
        
        // Reset and get new instance
        EventBus.reset()
        val instance3 = EventBus.getInstance()
        
        // Should be a different instance
        assertNotSame(instance1, instance3)
    }
}
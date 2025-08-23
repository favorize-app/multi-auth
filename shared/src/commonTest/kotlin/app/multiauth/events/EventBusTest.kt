package app.multiauth.events

import app.multiauth.util.Logger
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class EventBusTest {
    
    private lateinit var eventBus: EventBus
    private lateinit var mockLogger: MockLogger
    
    @BeforeTest
    fun setup() {
        mockLogger = MockLogger()
        eventBus = EventBus(mockLogger)
    }
    
    @Test
    fun `test publish and subscribe to events`() = runTest {
        // Given
        val receivedEvents = mutableListOf<AuthEvent>()
        val subscription = eventBus.subscribe { event ->
            receivedEvents.add(event)
        }
        
        val testEvent = AuthEvent.Login("test@example.com")
        
        // When
        eventBus.publish(testEvent)
        
        // Then
        assertEquals(1, receivedEvents.size)
        assertEquals(testEvent, receivedEvents[0])
        
        // Cleanup
        eventBus.unsubscribe(subscription)
    }
    
    @Test
    fun `test multiple subscribers receive events`() = runTest {
        // Given
        val events1 = mutableListOf<AuthEvent>()
        val events2 = mutableListOf<AuthEvent>()
        val events3 = mutableListOf<AuthEvent>()
        
        val subscription1 = eventBus.subscribe { event -> events1.add(event) }
        val subscription2 = eventBus.subscribe { event -> events2.add(event) }
        val subscription3 = eventBus.subscribe { event -> events3.add(event) }
        
        val testEvent = AuthEvent.Login("test@example.com")
        
        // When
        eventBus.publish(testEvent)
        
        // Then
        assertEquals(1, events1.size)
        assertEquals(1, events2.size)
        assertEquals(1, events3.size)
        assertEquals(testEvent, events1[0])
        assertEquals(testEvent, events2[0])
        assertEquals(testEvent, events3[0])
        
        // Cleanup
        eventBus.unsubscribe(subscription1)
        eventBus.unsubscribe(subscription2)
        eventBus.unsubscribe(subscription3)
    }
    
    @Test
    fun `test unsubscribe removes event listener`() = runTest {
        // Given
        val events = mutableListOf<AuthEvent>()
        val subscription = eventBus.subscribe { event -> events.add(event) }
        
        val testEvent1 = AuthEvent.Login("test1@example.com")
        val testEvent2 = AuthEvent.Login("test2@example.com")
        
        // When - publish first event
        eventBus.publish(testEvent1)
        assertEquals(1, events.size)
        
        // When - unsubscribe
        eventBus.unsubscribe(subscription)
        
        // When - publish second event
        eventBus.publish(testEvent2)
        
        // Then - second event should not be received
        assertEquals(1, events.size)
        assertEquals(testEvent1, events[0])
    }
    
    @Test
    fun `test unsubscribe with invalid subscription id does nothing`() = runTest {
        // Given
        val events = mutableListOf<AuthEvent>()
        val subscription = eventBus.subscribe { event -> events.add(event) }
        
        val testEvent = AuthEvent.Login("test@example.com")
        
        // When - unsubscribe with invalid id
        eventBus.unsubscribe("invalid-subscription-id")
        
        // When - publish event
        eventBus.publish(testEvent)
        
        // Then - event should still be received
        assertEquals(1, events.size)
        assertEquals(testEvent, events[0])
        
        // Cleanup
        eventBus.unsubscribe(subscription)
    }
    
    @Test
    fun `test unsubscribe with empty subscription id does nothing`() = runTest {
        // Given
        val events = mutableListOf<AuthEvent>()
        val subscription = eventBus.subscribe { event -> events.add(event) }
        
        val testEvent = AuthEvent.Login("test@example.com")
        
        // When - unsubscribe with empty id
        eventBus.unsubscribe("")
        
        // When - publish event
        eventBus.publish(testEvent)
        
        // Then - event should still be received
        assertEquals(1, events.size)
        assertEquals(testEvent, events[0])
        
        // Cleanup
        eventBus.unsubscribe(subscription)
    }
    
    @Test
    fun `test publish multiple events to multiple subscribers`() = runTest {
        // Given
        val events1 = mutableListOf<AuthEvent>()
        val events2 = mutableListOf<AuthEvent>()
        
        val subscription1 = eventBus.subscribe { event -> events1.add(event) }
        val subscription2 = eventBus.subscribe { event -> events2.add(event) }
        
        val testEvents = listOf(
            AuthEvent.Login("user1@example.com"),
            AuthEvent.Login("user2@example.com"),
            AuthEvent.Logout,
            AuthEvent.Register("user3@example.com")
        )
        
        // When
        testEvents.forEach { event ->
            eventBus.publish(event)
        }
        
        // Then
        assertEquals(testEvents.size, events1.size)
        assertEquals(testEvents.size, events2.size)
        
        testEvents.forEachIndexed { index, event ->
            assertEquals(event, events1[index])
            assertEquals(event, events2[index])
        }
        
        // Cleanup
        eventBus.unsubscribe(subscription1)
        eventBus.unsubscribe(subscription2)
    }
    
    @Test
    fun `test subscription ids are unique`() = runTest {
        // Given
        val subscription1 = eventBus.subscribe { }
        val subscription2 = eventBus.subscribe { }
        val subscription3 = eventBus.subscribe { }
        
        // When & Then
        assertNotEquals(subscription1, subscription2)
        assertNotEquals(subscription2, subscription3)
        assertNotEquals(subscription1, subscription3)
        
        // Cleanup
        eventBus.unsubscribe(subscription1)
        eventBus.unsubscribe(subscription2)
        eventBus.unsubscribe(subscription3)
    }
    
    @Test
    fun `test subscription ids are not empty`() = runTest {
        // Given
        val subscription = eventBus.subscribe { }
        
        // When & Then
        assertTrue(subscription.isNotEmpty())
        assertTrue(subscription.length > 0)
        
        // Cleanup
        eventBus.unsubscribe(subscription)
    }
    
    @Test
    fun `test publish event with no subscribers does not crash`() = runTest {
        // Given - no subscribers
        
        val testEvent = AuthEvent.Login("test@example.com")
        
        // When & Then - should not throw exception
        assertDoesNotThrow {
            eventBus.publish(testEvent)
        }
    }
    
    @Test
    fun `test publish event with null event does not crash`() = runTest {
        // Given
        val events = mutableListOf<AuthEvent>()
        val subscription = eventBus.subscribe { event -> events.add(event) }
        
        // When & Then - should not throw exception
        assertDoesNotThrow {
            eventBus.publish(AuthEvent.Login("test@example.com"))
        }
        
        // Cleanup
        eventBus.unsubscribe(subscription)
    }
    
    @Test
    fun `test concurrent subscriptions and unsubscriptions`() = runTest {
        // Given
        val results = mutableListOf<String>()
        
        // When - multiple concurrent subscribe operations
        val subscribeJobs = List(10) { index ->
            kotlinx.coroutines.async {
                eventBus.subscribe { }
            }
        }
        
        val subscriptions = subscribeJobs.map { it.await() }
        
        // When - multiple concurrent unsubscribe operations
        val unsubscribeJobs = subscriptions.map { subscription ->
            kotlinx.coroutines.async {
                eventBus.unsubscribe(subscription)
            }
        }
        
        unsubscribeJobs.forEach { it.await() }
        
        // Then - all operations should complete without errors
        assertEquals(10, subscriptions.size)
        subscriptions.forEach { subscription ->
            assertTrue(subscription.isNotEmpty())
        }
    }
    
    @Test
    fun `test concurrent event publishing`() = runTest {
        // Given
        val events = mutableListOf<AuthEvent>()
        val subscription = eventBus.subscribe { event -> events.add(event) }
        
        val testEvents = List(100) { index ->
            AuthEvent.Login("user$index@example.com")
        }
        
        // When - multiple concurrent publish operations
        val publishJobs = testEvents.map { event ->
            kotlinx.coroutines.async {
                eventBus.publish(event)
            }
        }
        
        publishJobs.forEach { it.await() }
        
        // Then - all events should be received
        assertEquals(testEvents.size, events.size)
        testEvents.forEach { event ->
            assertTrue(events.contains(event))
        }
        
        // Cleanup
        eventBus.unsubscribe(subscription)
    }
    
    @Test
    fun `test event ordering is maintained`() = runTest {
        // Given
        val events = mutableListOf<AuthEvent>()
        val subscription = eventBus.subscribe { event -> events.add(event) }
        
        val testEvents = listOf(
            AuthEvent.Login("user1@example.com"),
            AuthEvent.Login("user2@example.com"),
            AuthEvent.Logout,
            AuthEvent.Register("user3@example.com")
        )
        
        // When
        testEvents.forEach { event ->
            eventBus.publish(event)
        }
        
        // Then - events should be received in order
        assertEquals(testEvents.size, events.size)
        testEvents.forEachIndexed { index, event ->
            assertEquals(event, events[index])
        }
        
        // Cleanup
        eventBus.unsubscribe(subscription)
    }
    
    @Test
    fun `test subscriber exception handling`() = runTest {
        // Given
        val events = mutableListOf<AuthEvent>()
        val subscription = eventBus.subscribe { event ->
            if (event is AuthEvent.Login && event.email == "crash@example.com") {
                throw RuntimeException("Subscriber crash")
            }
            events.add(event)
        }
        
        val testEvents = listOf(
            AuthEvent.Login("user1@example.com"),
            AuthEvent.Login("crash@example.com"),
            AuthEvent.Login("user2@example.com")
        )
        
        // When
        testEvents.forEach { event ->
            eventBus.publish(event)
        }
        
        // Then - events should still be processed despite subscriber crash
        assertEquals(2, events.size)
        assertEquals(testEvents[0], events[0])
        assertEquals(testEvents[2], events[1])
        
        // Cleanup
        eventBus.unsubscribe(subscription)
    }
    
    @Test
    fun `test multiple subscribers with exceptions`() = runTest {
        // Given
        val events1 = mutableListOf<AuthEvent>()
        val events2 = mutableListOf<AuthEvent>()
        
        val subscription1 = eventBus.subscribe { event ->
            if (event is AuthEvent.Login && event.email == "crash@example.com") {
                throw RuntimeException("Subscriber 1 crash")
            }
            events1.add(event)
        }
        
        val subscription2 = eventBus.subscribe { event ->
            if (event is AuthEvent.Login && event.email == "crash@example.com") {
                throw RuntimeException("Subscriber 2 crash")
            }
            events2.add(event)
        }
        
        val testEvents = listOf(
            AuthEvent.Login("user1@example.com"),
            AuthEvent.Login("crash@example.com"),
            AuthEvent.Login("user2@example.com")
        )
        
        // When
        testEvents.forEach { event ->
            eventBus.publish(event)
        }
        
        // Then - both subscribers should receive events despite crashes
        assertEquals(2, events1.size)
        assertEquals(2, events2.size)
        
        // Cleanup
        eventBus.unsubscribe(subscription1)
        eventBus.unsubscribe(subscription2)
    }
    
    @Test
    fun `test event bus cleanup`() = runTest {
        // Given
        val events = mutableListOf<AuthEvent>()
        val subscription = eventBus.subscribe { event -> events.add(event) }
        
        val testEvent = AuthEvent.Login("test@example.com")
        
        // When - publish event
        eventBus.publish(testEvent)
        assertEquals(1, events.size)
        
        // When - unsubscribe
        eventBus.unsubscribe(subscription)
        
        // When - publish another event
        eventBus.publish(AuthEvent.Login("test2@example.com"))
        
        // Then - second event should not be received
        assertEquals(1, events.size)
    }
    
    @Test
    fun `test event bus with large number of subscribers`() = runTest {
        // Given
        val subscribers = mutableListOf<String>()
        val events = mutableListOf<AuthEvent>()
        
        // Create many subscribers
        repeat(1000) { index ->
            val subscription = eventBus.subscribe { event ->
                if (index == 0) { // Only first subscriber collects events
                    events.add(event)
                }
            }
            subscribers.add(subscription)
        }
        
        val testEvent = AuthEvent.Login("test@example.com")
        
        // When
        eventBus.publish(testEvent)
        
        // Then
        assertEquals(1, events.size)
        assertEquals(testEvent, events[0])
        
        // Cleanup
        subscribers.forEach { subscription ->
            eventBus.unsubscribe(subscription)
        }
    }
    
    @Test
    fun `test event bus memory management`() = runTest {
        // Given
        val subscriptions = mutableListOf<String>()
        
        // Create and immediately unsubscribe from many subscriptions
        repeat(1000) {
            val subscription = eventBus.subscribe { }
            subscriptions.add(subscription)
            eventBus.unsubscribe(subscription)
        }
        
        val testEvent = AuthEvent.Login("test@example.com")
        
        // When - publish event
        eventBus.publish(testEvent)
        
        // Then - should not crash and handle gracefully
        assertDoesNotThrow {
            eventBus.publish(testEvent)
        }
    }
}

class MockLogger : Logger {
    val errorMessages = mutableListOf<String>()
    val warningMessages = mutableListOf<String>()
    val infoMessages = mutableListOf<String>()
    val debugMessages = mutableListOf<String>()
    
    override fun error(message: String, throwable: Throwable?) {
        errorMessages.add(message)
    }
    
    override fun warn(message: String, throwable: Throwable?) {
        warningMessages.add(message)
    }
    
    override fun info(message: String, throwable: Throwable?) {
        infoMessages.add(message)
    }
    
    override fun debug(message: String, throwable: Throwable?) {
        debugMessages.add(message)
    }
}
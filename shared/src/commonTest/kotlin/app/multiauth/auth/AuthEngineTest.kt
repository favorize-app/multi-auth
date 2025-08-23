package app.multiauth.auth

import app.multiauth.events.AuthEvent
import app.multiauth.events.EventBus
import app.multiauth.models.AuthState
import app.multiauth.models.User
import app.multiauth.storage.SecureStorage
import app.multiauth.util.Logger
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AuthEngineTest {
    
    private lateinit var authEngine: AuthEngine
    private lateinit var mockEventBus: MockEventBus
    private lateinit var mockStorage: MockSecureStorage
    private lateinit var mockLogger: MockLogger
    
    @BeforeTest
    fun setup() {
        mockEventBus = MockEventBus()
        mockStorage = MockSecureStorage()
        mockLogger = MockLogger()
        
        authEngine = AuthEngine(
            eventBus = mockEventBus,
            storage = mockStorage,
            logger = mockLogger
        )
    }
    
    @Test
    fun `test initialization sets correct initial state`() = runTest {
        assertEquals(AuthState.UNAUTHENTICATED, authEngine.currentState)
        assertNull(authEngine.currentUser)
        assertFalse(authEngine.isAuthenticated)
    }
    
    @Test
    fun `test login with valid credentials updates state correctly`() = runTest {
        // Given
        val email = "test@example.com"
        val password = "password123"
        val user = User(
            id = "user123",
            email = email,
            displayName = "Test User"
        )
        
        // When
        val result = authEngine.login(email, password)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(AuthState.AUTHENTICATED, authEngine.currentState)
        assertEquals(user, authEngine.currentUser)
        assertTrue(authEngine.isAuthenticated)
        
        // Verify event was published
        assertEquals(1, mockEventBus.publishedEvents.size)
        val event = mockEventBus.publishedEvents[0]
        assertTrue(event is AuthEvent.Login)
        assertEquals(email, (event as AuthEvent.Login).email)
    }
    
    @Test
    fun `test login with invalid credentials returns failure`() = runTest {
        // Given
        val email = "invalid@example.com"
        val password = "wrongpassword"
        
        // When
        val result = authEngine.login(email, password)
        
        // Then
        assertFalse(result.isSuccess)
        assertEquals(AuthState.UNAUTHENTICATED, authEngine.currentState)
        assertNull(authEngine.currentUser)
        assertFalse(authEngine.isAuthenticated)
        
        // Verify failure event was published
        assertEquals(1, mockEventBus.publishedEvents.size)
        val event = mockEventBus.publishedEvents[0]
        assertTrue(event is AuthEvent.LoginFailure)
        assertEquals(email, (event as AuthEvent.LoginFailure).email)
    }
    
    @Test
    fun `test logout clears state correctly`() = runTest {
        // Given - user is logged in
        authEngine.login("test@example.com", "password123")
        assertEquals(AuthState.AUTHENTICATED, authEngine.currentState)
        
        // When
        authEngine.logout()
        
        // Then
        assertEquals(AuthState.UNAUTHENTICATED, authEngine.currentState)
        assertNull(authEngine.currentUser)
        assertFalse(authEngine.isAuthenticated)
        
        // Verify logout event was published
        assertEquals(2, mockEventBus.publishedEvents.size) // login + logout
        val logoutEvent = mockEventBus.publishedEvents[1]
        assertTrue(logoutEvent is AuthEvent.Logout)
    }
    
    @Test
    fun `test register creates new user successfully`() = runTest {
        // Given
        val email = "newuser@example.com"
        val password = "newpassword123"
        val displayName = "New User"
        
        // When
        val result = authEngine.register(email, password, displayName)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(AuthState.AUTHENTICATED, authEngine.currentState)
        assertNotNull(authEngine.currentUser)
        assertEquals(email, authEngine.currentUser?.email)
        assertEquals(displayName, authEngine.currentUser?.displayName)
        
        // Verify register event was published
        assertEquals(1, mockEventBus.publishedEvents.size)
        val event = mockEventBus.publishedEvents[0]
        assertTrue(event is AuthEvent.Register)
        assertEquals(email, (event as AuthEvent.Register).email)
    }
    
    @Test
    fun `test register with existing email returns failure`() = runTest {
        // Given - user already exists
        authEngine.register("existing@example.com", "password123", "Existing User")
        
        // When
        val result = authEngine.register("existing@example.com", "newpassword", "Another User")
        
        // Then
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("already exists") == true)
        
        // Verify register failure event was published
        assertEquals(2, mockEventBus.publishedEvents.size) // first register + register failure
        val event = mockEventBus.publishedEvents[1]
        assertTrue(event is AuthEvent.RegisterFailure)
        assertEquals("existing@example.com", (event as AuthEvent.RegisterFailure).email)
    }
    
    @Test
    fun `test forgot password sends reset email`() = runTest {
        // Given
        val email = "user@example.com"
        
        // When
        val result = authEngine.forgotPassword(email)
        
        // Then
        assertTrue(result.isSuccess)
        
        // Verify forgot password event was published
        assertEquals(1, mockEventBus.publishedEvents.size)
        val event = mockEventBus.publishedEvents[0]
        assertTrue(event is AuthEvent.ForgotPassword)
        assertEquals(email, (event as AuthEvent.ForgotPassword).email)
    }
    
    @Test
    fun `test reset password with valid token updates password`() = runTest {
        // Given
        val token = "valid-reset-token"
        val newPassword = "newpassword123"
        
        // When
        val result = authEngine.resetPassword(token, newPassword)
        
        // Then
        assertTrue(result.isSuccess)
        
        // Verify reset password event was published
        assertEquals(1, mockEventBus.publishedEvents.size)
        val event = mockEventBus.publishedEvents[0]
        assertTrue(event is AuthEvent.PasswordReset)
        assertEquals(token, (event as AuthEvent.PasswordReset).token)
    }
    
    @Test
    fun `test reset password with invalid token returns failure`() = runTest {
        // Given
        val token = "invalid-token"
        val newPassword = "newpassword123"
        
        // When
        val result = authEngine.resetPassword(token, newPassword)
        
        // Then
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("invalid") == true)
        
        // Verify reset password failure event was published
        assertEquals(1, mockEventBus.publishedEvents.size)
        val event = mockEventBus.publishedEvents[0]
        assertTrue(event is AuthEvent.PasswordResetFailure)
        assertEquals(token, (event as AuthEvent.PasswordResetFailure).token)
    }
    
    @Test
    fun `test change password updates password successfully`() = runTest {
        // Given - user is logged in
        authEngine.login("test@example.com", "oldpassword")
        val oldPassword = "oldpassword"
        val newPassword = "newpassword123"
        
        // When
        val result = authEngine.changePassword(oldPassword, newPassword)
        
        // Then
        assertTrue(result.isSuccess)
        
        // Verify change password event was published
        assertEquals(2, mockEventBus.publishedEvents.size) // login + change password
        val event = mockEventBus.publishedEvents[1]
        assertTrue(event is AuthEvent.PasswordChanged)
    }
    
    @Test
    fun `test change password with wrong old password returns failure`() = runTest {
        // Given - user is logged in
        authEngine.login("test@example.com", "correctpassword")
        val wrongOldPassword = "wrongpassword"
        val newPassword = "newpassword123"
        
        // When
        val result = authEngine.changePassword(wrongOldPassword, newPassword)
        
        // Then
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("incorrect") == true)
        
        // Verify change password failure event was published
        assertEquals(2, mockEventBus.publishedEvents.size) // login + change password failure
        val event = mockEventBus.publishedEvents[1]
        assertTrue(event is AuthEvent.PasswordChangeFailure)
    }
    
    @Test
    fun `test refresh token updates authentication state`() = runTest {
        // Given - user is logged in
        authEngine.login("test@example.com", "password123")
        val refreshToken = "valid-refresh-token"
        
        // When
        val result = authEngine.refreshToken(refreshToken)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(AuthState.AUTHENTICATED, authEngine.currentState)
        assertTrue(authEngine.isAuthenticated)
        
        // Verify refresh token event was published
        assertEquals(2, mockEventBus.publishedEvents.size) // login + refresh token
        val event = mockEventBus.publishedEvents[1]
        assertTrue(event is AuthEvent.TokenRefreshed)
        assertEquals(refreshToken, (event as AuthEvent.TokenRefreshed).refreshToken)
    }
    
    @Test
    fun `test refresh token with invalid token returns failure`() = runTest {
        // Given - user is logged in
        authEngine.login("test@example.com", "password123")
        val invalidToken = "invalid-refresh-token"
        
        // When
        val result = authEngine.refreshToken(invalidToken)
        
        // Then
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("invalid") == true)
        
        // Verify refresh token failure event was published
        assertEquals(2, mockEventBus.publishedEvents.size) // login + refresh token failure
        val event = mockEventBus.publishedEvents[1]
        assertTrue(event is AuthEvent.TokenRefreshFailure)
        assertEquals(invalidToken, (event as AuthEvent.TokenRefreshFailure).refreshToken)
    }
    
    @Test
    fun `test get current user returns correct user`() = runTest {
        // Given - user is logged in
        val email = "test@example.com"
        val displayName = "Test User"
        authEngine.login(email, "password123")
        
        // When
        val currentUser = authEngine.currentUser
        
        // Then
        assertNotNull(currentUser)
        assertEquals(email, currentUser.email)
        assertEquals(displayName, currentUser.displayName)
    }
    
    @Test
    fun `test get current state returns correct state`() = runTest {
        // Given - initial state
        assertEquals(AuthState.UNAUTHENTICATED, authEngine.currentState)
        
        // When - user logs in
        authEngine.login("test@example.com", "password123")
        
        // Then
        assertEquals(AuthState.AUTHENTICATED, authEngine.currentState)
        
        // When - user logs out
        authEngine.logout()
        
        // Then
        assertEquals(AuthState.UNAUTHENTICATED, authEngine.currentState)
    }
    
    @Test
    fun `test is authenticated returns correct boolean`() = runTest {
        // Given - initial state
        assertFalse(authEngine.isAuthenticated)
        
        // When - user logs in
        authEngine.login("test@example.com", "password123")
        
        // Then
        assertTrue(authEngine.isAuthenticated)
        
        // When - user logs out
        authEngine.logout()
        
        // Then
        assertFalse(authEngine.isAuthenticated)
    }
    
    @Test
    fun `test event bus subscription receives events`() = runTest {
        // Given
        val receivedEvents = mutableListOf<AuthEvent>()
        authEngine.subscribe { event ->
            receivedEvents.add(event)
        }
        
        // When
        authEngine.login("test@example.com", "password123")
        
        // Then
        assertEquals(1, receivedEvents.size)
        assertTrue(receivedEvents[0] is AuthEvent.Login)
    }
    
    @Test
    fun `test multiple event bus subscriptions work correctly`() = runTest {
        // Given
        val events1 = mutableListOf<AuthEvent>()
        val events2 = mutableListOf<AuthEvent>()
        
        authEngine.subscribe { event -> events1.add(event) }
        authEngine.subscribe { event -> events2.add(event) }
        
        // When
        authEngine.login("test@example.com", "password123")
        
        // Then
        assertEquals(1, events1.size)
        assertEquals(1, events2.size)
        assertTrue(events1[0] is AuthEvent.Login)
        assertTrue(events2[0] is AuthEvent.Login)
    }
    
    @Test
    fun `test unsubscribe removes event listener`() = runTest {
        // Given
        val events = mutableListOf<AuthEvent>()
        val subscription = authEngine.subscribe { event -> events.add(event) }
        
        // When - subscribe and receive event
        authEngine.login("test@example.com", "password123")
        assertEquals(1, events.size)
        
        // Then - unsubscribe and verify no more events
        authEngine.unsubscribe(subscription)
        authEngine.logout()
        assertEquals(1, events.size) // Should not increase
    }
    
    @Test
    fun `test error handling for storage failures`() = runTest {
        // Given - storage is configured to fail
        mockStorage.shouldFail = true
        
        // When
        val result = authEngine.login("test@example.com", "password123")
        
        // Then
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("storage") == true)
        
        // Verify error was logged
        assertTrue(mockLogger.errorMessages.isNotEmpty())
    }
    
    @Test
    fun `test concurrent operations are handled correctly`() = runTest {
        // Given
        val results = mutableListOf<Result<User>>()
        
        // When - multiple concurrent logins
        val jobs = List(5) { index ->
            kotlinx.coroutines.async {
                authEngine.login("user$index@example.com", "password123")
            }
        }
        
        jobs.forEach { job ->
            results.add(job.await())
        }
        
        // Then - all operations should complete
        assertEquals(5, results.size)
        results.forEach { result ->
            assertTrue(result.isSuccess)
        }
    }
    
    @Test
    fun `test edge cases with empty strings`() = runTest {
        // Given
        val emptyEmail = ""
        val emptyPassword = ""
        val emptyDisplayName = ""
        
        // When & Then
        val loginResult = authEngine.login(emptyEmail, emptyPassword)
        assertFalse(loginResult.isSuccess)
        
        val registerResult = authEngine.register(emptyEmail, emptyPassword, emptyDisplayName)
        assertFalse(registerResult.isSuccess)
    }
    
    @Test
    fun `test edge cases with very long strings`() = runTest {
        // Given
        val longEmail = "a".repeat(1000) + "@example.com"
        val longPassword = "a".repeat(1000)
        val longDisplayName = "a".repeat(1000)
        
        // When & Then
        val loginResult = authEngine.login(longEmail, longPassword)
        assertFalse(loginResult.isSuccess)
        
        val registerResult = authEngine.register(longEmail, longPassword, longDisplayName)
        assertFalse(registerResult.isSuccess)
    }
    
    @Test
    fun `test edge cases with special characters`() = runTest {
        // Given
        val specialEmail = "test+special@example.com"
        val specialPassword = "password!@#\$%^&*()"
        val specialDisplayName = "Test User <>&\"'"
        
        // When
        val result = authEngine.register(specialEmail, specialPassword, specialDisplayName)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(specialEmail, authEngine.currentUser?.email)
        assertEquals(specialDisplayName, authEngine.currentUser?.displayName)
    }
}

// Mock classes for testing

class MockEventBus : EventBus {
    val publishedEvents = mutableListOf<AuthEvent>()
    
    override suspend fun publish(event: AuthEvent) {
        publishedEvents.add(event)
    }
    
    override fun subscribe(listener: (AuthEvent) -> Unit): String {
        return "mock-subscription-id"
    }
    
    override fun unsubscribe(subscriptionId: String) {
        // Mock implementation
    }
}

class MockSecureStorage : SecureStorage {
    var shouldFail = false
    private val storage = mutableMapOf<String, String>()
    
    override suspend fun store(key: String, value: String): Boolean {
        if (shouldFail) return false
        storage[key] = value
        return true
    }
    
    override suspend fun retrieve(key: String): String? {
        if (shouldFail) return null
        return storage[key]
    }
    
    override suspend fun remove(key: String): Boolean {
        if (shouldFail) return false
        storage.remove(key)
        return true
    }
    
    override suspend fun contains(key: String): Boolean {
        if (shouldFail) return false
        return storage.containsKey(key)
    }
    
    override suspend fun clear(): Boolean {
        if (shouldFail) return false
        storage.clear()
        return true
    }
    
    override suspend fun getAllKeys(): List<String> {
        if (shouldFail) return emptyList()
        return storage.keys.toList()
    }
    
    override suspend fun getItemCount(): Int {
        if (shouldFail) return 0
        return storage.size
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
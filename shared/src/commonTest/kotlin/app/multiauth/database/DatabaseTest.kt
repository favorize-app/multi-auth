package app.multiauth.database

import app.multiauth.models.OAuthAccount
import app.multiauth.models.Session
import app.multiauth.models.User
import app.multiauth.util.Logger
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DatabaseTest {
    
    private lateinit var database: Database
    private lateinit var mockLogger: MockLogger
    
    @BeforeTest
    fun setup() {
        mockLogger = MockLogger()
        database = SqliteDatabase(mockLogger)
    }
    
    @AfterTest
    fun cleanup() {
        database.close()
    }
    
    @Test
    fun `test createUser creates user successfully`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        
        // When
        val result = database.createUser(user)
        
        // Then
        assertTrue(result.isSuccess)
        val createdUser = result.getOrNull()
        assertNotNull(createdUser)
        assertEquals(user.id, createdUser.id)
        assertEquals(user.email, createdUser.email)
        assertEquals(user.displayName, createdUser.displayName)
    }
    
    @Test
    fun `test createUser with duplicate email returns failure`() = runTest {
        // Given
        val user1 = User(
            id = "user1",
            email = "duplicate@example.com",
            displayName = "User 1"
        )
        val user2 = User(
            id = "user2",
            email = "duplicate@example.com",
            displayName = "User 2"
        )
        
        // When
        val result1 = database.createUser(user1)
        val result2 = database.createUser(user2)
        
        // Then
        assertTrue(result1.isSuccess)
        assertFalse(result2.isSuccess)
        assertTrue(result2.exceptionOrNull()?.message?.contains("already exists") == true)
    }
    
    @Test
    fun `test getUserById returns correct user`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        // When
        val result = database.getUserById(user.id)
        
        // Then
        assertTrue(result.isSuccess)
        val retrievedUser = result.getOrNull()
        assertNotNull(retrievedUser)
        assertEquals(user.id, retrievedUser.id)
        assertEquals(user.email, retrievedUser.email)
        assertEquals(user.displayName, retrievedUser.displayName)
    }
    
    @Test
    fun `test getUserById with non-existent id returns null`() = runTest {
        // Given
        val nonExistentId = "non-existent-id"
        
        // When
        val result = database.getUserById(nonExistentId)
        
        // Then
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }
    
    @Test
    fun `test getUserByEmail returns correct user`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        // When
        val result = database.getUserByEmail(user.email)
        
        // Then
        assertTrue(result.isSuccess)
        val retrievedUser = result.getOrNull()
        assertNotNull(retrievedUser)
        assertEquals(user.id, retrievedUser.id)
        assertEquals(user.email, retrievedUser.email)
        assertEquals(user.displayName, retrievedUser.displayName)
    }
    
    @Test
    fun `test getUserByEmail with non-existent email returns null`() = runTest {
        // Given
        val nonExistentEmail = "non-existent@example.com"
        
        // When
        val result = database.getUserByEmail(nonExistentEmail)
        
        // Then
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }
    
    @Test
    fun `test updateUser updates user successfully`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        val updatedUser = user.copy(displayName = "Updated User")
        
        // When
        val result = database.updateUser(updatedUser)
        
        // Then
        assertTrue(result.isSuccess)
        
        // Verify update
        val retrievedUser = database.getUserById(user.id).getOrNull()
        assertNotNull(retrievedUser)
        assertEquals("Updated User", retrievedUser.displayName)
    }
    
    @Test
    fun `test updateUser with non-existent user returns failure`() = runTest {
        // Given
        val nonExistentUser = User(
            id = "non-existent",
            email = "test@example.com",
            displayName = "Test User"
        )
        
        // When
        val result = database.updateUser(nonExistentUser)
        
        // Then
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
    }
    
    @Test
    fun `test deleteUser deletes user successfully`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        // Verify user exists
        assertNotNull(database.getUserById(user.id).getOrNull())
        
        // When
        val result = database.deleteUser(user.id)
        
        // Then
        assertTrue(result.isSuccess)
        
        // Verify user is deleted
        assertNull(database.getUserById(user.id).getOrNull())
    }
    
    @Test
    fun `test deleteUser with non-existent id returns failure`() = runTest {
        // Given
        val nonExistentId = "non-existent-id"
        
        // When
        val result = database.deleteUser(nonExistentId)
        
        // Then
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
    }
    
    @Test
    fun `test createOAuthAccount creates account successfully`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        val oauthAccount = OAuthAccount(
            id = "oauth123",
            userId = user.id,
            provider = "google",
            providerUserId = "google123",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = System.currentTimeMillis() + 3600000
        )
        
        // When
        val result = database.createOAuthAccount(oauthAccount)
        
        // Then
        assertTrue(result.isSuccess)
        val createdAccount = result.getOrNull()
        assertNotNull(createdAccount)
        assertEquals(oauthAccount.id, createdAccount.id)
        assertEquals(oauthAccount.userId, createdAccount.userId)
        assertEquals(oauthAccount.provider, createdAccount.provider)
    }
    
    @Test
    fun `test createOAuthAccount with duplicate provider and providerUserId returns failure`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        val oauthAccount1 = OAuthAccount(
            id = "oauth1",
            userId = user.id,
            provider = "google",
            providerUserId = "google123",
            accessToken = "access-token-1",
            refreshToken = "refresh-token-1",
            expiresAt = System.currentTimeMillis() + 3600000
        )
        
        val oauthAccount2 = OAuthAccount(
            id = "oauth2",
            userId = user.id,
            provider = "google",
            providerUserId = "google123",
            accessToken = "access-token-2",
            refreshToken = "refresh-token-2",
            expiresAt = System.currentTimeMillis() + 3600000
        )
        
        // When
        val result1 = database.createOAuthAccount(oauthAccount1)
        val result2 = database.createOAuthAccount(oauthAccount2)
        
        // Then
        assertTrue(result1.isSuccess)
        assertFalse(result2.isSuccess)
        assertTrue(result2.exceptionOrNull()?.message?.contains("already exists") == true)
    }
    
    @Test
    fun `test getOAuthAccountByProviderAndUserId returns correct account`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        val oauthAccount = OAuthAccount(
            id = "oauth123",
            userId = user.id,
            provider = "google",
            providerUserId = "google123",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = System.currentTimeMillis() + 3600000
        )
        database.createOAuthAccount(oauthAccount)
        
        // When
        val result = database.getOAuthAccountByProviderAndUserId("google", "google123")
        
        // Then
        assertTrue(result.isSuccess)
        val retrievedAccount = result.getOrNull()
        assertNotNull(retrievedAccount)
        assertEquals(oauthAccount.id, retrievedAccount.id)
        assertEquals(oauthAccount.provider, retrievedAccount.provider)
        assertEquals(oauthAccount.providerUserId, retrievedAccount.providerUserId)
    }
    
    @Test
    fun `test getOAuthAccountsByUserId returns all accounts for user`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        val oauthAccounts = listOf(
            OAuthAccount(
                id = "oauth1",
                userId = user.id,
                provider = "google",
                providerUserId = "google123",
                accessToken = "access-token-1",
                refreshToken = "refresh-token-1",
                expiresAt = System.currentTimeMillis() + 3600000
            ),
            OAuthAccount(
                id = "oauth2",
                userId = user.id,
                provider = "github",
                providerUserId = "github123",
                accessToken = "access-token-2",
                refreshToken = "refresh-token-2",
                expiresAt = System.currentTimeMillis() + 3600000
            )
        )
        
        oauthAccounts.forEach { account ->
            database.createOAuthAccount(account)
        }
        
        // When
        val result = database.getOAuthAccountsByUserId(user.id)
        
        // Then
        assertTrue(result.isSuccess)
        val retrievedAccounts = result.getOrNull()
        assertNotNull(retrievedAccounts)
        assertEquals(2, retrievedAccounts.size)
        
        val googleAccount = retrievedAccounts.find { it.provider == "google" }
        val githubAccount = retrievedAccounts.find { it.provider == "github" }
        
        assertNotNull(googleAccount)
        assertNotNull(githubAccount)
        assertEquals("google123", googleAccount.providerUserId)
        assertEquals("github123", githubAccount.providerUserId)
    }
    
    @Test
    fun `test updateOAuthAccount updates account successfully`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        val oauthAccount = OAuthAccount(
            id = "oauth123",
            userId = user.id,
            provider = "google",
            providerUserId = "google123",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = System.currentTimeMillis() + 3600000
        )
        database.createOAuthAccount(oauthAccount)
        
        val updatedAccount = oauthAccount.copy(
            accessToken = "new-access-token",
            expiresAt = System.currentTimeMillis() + 7200000
        )
        
        // When
        val result = database.updateOAuthAccount(updatedAccount)
        
        // Then
        assertTrue(result.isSuccess)
        
        // Verify update
        val retrievedAccount = database.getOAuthAccountByProviderAndUserId("google", "google123").getOrNull()
        assertNotNull(retrievedAccount)
        assertEquals("new-access-token", retrievedAccount.accessToken)
        assertTrue(retrievedAccount.expiresAt > oauthAccount.expiresAt)
    }
    
    @Test
    fun `test deleteOAuthAccount deletes account successfully`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        val oauthAccount = OAuthAccount(
            id = "oauth123",
            userId = user.id,
            provider = "google",
            providerUserId = "google123",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = System.currentTimeMillis() + 3600000
        )
        database.createOAuthAccount(oauthAccount)
        
        // Verify account exists
        assertNotNull(database.getOAuthAccountByProviderAndUserId("google", "google123").getOrNull())
        
        // When
        val result = database.deleteOAuthAccount("google", "google123")
        
        // Then
        assertTrue(result.isSuccess)
        
        // Verify account is deleted
        assertNull(database.getOAuthAccountByProviderAndUserId("google", "google123").getOrNull())
    }
    
    @Test
    fun `test createSession creates session successfully`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        val session = Session(
            id = "session123",
            userId = user.id,
            token = "session-token",
            expiresAt = System.currentTimeMillis() + 86400000,
            createdAt = System.currentTimeMillis()
        )
        
        // When
        val result = database.createSession(session)
        
        // Then
        assertTrue(result.isSuccess)
        val createdSession = result.getOrNull()
        assertNotNull(createdSession)
        assertEquals(session.id, createdSession.id)
        assertEquals(session.userId, createdSession.userId)
        assertEquals(session.token, createdSession.token)
    }
    
    @Test
    fun `test getSessionByToken returns correct session`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        val session = Session(
            id = "session123",
            userId = user.id,
            token = "session-token",
            expiresAt = System.currentTimeMillis() + 86400000,
            createdAt = System.currentTimeMillis()
        )
        database.createSession(session)
        
        // When
        val result = database.getSessionByToken("session-token")
        
        // Then
        assertTrue(result.isSuccess)
        val retrievedSession = result.getOrNull()
        assertNotNull(retrievedSession)
        assertEquals(session.id, retrievedSession.id)
        assertEquals(session.userId, retrievedSession.userId)
        assertEquals(session.token, retrievedSession.token)
    }
    
    @Test
    fun `test getSessionByToken with expired session returns null`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        val expiredSession = Session(
            id = "session123",
            userId = user.id,
            token = "expired-token",
            expiresAt = System.currentTimeMillis() - 1000, // Expired
            createdAt = System.currentTimeMillis() - 86400000
        )
        database.createSession(expiredSession)
        
        // When
        val result = database.getSessionByToken("expired-token")
        
        // Then
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }
    
    @Test
    fun `test deleteSession deletes session successfully`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        val session = Session(
            id = "session123",
            userId = user.id,
            token = "session-token",
            expiresAt = System.currentTimeMillis() + 86400000,
            createdAt = System.currentTimeMillis()
        )
        database.createSession(session)
        
        // Verify session exists
        assertNotNull(database.getSessionByToken("session-token").getOrNull())
        
        // When
        val result = database.deleteSession("session-token")
        
        // Then
        assertTrue(result.isSuccess)
        
        // Verify session is deleted
        assertNull(database.getSessionByToken("session-token").getOrNull())
    }
    
    @Test
    fun `test deleteExpiredSessions removes expired sessions`() = runTest {
        // Given
        val user = User(
            id = "user123",
            email = "test@example.com",
            displayName = "Test User"
        )
        database.createUser(user)
        
        val validSession = Session(
            id = "valid-session",
            userId = user.id,
            token = "valid-token",
            expiresAt = System.currentTimeMillis() + 86400000,
            createdAt = System.currentTimeMillis()
        )
        
        val expiredSession = Session(
            id = "expired-session",
            userId = user.id,
            token = "expired-token",
            expiresAt = System.currentTimeMillis() - 1000,
            createdAt = System.currentTimeMillis() - 86400000
        )
        
        database.createSession(validSession)
        database.createSession(expiredSession)
        
        // Verify both sessions exist
        assertNotNull(database.getSessionByToken("valid-token").getOrNull())
        assertNotNull(database.getSessionByToken("expired-token").getOrNull())
        
        // When
        val result = database.deleteExpiredSessions()
        
        // Then
        assertTrue(result.isSuccess)
        val deletedCount = result.getOrNull() ?: 0
        assertTrue(deletedCount > 0)
        
        // Verify expired session is deleted, valid session remains
        assertNotNull(database.getSessionByToken("valid-token").getOrNull())
        assertNull(database.getSessionByToken("expired-token").getOrNull())
    }
    
    @Test
    fun `test createAuditLog creates log entry successfully`() = runTest {
        // Given
        val auditLog = AuditLog(
            id = "log123",
            userId = "user123",
            action = "login",
            details = "User logged in successfully",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            timestamp = System.currentTimeMillis()
        )
        
        // When
        val result = database.createAuditLog(auditLog)
        
        // Then
        assertTrue(result.isSuccess)
        val createdLog = result.getOrNull()
        assertNotNull(createdLog)
        assertEquals(auditLog.id, createdLog.id)
        assertEquals(auditLog.userId, createdLog.userId)
        assertEquals(auditLog.action, createdLog.action)
    }
    
    @Test
    fun `test getAuditLogsByUserId returns logs for user`() = runTest {
        // Given
        val userId = "user123"
        val auditLogs = listOf(
            AuditLog(
                id = "log1",
                userId = userId,
                action = "login",
                details = "User logged in",
                ipAddress = "192.168.1.1",
                userAgent = "Mozilla/5.0",
                timestamp = System.currentTimeMillis()
            ),
            AuditLog(
                id = "log2",
                userId = userId,
                action = "logout",
                details = "User logged out",
                ipAddress = "192.168.1.1",
                userAgent = "Mozilla/5.0",
                timestamp = System.currentTimeMillis() + 1000
            )
        )
        
        auditLogs.forEach { log ->
            database.createAuditLog(log)
        }
        
        // When
        val result = database.getAuditLogsByUserId(userId, limit = 10, offset = 0)
        
        // Then
        assertTrue(result.isSuccess)
        val retrievedLogs = result.getOrNull()
        assertNotNull(retrievedLogs)
        assertEquals(2, retrievedLogs.size)
        
        // Logs should be ordered by timestamp (newest first)
        assertTrue(retrievedLogs[0].timestamp > retrievedLogs[1].timestamp)
    }
    
    @Test
    fun `test getAuditLogsByAction returns logs for action`() = runTest {
        // Given
        val action = "login"
        val auditLogs = listOf(
            AuditLog(
                id = "log1",
                userId = "user1",
                action = action,
                details = "User 1 logged in",
                ipAddress = "192.168.1.1",
                userAgent = "Mozilla/5.0",
                timestamp = System.currentTimeMillis()
            ),
            AuditLog(
                id = "log2",
                userId = "user2",
                action = action,
                details = "User 2 logged in",
                ipAddress = "192.168.1.2",
                userAgent = "Mozilla/5.0",
                timestamp = System.currentTimeMillis() + 1000
            )
        )
        
        auditLogs.forEach { log ->
            database.createAuditLog(log)
        }
        
        // When
        val result = database.getAuditLogsByAction(action, limit = 10, offset = 0)
        
        // Then
        assertTrue(result.isSuccess)
        val retrievedLogs = result.getOrNull()
        assertNotNull(retrievedLogs)
        assertEquals(2, retrievedLogs.size)
        
        retrievedLogs.forEach { log ->
            assertEquals(action, log.action)
        }
    }
    
    @Test
    fun `test database operations with large datasets`() = runTest {
        // Given
        val users = mutableListOf<User>()
        val oauthAccounts = mutableListOf<OAuthAccount>()
        val sessions = mutableListOf<Session>()
        val auditLogs = mutableListOf<AuditLog>()
        
        // Create 100 users with associated data
        repeat(100) { index ->
            val user = User(
                id = "user$index",
                email = "user$index@example.com",
                displayName = "User $index"
            )
            users.add(user)
            database.createUser(user)
            
            // Create OAuth account for each user
            val oauthAccount = OAuthAccount(
                id = "oauth$index",
                userId = user.id,
                provider = if (index % 2 == 0) "google" else "github",
                providerUserId = "provider$index",
                accessToken = "token$index",
                refreshToken = "refresh$index",
                expiresAt = System.currentTimeMillis() + 3600000
            )
            oauthAccounts.add(oauthAccount)
            database.createOAuthAccount(oauthAccount)
            
            // Create session for each user
            val session = Session(
                id = "session$index",
                userId = user.id,
                token = "session-token$index",
                expiresAt = System.currentTimeMillis() + 86400000,
                createdAt = System.currentTimeMillis()
            )
            sessions.add(session)
            database.createSession(session)
            
            // Create audit log for each user
            val auditLog = AuditLog(
                id = "log$index",
                userId = user.id,
                action = "login",
                details = "User $index logged in",
                ipAddress = "192.168.1.$index",
                userAgent = "Mozilla/5.0",
                timestamp = System.currentTimeMillis() + index
            )
            auditLogs.add(auditLog)
            database.createAuditLog(auditLog)
        }
        
        // When & Then - verify all data was created
        users.forEach { user ->
            val retrievedUser = database.getUserById(user.id).getOrNull()
            assertNotNull(retrievedUser)
            assertEquals(user.email, retrievedUser.email)
        }
        
        oauthAccounts.forEach { account ->
            val retrievedAccount = database.getOAuthAccountByProviderAndUserId(account.provider, account.providerUserId).getOrNull()
            assertNotNull(retrievedAccount)
            assertEquals(account.userId, retrievedAccount.userId)
        }
        
        sessions.forEach { session ->
            val retrievedSession = database.getSessionByToken(session.token).getOrNull()
            assertNotNull(retrievedSession)
            assertEquals(session.userId, retrievedSession.userId)
        }
        
        // Test pagination
        val paginatedLogs = database.getAuditLogsByUserId("user0", limit = 5, offset = 0).getOrNull()
        assertNotNull(paginatedLogs)
        assertTrue(paginatedLogs.size <= 5)
    }
    
    @Test
    fun `test concurrent database operations`() = runTest {
        // Given
        val results = mutableListOf<Result<User>>()
        
        // When - multiple concurrent user creations
        val jobs = List(50) { index ->
            kotlinx.coroutines.async {
                val user = User(
                    id = "concurrent-user$index",
                    email = "concurrent$index@example.com",
                    displayName = "Concurrent User $index"
                )
                database.createUser(user)
            }
        }
        
        jobs.forEach { job ->
            results.add(job.await())
        }
        
        // Then - all operations should complete
        assertEquals(50, results.size)
        results.forEach { result ->
            assertTrue(result.isSuccess)
        }
        
        // Verify all users were created
        repeat(50) { index ->
            val user = database.getUserById("concurrent-user$index").getOrNull()
            assertNotNull(user)
            assertEquals("concurrent$index@example.com", user.email)
        }
    }
    
    @Test
    fun `test database error handling`() = runTest {
        // Given - database is closed
        database.close()
        
        // When & Then - operations should fail gracefully
        val user = User(
            id = "test-user",
            email = "test@example.com",
            displayName = "Test User"
        )
        
        val createResult = database.createUser(user)
        assertFalse(createResult.isSuccess)
        
        val getUserResult = database.getUserById("test-user")
        assertFalse(getUserResult.isSuccess)
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
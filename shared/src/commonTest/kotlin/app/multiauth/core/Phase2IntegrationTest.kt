package app.multiauth.core

import app.multiauth.providers.impl.SmtpEmailProvider
import app.multiauth.providers.impl.TwilioSmsProvider
import app.multiauth.providers.impl.SmtpEmailConfig
import app.multiauth.providers.impl.TwilioSmsConfig
import app.multiauth.providers.impl.EmailServiceProvider
import app.multiauth.providers.impl.SmsServiceProvider
import app.multiauth.storage.StorageFactory
import app.multiauth.models.User
import app.multiauth.models.TokenPair
import app.multiauth.models.AuthMethod
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import io.ktor.client.HttpClient

/**
 * Integration tests for Phase 2 implementations:
 * - Real email service integration
 * - Real SMS service integration  
 * - Session management
 * - Token refresh service
 */
class Phase2IntegrationTest {
    
    @Test
    fun testEmailServiceIntegration() {
        val httpClient = HttpClient()
        val emailConfig = SmtpEmailConfig(
            provider = EmailServiceProvider.SENDGRID,
            apiKey = "test_api_key",
            fromEmail = "test@example.com",
            fromName = "Test App"
        )
        
        val emailProvider = SmtpEmailProvider(emailConfig, httpClient)
        
        // Test provider info
        val providerInfo = emailProvider.getProviderInfo()
        assertEquals("SMTP Email Provider", providerInfo.name)
        assertTrue(providerInfo.supportsVerification)
        assertTrue(providerInfo.supportsPasswordReset)
        assertTrue(providerInfo.supportsWelcomeEmails)
        assertTrue(providerInfo.supportsSecurityAlerts)
    }
    
    @Test
    fun testSmsServiceIntegration() {
        val httpClient = HttpClient()
        val smsConfig = TwilioSmsConfig(
            provider = SmsServiceProvider.MOCK, // Use mock for testing
            accountSid = "test_account_sid",
            authToken = "test_auth_token",
            fromPhoneNumber = "+1234567890"
        )
        
        val smsProvider = TwilioSmsProvider(smsConfig, httpClient)
        
        // Test provider info
        val providerInfo = smsProvider.getProviderInfo()
        assertEquals("Twilio SMS Provider", providerInfo.name)
        assertTrue(providerInfo.supportsVerification)
        assertTrue(providerInfo.supportsSecurityAlerts)
        assertTrue(providerInfo.supportedCountries.isNotEmpty())
    }
    
    @Test
    fun testSessionManagerCreation() {
        val secureStorage = StorageFactory.createMockStorage()
        val sessionManager = SessionManager(secureStorage)
        
        // Test initial state
        assertNull(sessionManager.currentSession.value)
        assertEquals(false, sessionManager.isSessionValid.value)
        assertNull(sessionManager.getCurrentUser())
    }
    
    @Test
    fun testSessionCreationAndValidation() = kotlinx.coroutines.test.runTest {
        val secureStorage = StorageFactory.createMockStorage()
        val sessionManager = SessionManager(secureStorage)
        
        // Create test user and tokens
        val user = User(
            id = "test_user_123",
            email = "test@example.com",
            displayName = "Test User",
            emailVerified = true,
            createdAt = Clock.System.now(),
            authMethods = listOf(AuthMethod.Email("test@example.com", true, Clock.System.now()))
        )
        
        val tokens = TokenPair(
            accessToken = "test_access_token",
            refreshToken = "test_refresh_token",
            expiresAt = Clock.System.now() + 30.minutes
        )
        
        // Create session
        when (val result = sessionManager.createSession(user, tokens)) {
            is app.multiauth.models.AuthResult.Success -> {
                val session = result.data
                assertNotNull(session)
                assertEquals(user.id, session.user.id)
                assertEquals(user.email, session.user.email)
                assertTrue(session.isActive)
                assertNotNull(session.sessionId)
            }
            else -> throw AssertionError("Session creation should succeed")
        }
        
        // Verify session is now active
        assertNotNull(sessionManager.currentSession.value)
        assertEquals(user, sessionManager.getCurrentUser())
    }
    
    @Test
    fun testTokenRefreshService() = kotlinx.coroutines.test.runTest {
        val secureStorage = StorageFactory.createMockStorage()
        val sessionManager = SessionManager(secureStorage)
        val tokenRefreshService = TokenRefreshService(sessionManager)
        
        // Test initial state
        assertEquals(false, tokenRefreshService.getRefreshStats().isAutoRefreshActive)
        
        // Test starting and stopping auto refresh
        tokenRefreshService.startAutoRefresh()
        assertTrue(tokenRefreshService.getRefreshStats().isAutoRefreshActive)
        
        tokenRefreshService.stopAutoRefresh()
        assertEquals(false, tokenRefreshService.getRefreshStats().isAutoRefreshActive)
    }
    
    @Test
    fun testTokenRefreshServiceStats() {
        val secureStorage = StorageFactory.createMockStorage()
        val sessionManager = SessionManager(secureStorage)
        val tokenRefreshService = TokenRefreshService(sessionManager)
        
        val stats = tokenRefreshService.getRefreshStats()
        assertNotNull(stats.refreshThreshold)
        assertNotNull(stats.checkInterval)
        assertNotNull(stats.currentStatus)
        assertEquals(false, stats.isAutoRefreshActive)
    }
    
    @Test
    fun testSessionInfoRetrieval() = kotlinx.coroutines.test.runTest {
        val secureStorage = StorageFactory.createMockStorage()
        val sessionManager = SessionManager(secureStorage)
        
        // Test when no session exists
        val noSessionInfo = sessionManager.getSessionInfo()
        assertNull(noSessionInfo)
        
        // Create a session first
        val user = User(
            id = "test_user_456",
            email = "info@example.com",
            displayName = "Info Test User",
            emailVerified = true,
            createdAt = Clock.System.now(),
            authMethods = listOf(AuthMethod.Email("info@example.com", true, Clock.System.now()))
        )
        
        val tokens = TokenPair(
            accessToken = "info_access_token",
            refreshToken = "info_refresh_token",
            expiresAt = Clock.System.now() + 30.minutes
        )
        
        when (sessionManager.createSession(user, tokens)) {
            is app.multiauth.models.AuthResult.Success -> {
                val sessionInfo = sessionManager.getSessionInfo()
                assertNotNull(sessionInfo)
                assertEquals(user.id, sessionInfo?.userId)
                assertEquals(user.email, sessionInfo?.userEmail)
                assertNotNull(sessionInfo?.sessionId)
                assertNotNull(sessionInfo?.deviceInfo)
            }
            else -> throw AssertionError("Session creation should succeed for info test")
        }
    }
    
    @Test
    fun testSessionInvalidation() = kotlinx.coroutines.test.runTest {
        val secureStorage = StorageFactory.createMockStorage()
        val sessionManager = SessionManager(secureStorage)
        
        // Create a session
        val user = User(
            id = "test_user_789",
            email = "invalidate@example.com",
            displayName = "Invalidate Test User",
            emailVerified = true,
            createdAt = Clock.System.now(),
            authMethods = listOf(AuthMethod.Email("invalidate@example.com", true, Clock.System.now()))
        )
        
        val tokens = TokenPair(
            accessToken = "invalidate_access_token",
            refreshToken = "invalidate_refresh_token",
            expiresAt = Clock.System.now() + 30.minutes
        )
        
        // Create and then invalidate session
        when (sessionManager.createSession(user, tokens)) {
            is app.multiauth.models.AuthResult.Success -> {
                // Verify session exists
                assertNotNull(sessionManager.currentSession.value)
                
                // Invalidate session
                when (val invalidateResult = sessionManager.invalidateSession()) {
                    is app.multiauth.models.AuthResult.Success -> {
                        // Verify session is cleared
                        assertNull(sessionManager.currentSession.value)
                        assertEquals(false, sessionManager.isSessionValid.value)
                        assertNull(sessionManager.getCurrentUser())
                    }
                    else -> throw AssertionError("Session invalidation should succeed")
                }
            }
            else -> throw AssertionError("Session creation should succeed for invalidation test")
        }
    }
    
    @Test
    fun testEmailVerificationFlow() = kotlinx.coroutines.test.runTest {
        val httpClient = HttpClient()
        val emailConfig = SmtpEmailConfig(
            provider = EmailServiceProvider.SENDGRID,
            apiKey = "test_api_key",
            fromEmail = "test@example.com"
        )
        
        val emailProvider = SmtpEmailProvider(emailConfig, httpClient)
        val testEmail = "verification@example.com"
        
        // Test email validation
        when (val validationResult = emailProvider.validateEmail(testEmail)) {
            is app.multiauth.models.AuthResult.Success -> {
                assertTrue(validationResult.data)
            }
            else -> throw AssertionError("Email validation should succeed")
        }
        
        // Test sending verification email (will use mock implementation)
        when (val sendResult = emailProvider.sendVerificationEmail(testEmail)) {
            is app.multiauth.models.AuthResult.Success -> {
                // Verify code was stored
                val storedCode = emailProvider.getStoredVerificationCode(testEmail)
                assertNotNull(storedCode)
                assertTrue(storedCode.length == 6)
                assertTrue(storedCode.all { it.isDigit() })
            }
            else -> throw AssertionError("Sending verification email should succeed")
        }
    }
    
    @Test
    fun testSmsVerificationFlow() = kotlinx.coroutines.test.runTest {
        val httpClient = HttpClient()
        val smsConfig = TwilioSmsConfig(
            provider = SmsServiceProvider.MOCK, // Use mock for testing
            accountSid = "test_account_sid",
            authToken = "test_auth_token",
            fromPhoneNumber = "+1234567890"
        )
        
        val smsProvider = TwilioSmsProvider(smsConfig, httpClient)
        val testPhoneNumber = "+1234567890"
        
        // Test phone number validation
        when (val validationResult = smsProvider.validatePhoneNumber(testPhoneNumber)) {
            is app.multiauth.models.AuthResult.Success -> {
                assertTrue(validationResult.data)
            }
            else -> throw AssertionError("Phone validation should succeed")
        }
        
        // Test sending verification code
        when (val sendResult = smsProvider.sendVerificationCode(testPhoneNumber)) {
            is app.multiauth.models.AuthResult.Success -> {
                val sessionId = sendResult.data
                assertNotNull(sessionId)
                assertTrue(sessionId.isNotEmpty())
                
                // Verify code was stored
                val storedCode = smsProvider.getStoredVerificationCode(sessionId)
                assertNotNull(storedCode)
                assertTrue(storedCode.length == 6)
                assertTrue(storedCode.all { it.isDigit() })
                
                // Test code verification
                when (val verifyResult = smsProvider.verifySmsCode(testPhoneNumber, storedCode, sessionId)) {
                    is app.multiauth.models.AuthResult.Success -> {
                        // Verification should succeed
                    }
                    else -> throw AssertionError("SMS code verification should succeed")
                }
            }
            else -> throw AssertionError("Sending SMS verification should succeed")
        }
    }
}
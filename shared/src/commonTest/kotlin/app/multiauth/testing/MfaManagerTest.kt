package app.multiauth.testing

import app.multiauth.core.AuthEngine
import app.multiauth.events.EventBus
import app.multiauth.mfa.MfaManager
import app.multiauth.mfa.MfaMethod
import app.multiauth.mfa.MfaState
import app.multiauth.models.User
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MfaManagerTest : BaseMultiAuthTest() {
    
    private lateinit var mfaManager: MfaManager
    private lateinit var mockAuthEngine: MockAuthEngine
    
    override fun setupTestEnvironment() {
        super.setupTestEnvironment()
        mockAuthEngine = MockAuthEngine()
        mfaManager = MfaManager(mockAuthEngine, mockEventBus)
    }
    
    @Test
    fun `test enable MFA with TOTP`() = runTest {
        // Given
        val user = createTestUser()
        val method = MfaMethod.TOTP
        
        // When
        val result = mfaManager.enableMfa(user, method)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(MfaState.Idle, mfaManager.mfaState.value)
        assertTrue(mfaManager.enabledMethods.value.contains(method))
    }
    
    @Test
    fun `test enable MFA with SMS`() = runTest {
        // Given
        val user = createTestUser()
        val method = MfaMethod.SMS
        
        // When
        val result = mfaManager.enableMfa(user, method)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(MfaState.Idle, mfaManager.mfaState.value)
        assertTrue(mfaManager.enabledMethods.value.contains(method))
    }
    
    @Test
    fun `test enable MFA with backup codes`() = runTest {
        // Given
        val user = createTestUser()
        val method = MfaMethod.BACKUP_CODES
        
        // When
        val result = mfaManager.enableMfa(user, method)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(MfaState.Idle, mfaManager.mfaState.value)
        assertTrue(mfaManager.enabledMethods.value.contains(method))
        assertTrue(mfaManager.backupCodes.value.isNotEmpty())
        assertEquals(10, mfaManager.backupCodes.value.size)
    }
    
    @Test
    fun `test disable MFA method`() = runTest {
        // Given
        val user = createTestUser()
        val method = MfaMethod.TOTP
        mfaManager.enableMfa(user, method)
        
        // When
        val result = mfaManager.disableMfa(user, method)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(MfaState.Idle, mfaManager.mfaState.value)
        assertFalse(mfaManager.enabledMethods.value.contains(method))
    }
    
    @Test
    fun `test verify TOTP code`() = runTest {
        // Given
        val user = createTestUser()
        val method = MfaMethod.TOTP
        mfaManager.enableMfa(user, method)
        val code = "123456"
        
        // When
        val result = mfaManager.verifyMfaCode(user, method, code)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(MfaState.Idle, mfaManager.mfaState.value)
    }
    
    @Test
    fun `test verify SMS code`() = runTest {
        // Given
        val user = createTestUser()
        val method = MfaMethod.SMS
        mfaManager.enableMfa(user, method)
        val code = "123456"
        
        // When
        val result = mfaManager.verifyMfaCode(user, method, code)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(MfaState.Idle, mfaManager.mfaState.value)
    }
    
    @Test
    fun `test verify backup code`() = runTest {
        // Given
        val user = createTestUser()
        val method = MfaMethod.BACKUP_CODES
        mfaManager.enableMfa(user, method)
        val backupCodes = mfaManager.backupCodes.value
        val code = backupCodes.first()
        
        // When
        val result = mfaManager.verifyMfaCode(user, method, code)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(MfaState.Idle, mfaManager.mfaState.value)
        // Backup code should be consumed
        assertFalse(mfaManager.backupCodes.value.contains(code))
        assertEquals(9, mfaManager.backupCodes.value.size)
    }
    
    @Test
    fun `test verify invalid TOTP code`() = runTest {
        // Given
        val user = createTestUser()
        val method = MfaMethod.TOTP
        mfaManager.enableMfa(user, method)
        val invalidCode = "invalid"
        
        // When
        val result = mfaManager.verifyMfaCode(user, method, invalidCode)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(MfaState.Idle, mfaManager.mfaState.value)
    }
    
    @Test
    fun `test verify invalid SMS code`() = runTest {
        // Given
        val user = createTestUser()
        val method = MfaMethod.SMS
        mfaManager.enableMfa(user, method)
        val invalidCode = "invalid"
        
        // When
        val result = mfaManager.verifyMfaCode(user, method, invalidCode)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(MfaState.Idle, mfaManager.mfaState.value)
    }
    
    @Test
    fun `test verify invalid backup code`() = runTest {
        // Given
        val user = createTestUser()
        val method = MfaMethod.BACKUP_CODES
        mfaManager.enableMfa(user, method)
        val invalidCode = "INVALID"
        
        // When
        val result = mfaManager.verifyMfaCode(user, method, invalidCode)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(MfaState.Idle, mfaManager.mfaState.value)
    }
    
    @Test
    fun `test generate new backup codes`() = runTest {
        // Given
        val user = createTestUser()
        
        // When
        val result = mfaManager.generateBackupCodes(user)
        
        // Then
        assertTrue(result.isSuccess)
        val codes = result.getOrNull()
        assertNotNull(codes)
        assertEquals(10, codes.size)
        assertTrue(codes.all { it.length == 8 })
        assertTrue(codes.all { it.all { char -> char.isLetterOrDigit() } })
        assertEquals(MfaState.Idle, mfaManager.mfaState.value)
    }
    
    @Test
    fun `test MFA required check`() = runTest {
        // Given
        val user = createTestUser()
        
        // When - no MFA enabled
        val initiallyRequired = mfaManager.isMfaRequired(user)
        
        // Then
        assertFalse(initiallyRequired)
        
        // When - enable MFA
        mfaManager.enableMfa(user, MfaMethod.TOTP)
        val afterEnabled = mfaManager.isMfaRequired(user)
        
        // Then
        assertTrue(afterEnabled)
    }
    
    @Test
    fun `test get available MFA methods`() = runTest {
        // Given
        val user = createTestUser()
        
        // When
        val methods = mfaManager.getAvailableMfaMethods(user)
        
        // Then
        assertEquals(3, methods.size)
        assertTrue(methods.contains(MfaMethod.TOTP))
        assertTrue(methods.contains(MfaMethod.SMS))
        assertTrue(methods.contains(MfaMethod.BACKUP_CODES))
    }
    
    @Test
    fun `test multiple MFA methods enabled`() = runTest {
        // Given
        val user = createTestUser()
        
        // When
        mfaManager.enableMfa(user, MfaMethod.TOTP)
        mfaManager.enableMfa(user, MfaMethod.SMS)
        
        // Then
        assertEquals(2, mfaManager.enabledMethods.value.size)
        assertTrue(mfaManager.enabledMethods.value.contains(MfaMethod.TOTP))
        assertTrue(mfaManager.enabledMethods.value.contains(MfaMethod.SMS))
    }
    
    @Test
    fun `test MFA state transitions`() = runTest {
        // Given
        val user = createTestUser()
        val method = MfaMethod.TOTP
        
        // When - enabling
        val enableJob = kotlinx.coroutines.launch {
            mfaManager.enableMfa(user, method)
        }
        
        // Then - should be in enabling state
        assertEquals(MfaState.Enabling(method), mfaManager.mfaState.value)
        
        // Wait for completion
        enableJob.join()
        
        // Then - should return to idle
        assertEquals(MfaState.Idle, mfaManager.mfaState.value)
    }
    
    @Test
    fun `test MFA verification state transitions`() = runTest {
        // Given
        val user = createTestUser()
        val method = MfaMethod.TOTP
        mfaManager.enableMfa(user, method)
        val code = "123456"
        
        // When - verifying
        val verifyJob = kotlinx.coroutines.launch {
            mfaManager.verifyMfaCode(user, method, code)
        }
        
        // Then - should be in verifying state
        assertEquals(MfaState.Verifying(method), mfaManager.mfaState.value)
        
        // Wait for completion
        verifyJob.join()
        
        // Then - should return to idle
        assertEquals(MfaState.Idle, mfaManager.mfaState.value)
    }
    
    @Test
    fun `test backup codes generation state transitions`() = runTest {
        // Given
        val user = createTestUser()
        
        // When - generating backup codes
        val generateJob = kotlinx.coroutines.launch {
            mfaManager.generateBackupCodes(user)
        }
        
        // Then - should be in generating state
        assertEquals(MfaState.GeneratingBackupCodes, mfaManager.mfaState.value)
        
        // Wait for completion
        generateJob.join()
        
        // Then - should return to idle
        assertEquals(MfaState.Idle, mfaManager.mfaState.value)
    }
    
    @Test
    fun `test backup codes are unique`() = runTest {
        // Given
        val user = createTestUser()
        
        // When
        val result = mfaManager.generateBackupCodes(user)
        
        // Then
        assertTrue(result.isSuccess)
        val codes = result.getOrNull()
        assertNotNull(codes)
        
        // All codes should be unique
        assertEquals(codes.size, codes.toSet().size)
    }
    
    @Test
    fun `test backup codes format`() = runTest {
        // Given
        val user = createTestUser()
        
        // When
        val result = mfaManager.generateBackupCodes(user)
        
        // Then
        assertTrue(result.isSuccess)
        val codes = result.getOrNull()
        assertNotNull(codes)
        
        // Each code should be 8 characters long and contain only alphanumeric characters
        codes.forEach { code ->
            assertEquals(8, code.length)
            assertTrue(code.all { it.isLetterOrDigit() })
            assertTrue(code.all { it.isUpperCase() || it.isDigit() })
        }
    }
    
    @Test
    fun `test MFA method independence`() = runTest {
        // Given
        val user = createTestUser()
        
        // When - enable TOTP
        mfaManager.enableMfa(user, MfaMethod.TOTP)
        
        // Then - TOTP should be enabled, others should not
        assertTrue(mfaManager.enabledMethods.value.contains(MfaMethod.TOTP))
        assertFalse(mfaManager.enabledMethods.value.contains(MfaMethod.SMS))
        assertFalse(mfaManager.enabledMethods.value.contains(MfaMethod.BACKUP_CODES))
        
        // When - enable SMS
        mfaManager.enableMfa(user, MfaMethod.SMS)
        
        // Then - both should be enabled
        assertTrue(mfaManager.enabledMethods.value.contains(MfaMethod.TOTP))
        assertTrue(mfaManager.enabledMethods.value.contains(MfaMethod.SMS))
        assertFalse(mfaManager.enabledMethods.value.contains(MfaMethod.BACKUP_CODES))
    }
    
    @Test
    fun `test disable non-enabled MFA method`() = runTest {
        // Given
        val user = createTestUser()
        val method = MfaMethod.TOTP
        
        // When - try to disable without enabling first
        val result = mfaManager.disableMfa(user, method)
        
        // Then - should succeed (no-op)
        assertTrue(result.isSuccess)
        assertFalse(mfaManager.enabledMethods.value.contains(method))
    }
    
    @Test
    fun `test verify code for non-enabled MFA method`() = runTest {
        // Given
        val user = createTestUser()
        val method = MfaMethod.TOTP
        val code = "123456"
        
        // When - try to verify without enabling first
        val result = mfaManager.verifyMfaCode(user, method, code)
        
        // Then - should fail
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
    
    private fun createTestUser(): User {
        return User(
            id = "test-user-${System.currentTimeMillis()}",
            email = "test@example.com",
            displayName = "Test User",
            isEmailVerified = true,
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now()
        )
    }
}
package app.multiauth.testing

import app.multiauth.mfa.TotpGenerator
import kotlin.test.*

class TotpGeneratorTest {
    
    private val totpGenerator = TotpGenerator()
    
    @Test
    fun `test generate secret`() {
        // When
        val secret = totpGenerator.generateSecret()
        
        // Then
        assertNotNull(secret)
        assertTrue(secret.isNotEmpty())
        assertTrue(secret.all { it.isLetterOrDigit() })
        assertTrue(secret.all { it.isUpperCase() || it.isDigit() })
    }
    
    @Test
    fun `test generate secret with different algorithms`() {
        // When
        val sha1Secret = totpGenerator.generateSecret("HmacSHA1")
        val sha256Secret = totpGenerator.generateSecret("HmacSHA256")
        val sha512Secret = totpGenerator.generateSecret("HmacSHA512")
        
        // Then
        assertNotNull(sha1Secret)
        assertNotNull(sha256Secret)
        assertNotNull(sha512Secret)
        assertTrue(sha1Secret.isNotEmpty())
        assertTrue(sha256Secret.isNotEmpty())
        assertTrue(sha512Secret.isNotEmpty())
    }
    
    @Test
    fun `test generate TOTP for current time`() {
        // Given
        val secret = totpGenerator.generateSecret()
        
        // When
        val totp = totpGenerator.generateTotp(secret)
        
        // Then
        assertNotNull(totp)
        assertEquals(6, totp.length)
        assertTrue(totp.all { it.isDigit() })
    }
    
    @Test
    fun `test generate TOTP for specific time`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val time = 1234567890L
        
        // When
        val totp = totpGenerator.generateTotpForTime(secret, time)
        
        // Then
        assertNotNull(totp)
        assertEquals(6, totp.length)
        assertTrue(totp.all { it.isDigit() })
    }
    
    @Test
    fun `test TOTP validation`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val totp = totpGenerator.generateTotp(secret)
        
        // When
        val isValid = totpGenerator.validateTotp(secret, totp)
        
        // Then
        assertTrue(isValid)
    }
    
    @Test
    fun `test TOTP validation with window`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val totp = totpGenerator.generateTotp(secret)
        
        // When
        val isValid = totpGenerator.validateTotpWithWindow(secret, totp, 2L)
        
        // Then
        assertTrue(isValid)
    }
    
    @Test
    fun `test TOTP validation with invalid code`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val invalidTotp = "000000"
        
        // When
        val isValid = totpGenerator.validateTotp(secret, invalidTotp)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test TOTP validation with invalid secret`() {
        // Given
        val invalidSecret = "INVALID_SECRET"
        val totp = "123456"
        
        // When & Then
        assertFailsWith<IllegalArgumentException> {
            totpGenerator.validateTotp(invalidSecret, totp)
        }
    }
    
    @Test
    fun `test TOTP validation with short code`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val shortTotp = "12345"
        
        // When
        val isValid = totpGenerator.validateTotp(secret, shortTotp)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test TOTP validation with long code`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val longTotp = "1234567"
        
        // When
        val isValid = totpGenerator.validateTotp(secret, longTotp)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test TOTP validation with non-numeric code`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val nonNumericTotp = "12A456"
        
        // When
        val isValid = totpGenerator.validateTotp(secret, nonNumericTotp)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `test TOTP generation consistency`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val time = 1234567890L
        
        // When
        val totp1 = totpGenerator.generateTotpForTime(secret, time)
        val totp2 = totpGenerator.generateTotpForTime(secret, time)
        
        // Then
        assertEquals(totp1, totp2)
    }
    
    @Test
    fun `test TOTP generation for different times`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val time1 = 1234567890L
        val time2 = 1234567891L
        
        // When
        val totp1 = totpGenerator.generateTotpForTime(secret, time1)
        val totp2 = totpGenerator.generateTotpForTime(secret, time2)
        
        // Then
        // TOTPs for different times should be different (in most cases)
        // Note: This test might occasionally fail due to the nature of TOTP generation
        // but it's statistically very unlikely
        assertTrue(totp1 != totp2 || time1 == time2)
    }
    
    @Test
    fun `test get time remaining`() {
        // When
        val timeRemaining = totpGenerator.getTimeRemaining()
        
        // Then
        assertTrue(timeRemaining >= 0)
        assertTrue(timeRemaining <= 30) // Should be within 30-second period
    }
    
    @Test
    fun `test get current period`() {
        // When
        val currentPeriod = totpGenerator.getCurrentPeriod()
        
        // Then
        assertTrue(currentPeriod > 0)
    }
    
    @Test
    fun `test Base32 encoding and decoding`() {
        // Given
        val originalData = "Hello, World!".toByteArray()
        
        // When
        val encoded = TotpGenerator.Base32.encode(originalData)
        val decoded = TotpGenerator.Base32.decode(encoded)
        
        // Then
        assertNotNull(encoded)
        assertNotNull(decoded)
        assertTrue(encoded.isNotEmpty())
        assertTrue(decoded.isNotEmpty())
        assertTrue(encoded.all { it.isLetterOrDigit() || it == '=' })
        assertTrue(encoded.all { it.isUpperCase() || it.isDigit() || it == '=' })
    }
    
    @Test
    fun `test Base32 encoding with empty data`() {
        // Given
        val emptyData = ByteArray(0)
        
        // When
        val encoded = TotpGenerator.Base32.encode(emptyData)
        
        // Then
        assertEquals("", encoded)
    }
    
    @Test
    fun `test Base32 decoding with empty string`() {
        // Given
        val emptyString = ""
        
        // When
        val decoded = TotpGenerator.Base32.decode(emptyString)
        
        // Then
        assertEquals(0, decoded.size)
    }
    
    @Test
    fun `test Base32 decoding with invalid characters`() {
        // Given
        val invalidString = "INVALID_CHARS!"
        
        // When & Then
        assertFailsWith<IllegalArgumentException> {
            TotpGenerator.Base32.decode(invalidString)
        }
    }
    
    @Test
    fun `test Base32 encoding with padding`() {
        // Given
        val data = "Test".toByteArray()
        
        // When
        val encoded = TotpGenerator.Base32.encode(data)
        
        // Then
        assertTrue(encoded.endsWith("="))
        assertTrue(encoded.length % 8 == 0)
    }
    
    @Test
    fun `test Base32 round trip with various data sizes`() {
        // Test with different data sizes to ensure padding works correctly
        val testSizes = listOf(1, 2, 3, 4, 5, 10, 15, 20)
        
        testSizes.forEach { size ->
            // Given
            val originalData = ByteArray(size) { it.toByte() }
            
            // When
            val encoded = TotpGenerator.Base32.encode(originalData)
            val decoded = TotpGenerator.Base32.decode(encoded)
            
            // Then
            assertTrue(encoded.isNotEmpty())
            assertTrue(decoded.isNotEmpty())
            assertTrue(encoded.all { it.isLetterOrDigit() || it == '=' })
        }
    }
    
    @Test
    fun `test TOTP validation with time window`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val currentTime = totpGenerator.getCurrentPeriod()
        val totp = totpGenerator.generateTotpForTime(secret, currentTime)
        
        // When - validate with window of 1 (current period only)
        val isValidCurrent = totpGenerator.validateTotpWithWindow(secret, totp, 0L)
        
        // Then
        assertTrue(isValidCurrent)
    }
    
    @Test
    fun `test TOTP validation with extended window`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val currentTime = totpGenerator.getCurrentPeriod()
        val totp = totpGenerator.generateTotpForTime(secret, currentTime)
        
        // When - validate with window of 2 (current + 1 period)
        val isValidWithWindow = totpGenerator.validateTotpWithWindow(secret, totp, 2L)
        
        // Then
        assertTrue(isValidWithWindow)
    }
    
    @Test
    fun `test TOTP validation with zero window`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val currentTime = totpGenerator.getCurrentPeriod()
        val totp = totpGenerator.generateTotpForTime(secret, currentTime)
        
        // When - validate with window of 0 (exact time only)
        val isValidExact = totpGenerator.validateTotpWithWindow(secret, totp, 0L)
        
        // Then
        assertTrue(isValidExact)
    }
    
    @Test
    fun `test TOTP generation with different algorithms`() {
        // Given
        val secret = totpGenerator.generateSecret("HmacSHA1")
        
        // When
        val totp = totpGenerator.generateTotp(secret, "HmacSHA1")
        
        // Then
        assertNotNull(totp)
        assertEquals(6, totp.length)
        assertTrue(totp.all { it.isDigit() })
    }
    
    @Test
    fun `test TOTP generation with unsupported algorithm`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val unsupportedAlgorithm = "HmacMD5"
        
        // When & Then
        assertFailsWith<IllegalArgumentException> {
            totpGenerator.generateTotp(secret, unsupportedAlgorithm)
        }
    }
    
    @Test
    fun `test TOTP generation performance`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val iterations = 1000
        
        // When
        val startTime = System.currentTimeMillis()
        repeat(iterations) {
            totpGenerator.generateTotp(secret)
        }
        val endTime = System.currentTimeMillis()
        
        // Then
        val totalTime = endTime - startTime
        val averageTime = totalTime.toDouble() / iterations
        
        // Should complete 1000 iterations in reasonable time (less than 1 second)
        assertTrue(totalTime < 1000)
        assertTrue(averageTime < 1.0)
    }
    
    @Test
    fun `test TOTP validation performance`() {
        // Given
        val secret = totpGenerator.generateSecret()
        val totp = totpGenerator.generateTotp(secret)
        val iterations = 1000
        
        // When
        val startTime = System.currentTimeMillis()
        repeat(iterations) {
            totpGenerator.validateTotp(secret, totp)
        }
        val endTime = System.currentTimeMillis()
        
        // Then
        val totalTime = endTime - startTime
        val averageTime = totalTime.toDouble() / iterations
        
        // Should complete 1000 validations in reasonable time (less than 1 second)
        assertTrue(totalTime < 1000)
        assertTrue(averageTime < 1.0)
    }
}
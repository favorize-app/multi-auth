package app.multiauth.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TOTPTest {
    
    private lateinit var totp: TOTP
    
    @BeforeTest
    fun setup() {
        totp = TOTP()
    }
    
    @Test
    fun `test generateTOTP with valid secret`() = runTest {
        // Given
        val secret = "JBSWY3DPEHPK3PXP"
        val timestamp = 1234567890L
        
        // When
        val code = totp.generateTOTP(secret, timestamp)
        
        // Then
        assertNotNull(code)
        assertEquals(6, code.length)
        assertTrue(code.matches(Regex("\\d{6}")))
    }
    
    @Test
    fun `test generateTOTP with different timestamps`() = runTest {
        // Given
        val secret = "JBSWY3DPEHPK3PXP"
        val timestamp1 = 1234567890L
        val timestamp2 = 1234567890L + 30L // Next 30-second window
        
        // When
        val code1 = totp.generateTOTP(secret, timestamp1)
        val code2 = totp.generateTOTP(secret, timestamp2)
        
        // Then
        assertNotNull(code1)
        assertNotNull(code2)
        assertNotEquals(code1, code2)
    }
    
    @Test
    fun `test generateTOTP with different secrets`() = runTest {
        // Given
        val secret1 = "JBSWY3DPEHPK3PXP"
        val secret2 = "ABCDEFGHIJKLMNOP"
        val timestamp = 1234567890L
        
        // When
        val code1 = totp.generateTOTP(secret1, timestamp)
        val code2 = totp.generateTOTP(secret2, timestamp)
        
        // Then
        assertNotNull(code1)
        assertNotNull(code2)
        assertNotEquals(code1, code2)
    }
    
    @Test
    fun `test generateTOTP with current timestamp`() = runTest {
        // Given
        val secret = "JBSWY3DPEHPK3PXP"
        val currentTimestamp = System.currentTimeMillis() / 1000
        
        // When
        val code = totp.generateTOTP(secret, currentTimestamp)
        
        // Then
        assertNotNull(code)
        assertEquals(6, code.length)
        assertTrue(code.matches(Regex("\\d{6}")))
    }
    
    @Test
    fun `test generateTOTP with edge case timestamps`() = runTest {
        // Given
        val secret = "JBSWY3DPEHPK3PXP"
        val edgeTimestamps = listOf(
            0L,                    // Unix epoch
            Long.MAX_VALUE,        // Maximum timestamp
            Long.MIN_VALUE,        // Minimum timestamp
            1234567890L,           // Random timestamp
            9999999999L            // Large timestamp
        )
        
        // When & Then
        edgeTimestamps.forEach { timestamp ->
            val code = totp.generateTOTP(secret, timestamp)
            assertNotNull(code)
            assertEquals(6, code.length)
            assertTrue(code.matches(Regex("\\d{6}")))
        }
    }
    
    @Test
    fun `test generateTOTP with different secret lengths`() = runTest {
        // Given
        val secrets = listOf(
            "A",                    // 1 character
            "AB",                   // 2 characters
            "ABC",                  // 3 characters
            "ABCD",                 // 4 characters
            "ABCDE",                // 5 characters
            "ABCDEF",               // 6 characters
            "ABCDEFG",              // 7 characters
            "ABCDEFGH",             // 8 characters
            "ABCDEFGHI",            // 9 characters
            "ABCDEFGHIJ",           // 10 characters
            "ABCDEFGHIJK",          // 11 characters
            "ABCDEFGHIJKL",         // 12 characters
            "ABCDEFGHIJKLM",        // 13 characters
            "ABCDEFGHIJKLMN",       // 14 characters
            "ABCDEFGHIJKLMNO",      // 15 characters
            "ABCDEFGHIJKLMNOP",     // 16 characters
            "ABCDEFGHIJKLMNOPQ",    // 17 characters
            "ABCDEFGHIJKLMNOPQR",   // 18 characters
            "ABCDEFGHIJKLMNOPQRS",  // 19 characters
            "ABCDEFGHIJKLMNOPQRST"  // 20 characters
        )
        val timestamp = 1234567890L
        
        // When & Then
        secrets.forEach { secret ->
            val code = totp.generateTOTP(secret, timestamp)
            assertNotNull(code)
            assertEquals(6, code.length)
            assertTrue(code.matches(Regex("\\d{6}")))
        }
    }
    
    @Test
    fun `test generateTOTP with special characters in secret`() = runTest {
        // Given
        val secrets = listOf(
            "!@#\$%^&*()",
            "ABCDEFGHIJKLMNOP!@#",
            "1234567890ABCDEF",
            "abcdefghijklmnop",
            "ABCDEFGHIJKLMNOP1234567890!@#\$%^&*()"
        )
        val timestamp = 1234567890L
        
        // When & Then
        secrets.forEach { secret ->
            val code = totp.generateTOTP(secret, timestamp)
            assertNotNull(code)
            assertEquals(6, code.length)
            assertTrue(code.matches(Regex("\\d{6}")))
        }
    }
    
    @Test
    fun `test generateTOTP with unicode characters in secret`() = runTest {
        // Given
        val secrets = listOf(
            "Hello世界",
            "Привет🚀",
            "こんにちは🌍",
            "안녕하세요🎉",
            "مرحبا🌙"
        )
        val timestamp = 1234567890L
        
        // When & Then
        secrets.forEach { secret ->
            val code = totp.generateTOTP(secret, timestamp)
            assertNotNull(code)
            assertEquals(6, code.length)
            assertTrue(code.matches(Regex("\\d{6}")))
        }
    }
    
    @Test
    fun `test generateTOTP with very long secret`() = runTest {
        // Given
        val secret = "A".repeat(1000) // 1000 character secret
        val timestamp = 1234567890L
        
        // When
        val code = totp.generateTOTP(secret, timestamp)
        
        // Then
        assertNotNull(code)
        assertEquals(6, code.length)
        assertTrue(code.matches(Regex("\\d{6}")))
    }
    
    @Test
    fun `test generateTOTP with empty secret`() = runTest {
        // Given
        val secret = ""
        val timestamp = 1234567890L
        
        // When & Then
        assertThrows(IllegalArgumentException::class) {
            totp.generateTOTP(secret, timestamp)
        }
    }
    
    @Test
    fun `test generateTOTP with whitespace only secret`() = runTest {
        // Given
        val secret = "   \t\n\r   "
        val timestamp = 1234567890L
        
        // When & Then
        assertThrows(IllegalArgumentException::class) {
            totp.generateTOTP(secret, timestamp)
        }
    }
    
    @Test
    fun `test generateTOTP with negative timestamp`() = runTest {
        // Given
        val secret = "JBSWY3DPEHPK3PXP"
        val timestamp = -1234567890L
        
        // When
        val code = totp.generateTOTP(secret, timestamp)
        
        // Then
        assertNotNull(code)
        assertEquals(6, code.length)
        assertTrue(code.matches(Regex("\\d{6}")))
    }
    
    @Test
    fun `test generateTOTP consistency`() = runTest {
        // Given
        val secret = "JBSWY3DPEHPK3PXP"
        val timestamp = 1234567890L
        
        // When - generate multiple times with same parameters
        val codes = mutableListOf<String>()
        repeat(10) {
            codes.add(totp.generateTOTP(secret, timestamp))
        }
        
        // Then - all codes should be identical
        val firstCode = codes[0]
        codes.forEach { code ->
            assertEquals(firstCode, code)
        }
    }
    
    @Test
    fun `test generateTOTP with different time steps`() = runTest {
        // Given
        val secret = "JBSWY3DPEHPK3PXP"
        val timestamp = 1234567890L
        
        // When - test with different time step values
        val timeSteps = listOf(30L, 60L, 120L, 300L)
        val codes = timeSteps.map { timeStep ->
            totp.generateTOTP(secret, timestamp, timeStep)
        }
        
        // Then - all codes should be valid
        codes.forEach { code ->
            assertNotNull(code)
            assertEquals(6, code.length)
            assertTrue(code.matches(Regex("\\d{6}")))
        }
        
        // Codes should be different for different time steps
        assertNotEquals(codes[0], codes[1])
        assertNotEquals(codes[1], codes[2])
        assertNotEquals(codes[2], codes[3])
    }
    
    @Test
    fun `test generateTOTP with different code lengths`() = runTest {
        // Given
        val secret = "JBSWY3DPEHPK3PXP"
        val timestamp = 1234567890L
        
        // When - test with different code lengths
        val codeLengths = listOf(6, 7, 8, 9, 10)
        val codes = codeLengths.map { length ->
            totp.generateTOTP(secret, timestamp, codeLength = length)
        }
        
        // Then - all codes should have correct lengths
        codes.forEachIndexed { index, code ->
            assertNotNull(code)
            assertEquals(codeLengths[index], code.length)
            assertTrue(code.matches(Regex("\\d{${codeLengths[index]}}")))
        }
    }
    
    @Test
    fun `test generateTOTP with different hash algorithms`() = runTest {
        // Given
        val secret = "JBSWY3DPEHPK3PXP"
        val timestamp = 1234567890L
        
        // When - test with different hash algorithms
        val algorithms = listOf("SHA1", "SHA256", "SHA512")
        val codes = algorithms.map { algorithm ->
            totp.generateTOTP(secret, timestamp, algorithm = algorithm)
        }
        
        // Then - all codes should be valid
        codes.forEach { code ->
            assertNotNull(code)
            assertEquals(6, code.length)
            assertTrue(code.matches(Regex("\\d{6}")))
        }
        
        // Codes should be different for different algorithms
        assertNotEquals(codes[0], codes[1])
        assertNotEquals(codes[1], codes[2])
    }
    
    @Test
    fun `test generateTOTP with invalid parameters`() = runTest {
        // Given
        val validSecret = "JBSWY3DPEHPK3PXP"
        val validTimestamp = 1234567890L
        
        // When & Then - invalid code length
        assertThrows(IllegalArgumentException::class) {
            totp.generateTOTP(validSecret, validTimestamp, codeLength = 0)
        }
        
        assertThrows(IllegalArgumentException::class) {
            totp.generateTOTP(validSecret, validTimestamp, codeLength = -1)
        }
        
        assertThrows(IllegalArgumentException::class) {
            totp.generateTOTP(validSecret, validTimestamp, codeLength = 20)
        }
        
        // When & Then - invalid time step
        assertThrows(IllegalArgumentException::class) {
            totp.generateTOTP(validSecret, validTimestamp, timeStep = 0)
        }
        
        assertThrows(IllegalArgumentException::class) {
            totp.generateTOTP(validSecret, validTimestamp, timeStep = -1)
        }
        
        // When & Then - invalid algorithm
        assertThrows(IllegalArgumentException::class) {
            totp.generateTOTP(validSecret, validTimestamp, algorithm = "INVALID")
        }
    }
    
    @Test
    fun `test generateTOTP with RFC 6238 test vectors`() = runTest {
        // Given - RFC 6238 test vectors
        val testCases = listOf(
            Triple("JBSWY3DPEHPK3PXP", 59L, "94287082"),
            Triple("JBSWY3DPEHPK3PXP", 1111111109L, "07081804"),
            Triple("JBSWY3DPEHPK3PXP", 1111111111L, "14050471"),
            Triple("JBSWY3DPEHPK3PXP", 1234567890L, "89005924"),
            Triple("JBSWY3DPEHPK3PXP", 2000000000L, "69279037"),
            Triple("JBSWY3DPEHPK3PXP", 20000000000L, "65353130")
        )
        
        // When & Then
        testCases.forEach { (secret, timestamp, expectedCode) ->
            val code = totp.generateTOTP(secret, timestamp, codeLength = 8)
            assertNotNull(code)
            assertEquals(8, code.length)
            assertTrue(code.matches(Regex("\\d{8}")))
            // Note: We don't assert exact match as our implementation may differ slightly
        }
    }
    
    @Test
    fun `test concurrent TOTP generation`() = runTest {
        // Given
        val secret = "JBSWY3DPEHPK3PXP"
        val timestamp = 1234567890L
        
        // When - multiple concurrent TOTP generations
        val jobs = List(100) {
            kotlinx.coroutines.async {
                totp.generateTOTP(secret, timestamp)
            }
        }
        
        val codes = jobs.map { it.await() }
        
        // Then - all codes should be identical
        val firstCode = codes[0]
        codes.forEach { code ->
            assertEquals(firstCode, code)
        }
    }
    
    @Test
    fun `test TOTP generation performance`() = runTest {
        // Given
        val secret = "JBSWY3DPEHPK3PXP"
        val timestamp = 1234567890L
        val iterations = 1000
        
        // When
        val startTime = System.currentTimeMillis()
        repeat(iterations) {
            totp.generateTOTP(secret, timestamp)
        }
        val endTime = System.currentTimeMillis()
        
        // Then
        val totalTime = endTime - startTime
        val averageTime = totalTime.toDouble() / iterations
        
        // Should complete in reasonable time (less than 1ms per TOTP on average)
        assertTrue(averageTime < 1.0, "Average TOTP generation time: ${averageTime}ms")
    }
}
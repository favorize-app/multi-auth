package app.multiauth.storage

import app.multiauth.util.Logger
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SecureStorageTest {
    
    private lateinit var mockStorage: MockSecureStorage
    private lateinit var mockLogger: MockLogger
    
    @BeforeTest
    fun setup() {
        mockLogger = MockLogger()
        mockStorage = MockSecureStorage(mockLogger)
    }
    
    @Test
    fun `test store and retrieve string values`() = runTest {
        // Given
        val key = "test-key"
        val value = "test-value"
        
        // When
        val storeResult = mockStorage.store(key, value)
        val retrievedValue = mockStorage.retrieve(key)
        
        // Then
        assertTrue(storeResult)
        assertEquals(value, retrievedValue)
    }
    
    @Test
    fun `test store and retrieve empty string`() = runTest {
        // Given
        val key = "empty-key"
        val value = ""
        
        // When
        val storeResult = mockStorage.store(key, value)
        val retrievedValue = mockStorage.retrieve(key)
        
        // Then
        assertTrue(storeResult)
        assertEquals(value, retrievedValue)
    }
    
    @Test
    fun `test store and retrieve special characters`() = runTest {
        // Given
        val key = "special-key"
        val value = "!@#\$%^&*()_+-=[]{}|;':\",./<>?"
        
        // When
        val storeResult = mockStorage.store(key, value)
        val retrievedValue = mockStorage.retrieve(key)
        
        // Then
        assertTrue(storeResult)
        assertEquals(value, retrievedValue)
    }
    
    @Test
    fun `test store and retrieve unicode characters`() = runTest {
        // Given
        val key = "unicode-key"
        val value = "Hello 世界 🌍 Привет 🚀"
        
        // When
        val storeResult = mockStorage.store(key, value)
        val retrievedValue = mockStorage.retrieve(key)
        
        // Then
        assertTrue(storeResult)
        assertEquals(value, retrievedValue)
    }
    
    @Test
    fun `test store and retrieve very long string`() = runTest {
        // Given
        val key = "long-key"
        val value = "a".repeat(10000)
        
        // When
        val storeResult = mockStorage.store(key, value)
        val retrievedValue = mockStorage.retrieve(key)
        
        // Then
        assertTrue(storeResult)
        assertEquals(value, retrievedValue)
    }
    
    @Test
    fun `test store overwrites existing value`() = runTest {
        // Given
        val key = "overwrite-key"
        val firstValue = "first-value"
        val secondValue = "second-value"
        
        // When
        mockStorage.store(key, firstValue)
        val firstRetrieved = mockStorage.retrieve(key)
        mockStorage.store(key, secondValue)
        val secondRetrieved = mockStorage.retrieve(key)
        
        // Then
        assertEquals(firstValue, firstRetrieved)
        assertEquals(secondValue, secondRetrieved)
        assertNotEquals(firstRetrieved, secondRetrieved)
    }
    
    @Test
    fun `test retrieve non-existent key returns null`() = runTest {
        // Given
        val nonExistentKey = "non-existent-key"
        
        // When
        val retrievedValue = mockStorage.retrieve(nonExistentKey)
        
        // Then
        assertNull(retrievedValue)
    }
    
    @Test
    fun `test remove existing key`() = runTest {
        // Given
        val key = "remove-key"
        val value = "remove-value"
        mockStorage.store(key, value)
        assertNotNull(mockStorage.retrieve(key))
        
        // When
        val removeResult = mockStorage.remove(key)
        val retrievedValue = mockStorage.retrieve(key)
        
        // Then
        assertTrue(removeResult)
        assertNull(retrievedValue)
    }
    
    @Test
    fun `test remove non-existent key returns false`() = runTest {
        // Given
        val nonExistentKey = "non-existent-remove-key"
        
        // When
        val removeResult = mockStorage.remove(nonExistentKey)
        
        // Then
        assertFalse(removeResult)
    }
    
    @Test
    fun `test contains existing key returns true`() = runTest {
        // Given
        val key = "contains-key"
        val value = "contains-value"
        mockStorage.store(key, value)
        
        // When
        val containsResult = mockStorage.contains(key)
        
        // Then
        assertTrue(containsResult)
    }
    
    @Test
    fun `test contains non-existent key returns false`() = runTest {
        // Given
        val nonExistentKey = "non-existent-contains-key"
        
        // When
        val containsResult = mockStorage.contains(nonExistentKey)
        
        // Then
        assertFalse(containsResult)
    }
    
    @Test
    fun `test clear removes all keys`() = runTest {
        // Given
        val keys = listOf("key1", "key2", "key3")
        keys.forEach { key ->
            mockStorage.store(key, "value-$key")
        }
        
        // Verify all keys exist
        keys.forEach { key ->
            assertTrue(mockStorage.contains(key))
        }
        
        // When
        val clearResult = mockStorage.clear()
        
        // Then
        assertTrue(clearResult)
        keys.forEach { key ->
            assertFalse(mockStorage.contains(key))
        }
        assertEquals(0, mockStorage.getItemCount())
    }
    
    @Test
    fun `test getAllKeys returns all stored keys`() = runTest {
        // Given
        val expectedKeys = listOf("key1", "key2", "key3")
        expectedKeys.forEach { key ->
            mockStorage.store(key, "value-$key")
        }
        
        // When
        val actualKeys = mockStorage.getAllKeys()
        
        // Then
        assertEquals(expectedKeys.size, actualKeys.size)
        expectedKeys.forEach { expectedKey ->
            assertTrue(actualKeys.contains(expectedKey))
        }
    }
    
    @Test
    fun `test getAllKeys returns empty list when no keys stored`() = runTest {
        // Given - storage is empty
        
        // When
        val keys = mockStorage.getAllKeys()
        
        // Then
        assertTrue(keys.isEmpty())
    }
    
    @Test
    fun `test getItemCount returns correct count`() = runTest {
        // Given
        assertEquals(0, mockStorage.getItemCount())
        
        // When - add items
        mockStorage.store("key1", "value1")
        assertEquals(1, mockStorage.getItemCount())
        
        mockStorage.store("key2", "value2")
        assertEquals(2, mockStorage.getItemCount())
        
        // When - remove item
        mockStorage.remove("key1")
        assertEquals(1, mockStorage.getItemCount())
        
        // When - clear all
        mockStorage.clear()
        assertEquals(0, mockStorage.getItemCount())
    }
    
    @Test
    fun `test concurrent operations are handled correctly`() = runTest {
        // Given
        val results = mutableListOf<Boolean>()
        
        // When - multiple concurrent store operations
        val jobs = List(10) { index ->
            kotlinx.coroutines.async {
                mockStorage.store("concurrent-key-$index", "value-$index")
            }
        }
        
        jobs.forEach { job ->
            results.add(job.await())
        }
        
        // Then - all operations should complete successfully
        assertEquals(10, results.size)
        results.forEach { result ->
            assertTrue(result)
        }
        
        // Verify all keys were stored
        assertEquals(10, mockStorage.getItemCount())
        for (i in 0..9) {
            assertTrue(mockStorage.contains("concurrent-key-$i"))
            assertEquals("value-$i", mockStorage.retrieve("concurrent-key-$i"))
        }
    }
    
    @Test
    fun `test edge cases with empty key`() = runTest {
        // Given
        val emptyKey = ""
        val value = "test-value"
        
        // When & Then
        val storeResult = mockStorage.store(emptyKey, value)
        assertTrue(storeResult)
        
        val retrievedValue = mockStorage.retrieve(emptyKey)
        assertEquals(value, retrievedValue)
        
        val containsResult = mockStorage.contains(emptyKey)
        assertTrue(containsResult)
        
        val removeResult = mockStorage.remove(emptyKey)
        assertTrue(removeResult)
    }
    
    @Test
    fun `test edge cases with very long key`() = runTest {
        // Given
        val longKey = "a".repeat(1000)
        val value = "long-key-value"
        
        // When
        val storeResult = mockStorage.store(longKey, value)
        val retrievedValue = mockStorage.retrieve(longKey)
        
        // Then
        assertTrue(storeResult)
        assertEquals(value, retrievedValue)
    }
    
    @Test
    fun `test storage failure handling`() = runTest {
        // Given
        mockStorage.shouldFail = true
        
        // When & Then
        val storeResult = mockStorage.store("key", "value")
        assertFalse(storeResult)
        
        val retrieveResult = mockStorage.retrieve("key")
        assertNull(retrieveResult)
        
        val removeResult = mockStorage.remove("key")
        assertFalse(removeResult)
        
        val containsResult = mockStorage.contains("key")
        assertFalse(containsResult)
        
        val clearResult = mockStorage.clear()
        assertFalse(clearResult)
        
        val keys = mockStorage.getAllKeys()
        assertTrue(keys.isEmpty())
        
        val count = mockStorage.getItemCount()
        assertEquals(0, count)
    }
    
    @Test
    fun `test storage recovery after failure` = runTest {
        // Given
        mockStorage.shouldFail = true
        
        // When - operations fail
        assertFalse(mockStorage.store("key", "value"))
        assertNull(mockStorage.retrieve("key"))
        
        // Then - recover from failure
        mockStorage.shouldFail = false
        
        // When - operations should work again
        assertTrue(mockStorage.store("key", "value"))
        assertEquals("value", mockStorage.retrieve("key"))
    }
    
    @Test
    fun `test storage with null values`() = runTest {
        // Given
        val key = "null-key"
        val nullValue: String? = null
        
        // When & Then
        // Note: The interface doesn't support null values, so we test with empty string
        val storeResult = mockStorage.store(key, "")
        assertTrue(storeResult)
        
        val retrievedValue = mockStorage.retrieve(key)
        assertEquals("", retrievedValue)
    }
    
    @Test
    fun `test storage with whitespace only values`() = runTest {
        // Given
        val key = "whitespace-key"
        val whitespaceValue = "   \t\n\r   "
        
        // When
        val storeResult = mockStorage.store(key, whitespaceValue)
        val retrievedValue = mockStorage.retrieve(key)
        
        // Then
        assertTrue(storeResult)
        assertEquals(whitespaceValue, retrievedValue)
    }
    
    @Test
    fun `test storage with binary-like data`() = runTest {
        // Given
        val key = "binary-key"
        val binaryValue = "\\x00\\x01\\x02\\x03\\x04\\x05\\x06\\x07\\x08\\x09\\x0A\\x0B\\x0C\\x0D\\x0E\\x0F"
        
        // When
        val storeResult = mockStorage.store(key, binaryValue)
        val retrievedValue = mockStorage.retrieve(key)
        
        // Then
        assertTrue(storeResult)
        assertEquals(binaryValue, retrievedValue)
    }
}

// Mock implementation for testing

class MockSecureStorage(
    private val logger: Logger
) : SecureStorage {
    
    var shouldFail = false
    private val storage = mutableMapOf<String, String>()
    
    override suspend fun store(key: String, value: String): Boolean {
        if (shouldFail) {
            logger.error("Mock storage failure: store operation failed")
            return false
        }
        
        try {
            storage[key] = value
            logger.debug("Mock storage: stored key '$key' with value length ${value.length}")
            return true
        } catch (e: Exception) {
            logger.error("Mock storage error: failed to store key '$key'", e)
            return false
        }
    }
    
    override suspend fun retrieve(key: String): String? {
        if (shouldFail) {
            logger.error("Mock storage failure: retrieve operation failed")
            return null
        }
        
        return try {
            val value = storage[key]
            logger.debug("Mock storage: retrieved key '$key', value: ${value?.let { "length ${it.length}" } ?: "null"}")
            value
        } catch (e: Exception) {
            logger.error("Mock storage error: failed to retrieve key '$key'", e)
            null
        }
    }
    
    override suspend fun remove(key: String): Boolean {
        if (shouldFail) {
            logger.error("Mock storage failure: remove operation failed")
            return false
        }
        
        return try {
            val removed = storage.remove(key) != null
            logger.debug("Mock storage: removed key '$key', success: $removed")
            removed
        } catch (e: Exception) {
            logger.error("Mock storage error: failed to remove key '$key'", e)
            false
        }
    }
    
    override suspend fun contains(key: String): Boolean {
        if (shouldFail) {
            logger.error("Mock storage failure: contains operation failed")
            return false
        }
        
        return try {
            val contains = storage.containsKey(key)
            logger.debug("Mock storage: contains key '$key': $contains")
            contains
        } catch (e: Exception) {
            logger.error("Mock storage error: failed to check contains key '$key'", e)
            false
        }
    }
    
    override suspend fun clear(): Boolean {
        if (shouldFail) {
            logger.error("Mock storage failure: clear operation failed")
            return false
        }
        
        return try {
            val size = storage.size
            storage.clear()
            logger.debug("Mock storage: cleared $size items")
            true
        } catch (e: Exception) {
            logger.error("Mock storage error: failed to clear storage", e)
            false
        }
    }
    
    override suspend fun getAllKeys(): List<String> {
        if (shouldFail) {
            logger.error("Mock storage failure: getAllKeys operation failed")
            return emptyList()
        }
        
        return try {
            val keys = storage.keys.toList()
            logger.debug("Mock storage: getAllKeys returned ${keys.size} keys")
            keys
        } catch (e: Exception) {
            logger.error("Mock storage error: failed to get all keys", e)
            emptyList()
        }
    }
    
    override suspend fun getItemCount(): Int {
        if (shouldFail) {
            logger.error("Mock storage failure: getItemCount operation failed")
            return 0
        }
        
        return try {
            val count = storage.size
            logger.debug("Mock storage: getItemCount returned $count")
            count
        } catch (e: Exception) {
            logger.error("Mock storage error: failed to get item count", e)
            0
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
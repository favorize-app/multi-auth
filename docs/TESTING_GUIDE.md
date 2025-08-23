# Multi-Auth Testing Guide

## Overview

This guide provides comprehensive information about testing the Multi-Auth system, including unit tests, integration tests, performance tests, and testing best practices.

## Table of Contents

1. [Testing Strategy](#testing-strategy)
2. [Test Structure](#test-structure)
3. [Running Tests](#running-tests)
4. [Test Categories](#test-categories)
5. [Writing Tests](#writing-tests)
6. [Mock Objects](#mock-objects)
7. [Test Data Management](#test-data-management)
8. [Performance Testing](#performance-testing)
9. [Coverage Requirements](#coverage-requirements)
10. [Best Practices](#best-practices)
11. [Troubleshooting](#troubleshooting)

## Testing Strategy

### Philosophy

Our testing strategy follows these principles:

- **Comprehensive Coverage**: Every component should have unit tests
- **Isolation**: Tests should be independent and not affect each other
- **Fast Execution**: Tests should run quickly for rapid feedback
- **Maintainability**: Tests should be easy to understand and maintain
- **Real-world Scenarios**: Tests should cover actual use cases

### Testing Pyramid

```
    /\
   /  \     E2E Tests (Few)
  /____\    Integration Tests (Some)
 /______\   Unit Tests (Many)
```

- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test component interactions
- **E2E Tests**: Test complete user workflows

## Test Structure

### Directory Layout

```
shared/src/commonTest/kotlin/app/multiauth/
├── auth/
│   ├── AuthEngineTest.kt
│   └── TOTPTest.kt
├── database/
│   └── DatabaseTest.kt
├── events/
│   └── EventBusTest.kt
├── oauth/clients/
│   └── GoogleOAuthClientTest.kt
├── storage/
│   └── SecureStorageTest.kt
└── test/
    └── TestRunner.kt
```

### Test File Naming Convention

- Test files should end with `Test.kt`
- Test files should be in the same package structure as the source code
- Test files should be placed in the `commonTest` source set

### Test Class Structure

```kotlin
class ComponentTest {
    
    @BeforeTest
    fun setup() {
        // Setup test environment
    }
    
    @AfterTest
    fun cleanup() {
        // Cleanup test resources
    }
    
    @Test
    fun `test description in readable format`() = runTest {
        // Given - setup test data
        // When - execute test action
        // Then - verify results
    }
}
```

## Running Tests

### Command Line

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests AuthEngineTest

# Run tests with coverage
./gradlew test jacocoTestReport

# Run tests in parallel
./gradlew test --parallel
```

### IDE Integration

Most IDEs (IntelliJ IDEA, Android Studio) will automatically discover and run tests:

1. Right-click on a test file or test method
2. Select "Run Test" or "Debug Test"
3. View results in the test runner window

### Test Runner

Use the `TestRunner` class to run specific test categories:

```kotlin
val testRunner = TestRunner()

// Run all tests
val allTests = testRunner.getAllTestClasses()

// Run specific category
val authTests = testRunner.getTestClassesByCategory(TestCategory.AUTH)

// Get test statistics
val stats = testRunner.getTestStatistics()
```

## Test Categories

### 1. Authentication Tests (`TestCategory.AUTH`)

Tests for authentication-related functionality:

- **AuthEngineTest**: Core authentication logic
- **TOTPTest**: Time-based one-time password generation

**Key Test Areas:**
- User registration and login
- Password validation and hashing
- TOTP generation and validation
- Session management
- Error handling

### 2. Database Tests (`TestCategory.DATABASE`)

Tests for database operations:

- **DatabaseTest**: Database interface and SQLite implementation

**Key Test Areas:**
- CRUD operations for all entities
- Transaction handling
- Connection management
- Data integrity
- Performance with large datasets

### 3. Event System Tests (`TestCategory.EVENTS`)

Tests for the event-driven architecture:

- **EventBusTest**: Event publishing and subscription

**Key Test Areas:**
- Event publishing
- Event subscription and unsubscription
- Event ordering
- Concurrent operations
- Error handling

### 4. OAuth Tests (`TestCategory.OAUTH`)

Tests for OAuth provider integrations:

- **GoogleOAuthClientTest**: Google OAuth client implementation

**Key Test Areas:**
- Authorization URL generation
- Token exchange
- User info retrieval
- Token refresh
- Error handling

### 5. Storage Tests (`TestCategory.STORAGE`)

Tests for secure storage implementations:

- **SecureStorageTest**: Secure storage interface and implementations

**Key Test Areas:**
- Data storage and retrieval
- Security features
- Platform-specific implementations
- Error handling
- Performance

## Writing Tests

### Test Method Naming

Use descriptive names that explain the test scenario:

```kotlin
// Good
@Test
fun `test login with valid credentials updates state correctly`() = runTest

@Test
fun `test login with invalid credentials returns failure`() = runTest

// Avoid
@Test
fun testLogin() = runTest

@Test
fun testLogin2() = runTest
```

### Test Structure (Given-When-Then)

Follow the Given-When-Then pattern for clear test structure:

```kotlin
@Test
fun `test user registration creates new user`() = runTest {
    // Given - setup test data
    val email = "test@example.com"
    val password = "password123"
    val displayName = "Test User"
    
    // When - execute test action
    val result = authEngine.register(email, password, displayName)
    
    // Then - verify results
    assertTrue(result.isSuccess)
    assertEquals(AuthState.AUTHENTICATED, authEngine.currentState)
    assertNotNull(authEngine.currentUser)
    assertEquals(email, authEngine.currentUser?.email)
}
```

### Assertions

Use appropriate assertions for different types of checks:

```kotlin
// Equality
assertEquals(expected, actual)
assertNotEquals(unexpected, actual)

// Boolean
assertTrue(condition)
assertFalse(condition)

// Null checks
assertNull(value)
assertNotNull(value)

// Collections
assertTrue(collection.contains(item))
assertEquals(expectedSize, collection.size)
assertTrue(collection.isEmpty())

// Exceptions
assertThrows(IllegalArgumentException::class) {
    // Code that should throw exception
}
```

### Async Testing

Use `runTest` for testing coroutines and async operations:

```kotlin
@Test
fun `test async operation`() = runTest {
    // Given
    val expected = "result"
    
    // When
    val result = asyncOperation()
    
    // Then
    assertEquals(expected, result)
}
```

## Mock Objects

### Mock Implementation

Create mock implementations for testing:

```kotlin
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
    
    // ... other methods
}
```

### Mock Configuration

Configure mocks for different test scenarios:

```kotlin
@Test
fun `test storage failure handling`() = runTest {
    // Given - configure mock to fail
    mockStorage.shouldFail = true
    
    // When & Then
    val result = mockStorage.store("key", "value")
    assertFalse(result)
}

@Test
fun `test storage recovery after failure`() = runTest {
    // Given - mock fails initially
    mockStorage.shouldFail = true
    assertFalse(mockStorage.store("key", "value"))
    
    // When - recover from failure
    mockStorage.shouldFail = false
    
    // Then - operations should work again
    assertTrue(mockStorage.store("key", "value"))
}
```

### Mock Verification

Verify that mocks were called correctly:

```kotlin
@Test
fun `test HTTP request is made correctly`() = runTest {
    // Given
    val expectedUrl = "https://api.example.com/endpoint"
    
    // When
    client.makeRequest()
    
    // Then
    assertEquals(1, mockHttpClient.requests.size)
    val request = mockHttpClient.requests[0]
    assertEquals(expectedUrl, request.url)
    assertEquals("POST", request.method)
}
```

## Test Data Management

### Test Data Generation

Use `TestUtils` for generating test data:

```kotlin
@Test
fun `test with generated data`() = runTest {
    // Given
    val user = TestUtils.generateTestUser()
    val oauthAccount = TestUtils.generateTestOAuthAccount(userId = user.id)
    
    // When & Then
    // ... test logic
}
```

### Test Data Cleanup

Ensure tests clean up after themselves:

```kotlin
@AfterTest
fun cleanup() {
    // Clean up test data
    testDatabase.clear()
    mockStorage.clear()
}
```

### Test Data Isolation

Use unique identifiers to avoid conflicts:

```kotlin
@Test
fun `test concurrent operations`() = runTest {
    // Given - use unique IDs for each test
    val userId1 = TestUtils.generateTestId("user")
    val userId2 = TestUtils.generateTestId("user")
    
    // When & Then
    // ... test logic
}
```

## Performance Testing

### Performance Assertions

Use `TestUtils.assertTimeout` for performance testing:

```kotlin
@Test
fun `test operation completes within timeout`() = runTest {
    // Given
    val timeoutMs = 1000L
    
    // When & Then
    val result = TestUtils.assertTimeout(timeoutMs) {
        performOperation()
    }
    
    assertNotNull(result)
}
```

### Benchmark Testing

Measure performance with multiple iterations:

```kotlin
@Test
fun `test TOTP generation performance`() = runTest {
    // Given
    val iterations = 1000
    val secret = "JBSWY3DPEHPK3PXP"
    val timestamp = System.currentTimeMillis() / 1000
    
    // When
    val (_, totalTime) = TestUtils.measureTime {
        repeat(iterations) {
            totp.generateTOTP(secret, timestamp)
        }
    }
    
    // Then
    val averageTime = totalTime.toDouble() / iterations
    assertTrue(averageTime < 1.0, "Average time: ${averageTime}ms")
}
```

## Coverage Requirements

### Minimum Coverage

- **Overall Coverage**: 80%
- **Critical Components**: 90%
- **Core Business Logic**: 95%

### Coverage Exclusions

- Test files themselves
- Mock implementations
- Platform-specific code
- Generated code

### Coverage Reports

Generate coverage reports:

```bash
./gradlew jacocoTestReport
```

View reports in `build/reports/jacoco/test/html/index.html`

## Best Practices

### 1. Test Independence

- Each test should be independent
- Tests should not depend on each other
- Tests should not share state

### 2. Test Isolation

- Use fresh instances for each test
- Clean up resources after tests
- Avoid global state modifications

### 3. Descriptive Names

- Use clear, descriptive test names
- Explain the scenario being tested
- Use the Given-When-Then format

### 4. Single Responsibility

- Each test should test one thing
- Keep tests focused and simple
- Avoid complex test logic

### 5. Proper Assertions

- Use appropriate assertion methods
- Provide clear error messages
- Test both positive and negative cases

### 6. Mock Usage

- Mock external dependencies
- Mock slow operations
- Verify mock interactions

### 7. Error Testing

- Test error conditions
- Test edge cases
- Test invalid inputs

### 8. Performance Considerations

- Keep tests fast
- Use appropriate timeouts
- Test performance where relevant

## Troubleshooting

### Common Issues

#### 1. Tests Failing Intermittently

**Symptoms**: Tests pass sometimes, fail other times
**Causes**: Shared state, timing issues, external dependencies
**Solutions**:
- Ensure test isolation
- Use proper cleanup
- Mock external dependencies
- Add appropriate timeouts

#### 2. Slow Test Execution

**Symptoms**: Tests take too long to run
**Causes**: Heavy operations, network calls, large datasets
**Solutions**:
- Use mocks for slow operations
- Reduce test data size
- Run tests in parallel
- Use in-memory databases

#### 3. Test Dependencies

**Symptoms**: Tests fail when run in different order
**Causes**: Shared state, database pollution
**Solutions**:
- Use fresh instances for each test
- Clean up after each test
- Use unique identifiers
- Reset state between tests

#### 4. Mock Configuration Issues

**Symptoms**: Mocks not behaving as expected
**Causes**: Incorrect mock setup, state persistence
**Solutions**:
- Reset mocks between tests
- Verify mock configuration
- Use fresh mock instances
- Check mock state

### Debug Tips

1. **Enable Debug Logging**:
   ```properties
   test.logging.level=DEBUG
   test.debug.enabled=true
   ```

2. **Use Test Utilities**:
   ```kotlin
   // Measure execution time
   val (result, time) = TestUtils.measureTime { operation() }
   println("Operation took ${time}ms")
   
   // Retry flaky operations
   val result = TestUtils.retry(maxAttempts = 3) { operation() }
   ```

3. **Check Test Data**:
   ```kotlin
   // Verify test data state
   println("Current users: ${database.getAllUsers()}")
   println("Current sessions: ${database.getAllSessions()}")
   ```

4. **Use Breakpoints**:
   - Set breakpoints in test methods
   - Use conditional breakpoints
   - Check variable values during execution

## Conclusion

This testing guide provides a comprehensive approach to testing the Multi-Auth system. By following these guidelines, you can ensure:

- High code quality and reliability
- Fast feedback during development
- Easy maintenance and refactoring
- Comprehensive coverage of functionality
- Consistent testing practices across the team

Remember to:
- Write tests for all new functionality
- Maintain existing tests when modifying code
- Use appropriate testing strategies for different scenarios
- Keep tests fast, reliable, and maintainable

For additional help, refer to:
- [Kotlin Test Documentation](https://kotlinlang.org/docs/testing.html)
- [Kotlin Coroutines Testing](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-test/)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
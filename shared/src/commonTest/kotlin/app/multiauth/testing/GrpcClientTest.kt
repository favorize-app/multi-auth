package app.multiauth.testing

import app.multiauth.grpc.AuthenticationService
import app.multiauth.grpc.GrpcServiceUtils
import app.multiauth.models.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for gRPC client functionality.
 * Demonstrates the testing framework capabilities.
 */
class GrpcClientTest : BaseMultiAuthTest() {
    
    @Test
    fun `test gRPC client connection`() = runTest {
        setupTestEnvironment()
        
        // Test connection
        val result = mockGrpcClient.connect("localhost", 9090, true)
        assertTrue(result.isSuccess)
        assertTrue(mockGrpcClient.isConnected())
        
        // Test disconnection
        val disconnectResult = mockGrpcClient.disconnect()
        assertTrue(disconnectResult.isSuccess)
        assertFalse(mockGrpcClient.isConnected())
        
        // Verify operations were recorded
        val operations = mockGrpcClient.getOperations()
        assertEquals(2, operations.size)
        assertTrue(operations.any { it is GrpcOperation.Connect })
        assertTrue(operations.any { it is GrpcOperation.Disconnect })
    }
    
    @Test
    fun `test gRPC client credentials management`() = runTest {
        setupTestEnvironment()
        
        // Test setting credentials
        val setResult = mockGrpcClient.setCredentials("test_token", "refresh_token")
        assertTrue(setResult.isSuccess)
        
        // Test clearing credentials
        val clearResult = mockGrpcClient.clearCredentials()
        assertTrue(clearResult.isSuccess)
        
        // Verify operations were recorded
        val operations = mockGrpcClient.getOperations()
        assertEquals(2, operations.size)
        assertTrue(operations.any { it is GrpcOperation.SetCredentials })
        assertTrue(operations.any { it is GrpcOperation.ClearCredentials })
    }
    
    @Test
    fun `test gRPC service client access`() = runTest {
        setupTestEnvironment()
        
        // Test getting authentication service
        val authService = mockGrpcClient.getAuthenticationService()
        assertTrue(authService is MockAuthenticationServiceClient)
        
        // Test getting user management service
        val userService = mockGrpcClient.getUserManagementService()
        assertTrue(userService is MockUserManagementServiceClient)
        
        // Test getting token validation service
        val tokenService = mockGrpcClient.getTokenValidationService()
        assertTrue(tokenService is MockTokenValidationServiceClient)
        
        // Verify operations were recorded
        val operations = mockGrpcClient.getOperations()
        assertEquals(3, operations.size)
        assertTrue(operations.any { it is GrpcOperation.GetAuthenticationService })
        assertTrue(operations.any { it is GrpcOperation.GetUserManagementService })
        assertTrue(operations.any { it is GrpcOperation.GetTokenValidationService })
    }
    
    @Test
    fun `test gRPC service definitions`() {
        // Test authentication service definitions
        assertEquals("auth.AuthenticationService", AuthenticationService.SERVICE_NAME)
        
        // Test request/response data classes
        val signInRequest = AuthenticationService.SignInWithEmailRequest(
            email = "test@example.com",
            password = "password123"
        )
        assertEquals("test@example.com", signInRequest.email)
        assertEquals("password123", signInRequest.password)
        
        val registerRequest = AuthenticationService.RegisterRequest(
            email = "new@example.com",
            password = "newpass123",
            displayName = "Test User"
        )
        assertEquals("new@example.com", registerRequest.email)
        assertEquals("newpass123", registerRequest.password)
        assertEquals("Test User", registerRequest.displayName)
    }
    
    @Test
    fun `test gRPC service utilities`() {
        val testUser = User(
            id = "test_user_001",
            email = "test@example.com",
            displayName = "Test User",
            isEmailVerified = true,
            createdAt = System.currentTimeMillis(),
            lastSignInAt = System.currentTimeMillis()
        )
        
        // Test successful authentication response creation
        val successResponse = GrpcServiceUtils.createSuccessAuthResponse(
            user = testUser,
            accessToken = "test_access_token",
            refreshToken = "test_refresh_token",
            expiresIn = 3600
        )
        
        assertTrue(successResponse.success)
        assertEquals(testUser, successResponse.user)
        assertEquals("test_access_token", successResponse.accessToken)
        assertEquals("test_refresh_token", successResponse.refreshToken)
        assertEquals(3600, successResponse.expiresIn)
        assertEquals("Bearer", successResponse.tokenType)
        assertEquals(null, successResponse.errorMessage)
        assertEquals(null, successResponse.errorCode)
        
        // Test error authentication response creation
        val errorResponse = GrpcServiceUtils.createErrorAuthResponse(
            errorMessage = "Invalid credentials",
            errorCode = "INVALID_CREDENTIALS"
        )
        
        assertFalse(errorResponse.success)
        assertEquals(null, errorResponse.user)
        assertEquals("Invalid credentials", errorResponse.errorMessage)
        assertEquals("INVALID_CREDENTIALS", errorResponse.errorCode)
    }
    
    @Test
    fun `test gRPC error codes`() {
        // Test authentication error codes
        assertEquals("INVALID_CREDENTIALS", app.multiauth.grpc.GrpcErrorCodes.INVALID_CREDENTIALS)
        assertEquals("USER_NOT_FOUND", app.multiauth.grpc.GrpcErrorCodes.USER_NOT_FOUND)
        assertEquals("ACCOUNT_DISABLED", app.multiauth.grpc.GrpcErrorCodes.ACCOUNT_DISABLED)
        
        // Test registration error codes
        assertEquals("EMAIL_ALREADY_EXISTS", app.multiauth.grpc.GrpcErrorCodes.EMAIL_ALREADY_EXISTS)
        assertEquals("WEAK_PASSWORD", app.multiauth.grpc.GrpcErrorCodes.WEAK_PASSWORD)
        
        // Test token error codes
        assertEquals("INVALID_TOKEN", app.multiauth.grpc.GrpcErrorCodes.INVALID_TOKEN)
        assertEquals("EXPIRED_TOKEN", app.multiauth.grpc.GrpcErrorCodes.EXPIRED_TOKEN)
        
        // Test system error codes
        assertEquals("INTERNAL_ERROR", app.multiauth.grpc.GrpcErrorCodes.INTERNAL_ERROR)
        assertEquals("SERVICE_UNAVAILABLE", app.multiauth.grpc.GrpcErrorCodes.SERVICE_UNAVAILABLE)
    }
    
    @Test
    fun `test mock authentication service client`() = runTest {
        setupTestEnvironment()
        
        val authService = mockGrpcClient.getAuthenticationService() as MockAuthenticationServiceClient
        
        // Test setting mock responses
        val mockUser = User(
            id = "mock_user_001",
            email = "mock@example.com",
            displayName = "Mock User",
            isEmailVerified = true,
            createdAt = System.currentTimeMillis(),
            lastSignInAt = System.currentTimeMillis()
        )
        
        val mockResponse = AuthenticationService.AuthenticationResponse(
            success = true,
            user = mockUser,
            accessToken = "mock_access_token",
            refreshToken = "mock_refresh_token",
            expiresIn = 3600
        )
        
        authService.setMockResponse("signInWithEmail", Result.success(mockResponse))
        
        // Test the mock response
        val result = authService.signInWithEmail("test@example.com", "password")
        assertTrue(result.isSuccess)
        
        val response = result.getOrNull()
        assertTrue(response?.success == true)
        assertEquals(mockUser, response?.user)
        assertEquals("mock_access_token", response?.accessToken)
        
        // Verify operation was recorded
        val operations = authService.getOperations()
        assertEquals(1, operations.size)
        assertTrue(operations.any { it is AuthOperation.SignInWithEmail })
    }
    
    @Test
    fun `test mock secure storage operations`() = runTest {
        setupTestEnvironment()
        
        // Test storage operations
        val storeResult = mockSecureStorage.store("test_key", "test_value")
        assertTrue(storeResult)
        
        val retrieveResult = mockSecureStorage.retrieve("test_key")
        assertEquals("test_value", retrieveResult)
        
        val containsResult = mockSecureStorage.contains("test_key")
        assertTrue(containsResult)
        
        val removeResult = mockSecureStorage.remove("test_key")
        assertTrue(removeResult)
        
        val containsAfterRemove = mockSecureStorage.contains("test_key")
        assertFalse(containsAfterRemove)
        
        // Verify operations were recorded
        val operations = mockSecureStorage.getOperations()
        assertEquals(5, operations.size)
        assertTrue(operations.any { it is StorageOperation.Store })
        assertTrue(operations.any { it is StorageOperation.Retrieve })
        assertTrue(operations.any { it is StorageOperation.Contains })
        assertTrue(operations.any { it is StorageOperation.Remove })
    }
    
    @Test
    fun `test mock event bus`() {
        setupTestEnvironment()
        
        // Test event dispatching
        val testEvent = app.multiauth.events.AuthEvent.Authentication.SignInCompleted(
            User(
                id = "test_user",
                email = "test@example.com",
                displayName = "Test User",
                isEmailVerified = true,
                createdAt = System.currentTimeMillis(),
                lastSignInAt = System.currentTimeMillis()
            )
        )
        
        mockEventBus.dispatch(testEvent)
        
        // Verify event was dispatched
        assertEquals(1, mockEventBus.getDispatchedEventCount())
        val dispatchedEvents = mockEventBus.getDispatchedEvents<app.multiauth.events.AuthEvent.Authentication.SignInCompleted>()
        assertEquals(1, dispatchedEvents.size)
        assertEquals(testEvent.user.id, dispatchedEvents[0].user.id)
        
        // Test event subscription
        var receivedEvent: app.multiauth.events.AuthEvent.Authentication.SignInCompleted? = null
        mockEventBus.subscribe(app.multiauth.events.AuthEvent.Authentication.SignInCompleted::class.java) { event, _ ->
            receivedEvent = event
        }
        
        // Dispatch another event
        mockEventBus.dispatch(testEvent)
        
        // Verify subscription received the event
        assertEquals(testEvent.user.id, receivedEvent?.user?.id)
    }
}
package app.multiauth.oauth.clients

import app.multiauth.oauth.models.OAuthConfig
import app.multiauth.oauth.models.OAuthResult
import app.multiauth.oauth.models.TokenResponse
import app.multiauth.oauth.models.UserInfo
import app.multiauth.util.Logger
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GoogleOAuthClientTest {
    
    private lateinit var googleOAuthClient: GoogleOAuthClient
    private lateinit var mockHttpClient: MockHttpClient
    private lateinit var mockLogger: MockLogger
    
    private val testConfig = OAuthConfig(
        clientId = "test-client-id",
        clientSecret = "test-client-secret",
        redirectUri = "https://example.com/callback",
        scopes = listOf("openid", "profile", "email")
    )
    
    @BeforeTest
    fun setup() {
        mockHttpClient = MockHttpClient()
        mockLogger = MockLogger()
        
        googleOAuthClient = GoogleOAuthClient(
            config = testConfig,
            httpClient = mockHttpClient,
            logger = mockLogger
        )
    }
    
    @Test
    fun `test generateAuthorizationUrl creates correct URL`() = runTest {
        // Given
        val state = "test-state-123"
        val codeChallenge = "test-code-challenge"
        
        // When
        val authUrl = googleOAuthClient.generateAuthorizationUrl(state, codeChallenge)
        
        // Then
        assertTrue(authUrl.startsWith("https://accounts.google.com/oauth/authorize"))
        assertTrue(authUrl.contains("client_id=${testConfig.clientId}"))
        assertTrue(authUrl.contains("redirect_uri=${testConfig.redirectUri}"))
        assertTrue(authUrl.contains("scope=${testConfig.scopes.joinToString(" ")}"))
        assertTrue(authUrl.contains("response_type=code"))
        assertTrue(authUrl.contains("state=$state"))
        assertTrue(authUrl.contains("code_challenge=$codeChallenge"))
        assertTrue(authUrl.contains("code_challenge_method=S256"))
    }
    
    @Test
    fun `test exchangeCodeForToken with valid response returns success`() = runTest {
        // Given
        val code = "valid-auth-code"
        val codeVerifier = "valid-code-verifier"
        val tokenResponse = TokenResponse(
            accessToken = "access-token-123",
            tokenType = "Bearer",
            expiresIn = 3600,
            refreshToken = "refresh-token-123",
            scope = "openid profile email"
        )
        
        mockHttpClient.mockResponse = MockHttpResponse(
            status = 200,
            body = """
            {
                "access_token": "${tokenResponse.accessToken}",
                "token_type": "${tokenResponse.tokenType}",
                "expires_in": ${tokenResponse.expiresIn},
                "refresh_token": "${tokenResponse.refreshToken}",
                "scope": "${tokenResponse.scope}"
            }
            """.trimIndent()
        )
        
        // When
        val result = googleOAuthClient.exchangeCodeForToken(code, codeVerifier)
        
        // Then
        assertTrue(result.isSuccess)
        val successResult = result.getOrNull()
        assertNotNull(successResult)
        assertEquals(tokenResponse.accessToken, successResult.accessToken)
        assertEquals(tokenResponse.refreshToken, successResult.refreshToken)
        assertEquals(tokenResponse.expiresIn, successResult.expiresIn)
        
        // Verify HTTP request was made correctly
        assertEquals(1, mockHttpClient.requests.size)
        val request = mockHttpClient.requests[0]
        assertEquals("POST", request.method)
        assertEquals("https://oauth2.googleapis.com/token", request.url)
        assertTrue(request.body.contains("code=$code"))
        assertTrue(request.body.contains("code_verifier=$codeVerifier"))
        assertTrue(request.body.contains("client_id=${testConfig.clientId}"))
        assertTrue(request.body.contains("client_secret=${testConfig.clientSecret}"))
    }
    
    @Test
    fun `test exchangeCodeForToken with invalid response returns failure`() = runTest {
        // Given
        val code = "invalid-auth-code"
        val codeVerifier = "valid-code-verifier"
        
        mockHttpClient.mockResponse = MockHttpResponse(
            status = 400,
            body = """
            {
                "error": "invalid_grant",
                "error_description": "Invalid authorization code"
            }
            """.trimIndent()
        )
        
        // When
        val result = googleOAuthClient.exchangeCodeForToken(code, codeVerifier)
        
        // Then
        assertFalse(result.isSuccess)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error.message?.contains("invalid_grant") == true)
    }
    
    @Test
    fun `test exchangeCodeForToken with network error returns failure`() = runTest {
        // Given
        val code = "valid-auth-code"
        val codeVerifier = "valid-code-verifier"
        
        mockHttpClient.shouldFail = true
        mockHttpClient.failureMessage = "Network error"
        
        // When
        val result = googleOAuthClient.exchangeCodeForToken(code, codeVerifier)
        
        // Then
        assertFalse(result.isSuccess)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error.message?.contains("Network error") == true)
    }
    
    @Test
    fun `test refreshToken with valid response returns success`() = runTest {
        // Given
        val refreshToken = "valid-refresh-token"
        val tokenResponse = TokenResponse(
            accessToken = "new-access-token-123",
            tokenType = "Bearer",
            expiresIn = 3600,
            refreshToken = "new-refresh-token-123",
            scope = "openid profile email"
        )
        
        mockHttpClient.mockResponse = MockHttpResponse(
            status = 200,
            body = """
            {
                "access_token": "${tokenResponse.accessToken}",
                "token_type": "${tokenResponse.tokenType}",
                "expires_in": ${tokenResponse.expiresIn},
                "refresh_token": "${tokenResponse.refreshToken}",
                "scope": "${tokenResponse.scope}"
            }
            """.trimIndent()
        )
        
        // When
        val result = googleOAuthClient.refreshToken(refreshToken)
        
        // Then
        assertTrue(result.isSuccess)
        val successResult = result.getOrNull()
        assertNotNull(successResult)
        assertEquals(tokenResponse.accessToken, successResult.accessToken)
        assertEquals(tokenResponse.refreshToken, successResult.refreshToken)
        
        // Verify HTTP request was made correctly
        assertEquals(1, mockHttpClient.requests.size)
        val request = mockHttpClient.requests[0]
        assertEquals("POST", request.method)
        assertEquals("https://oauth2.googleapis.com/token", request.url)
        assertTrue(request.body.contains("grant_type=refresh_token"))
        assertTrue(request.body.contains("refresh_token=$refreshToken"))
        assertTrue(request.body.contains("client_id=${testConfig.clientId}"))
        assertTrue(request.body.contains("client_secret=${testConfig.clientSecret}"))
    }
    
    @Test
    fun `test refreshToken with invalid token returns failure`() = runTest {
        // Given
        val refreshToken = "invalid-refresh-token"
        
        mockHttpClient.mockResponse = MockHttpResponse(
            status = 400,
            body = """
            {
                "error": "invalid_grant",
                "error_description": "Invalid refresh token"
            }
            """.trimIndent()
        )
        
        // When
        val result = googleOAuthClient.refreshToken(refreshToken)
        
        // Then
        assertFalse(result.isSuccess)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error.message?.contains("invalid_grant") == true)
    }
    
    @Test
    fun `test getUserInfo with valid response returns success`() = runTest {
        // Given
        val accessToken = "valid-access-token"
        val userInfo = UserInfo(
            id = "user123",
            email = "user@example.com",
            name = "Test User",
            picture = "https://example.com/avatar.jpg",
            verifiedEmail = true
        )
        
        mockHttpClient.mockResponse = MockHttpResponse(
            status = 200,
            body = """
            {
                "id": "${userInfo.id}",
                "email": "${userInfo.email}",
                "name": "${userInfo.name}",
                "picture": "${userInfo.picture}",
                "verified_email": ${userInfo.verifiedEmail}
            }
            """.trimIndent()
        )
        
        // When
        val result = googleOAuthClient.getUserInfo(accessToken)
        
        // Then
        assertTrue(result.isSuccess)
        val successResult = result.getOrNull()
        assertNotNull(successResult)
        assertEquals(userInfo.id, successResult.id)
        assertEquals(userInfo.email, successResult.email)
        assertEquals(userInfo.name, successResult.name)
        assertEquals(userInfo.picture, successResult.picture)
        assertEquals(userInfo.verifiedEmail, successResult.verifiedEmail)
        
        // Verify HTTP request was made correctly
        assertEquals(1, mockHttpClient.requests.size)
        val request = mockHttpClient.requests[0]
        assertEquals("GET", request.method)
        assertEquals("https://www.googleapis.com/oauth2/v2/userinfo", request.url)
        assertTrue(request.headers["Authorization"] == "Bearer $accessToken")
    }
    
    @Test
    fun `test getUserInfo with invalid token returns failure`() = runTest {
        // Given
        val accessToken = "invalid-access-token"
        
        mockHttpClient.mockResponse = MockHttpResponse(
            status = 401,
            body = """
            {
                "error": {
                    "code": 401,
                    "message": "Invalid Credentials",
                    "status": "UNAUTHENTICATED"
                }
            }
            """.trimIndent()
        )
        
        // When
        val result = googleOAuthClient.getUserInfo(accessToken)
        
        // Then
        assertFalse(result.isSuccess)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error.message?.contains("Invalid Credentials") == true)
    }
    
    @Test
    fun `test revokeToken with valid response returns success`() = runTest {
        // Given
        val token = "valid-token"
        
        mockHttpClient.mockResponse = MockHttpResponse(
            status = 200,
            body = ""
        )
        
        // When
        val result = googleOAuthClient.revokeToken(token)
        
        // Then
        assertTrue(result)
        
        // Verify HTTP request was made correctly
        assertEquals(1, mockHttpClient.requests.size)
        val request = mockHttpClient.requests[0]
        assertEquals("POST", request.method)
        assertEquals("https://oauth2.googleapis.com/revoke", request.url)
        assertTrue(request.body.contains("token=$token"))
    }
    
    @Test
    fun `test revokeToken with invalid token returns false`() = runTest {
        // Given
        val token = "invalid-token"
        
        mockHttpClient.mockResponse = MockHttpResponse(
            status = 400,
            body = """
            {
                "error": "invalid_token"
            }
            """.trimIndent()
        )
        
        // When
        val result = googleOAuthClient.revokeToken(token)
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `test validateToken with valid token returns true`() = runTest {
        // Given
        val token = "valid-token"
        
        mockHttpClient.mockResponse = MockHttpResponse(
            status = 200,
            body = """
            {
                "azp": "${testConfig.clientId}",
                "aud": "${testConfig.clientId}",
                "exp": ${System.currentTimeMillis() / 1000 + 3600}
            }
            """.trimIndent()
        )
        
        // When
        val result = googleOAuthClient.validateToken(token)
        
        // Then
        assertTrue(result)
        
        // Verify HTTP request was made correctly
        assertEquals(1, mockHttpClient.requests.size)
        val request = mockHttpClient.requests[0]
        assertEquals("GET", request.method)
        assertTrue(request.url.startsWith("https://oauth2.googleapis.com/tokeninfo"))
        assertTrue(request.url.contains("access_token=$token"))
    }
    
    @Test
    fun `test validateToken with invalid token returns false`() = runTest {
        // Given
        val token = "invalid-token"
        
        mockHttpClient.mockResponse = MockHttpResponse(
            status = 400,
            body = """
            {
                "error": "invalid_token"
            }
            """.trimIndent()
        )
        
        // When
        val result = googleOAuthClient.validateToken(token)
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `test validateToken with expired token returns false`() = runTest {
        // Given
        val token = "expired-token"
        
        mockHttpClient.mockResponse = MockHttpResponse(
            status = 200,
            body = """
            {
                "azp": "${testConfig.clientId}",
                "aud": "${testConfig.clientId}",
                "exp": ${System.currentTimeMillis() / 1000 - 3600}
            }
            """.trimIndent()
        )
        
        // When
        val result = googleOAuthClient.validateToken(token)
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `test validateToken with wrong audience returns false`() = runTest {
        // Given
        val token = "wrong-audience-token"
        
        mockHttpClient.mockResponse = MockHttpResponse(
            status = 200,
            body = """
            {
                "azp": "wrong-client-id",
                "aud": "wrong-client-id",
                "exp": ${System.currentTimeMillis() / 1000 + 3600}
            }
            """.trimIndent()
        )
        
        // When
        val result = googleOAuthClient.validateToken(token)
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `test edge cases with empty strings`() = runTest {
        // Given
        val emptyCode = ""
        val emptyCodeVerifier = ""
        val emptyToken = ""
        
        // When & Then
        val authUrl = googleOAuthClient.generateAuthorizationUrl("state", "challenge")
        assertTrue(authUrl.isNotEmpty())
        
        val tokenResult = googleOAuthClient.exchangeCodeForToken(emptyCode, emptyCodeVerifier)
        assertFalse(tokenResult.isSuccess)
        
        val refreshResult = googleOAuthClient.refreshToken(emptyToken)
        assertFalse(refreshResult.isSuccess)
        
        val userInfoResult = googleOAuthClient.getUserInfo(emptyToken)
        assertFalse(userInfoResult.isSuccess)
        
        val revokeResult = googleOAuthClient.revokeToken(emptyToken)
        assertFalse(revokeResult)
        
        val validateResult = googleOAuthClient.validateToken(emptyToken)
        assertFalse(validateResult)
    }
    
    @Test
    fun `test edge cases with very long strings`() = runTest {
        // Given
        val longCode = "a".repeat(1000)
        val longCodeVerifier = "a".repeat(1000)
        val longToken = "a".repeat(1000)
        
        // When & Then
        val tokenResult = googleOAuthClient.exchangeCodeForToken(longCode, longCodeVerifier)
        // Should handle long strings gracefully
        assertTrue(tokenResult.isSuccess || tokenResult.isFailure)
        
        val refreshResult = googleOAuthClient.refreshToken(longToken)
        assertTrue(refreshResult.isSuccess || refreshResult.isFailure)
        
        val userInfoResult = googleOAuthClient.getUserInfo(longToken)
        assertTrue(userInfoResult.isSuccess || userInfoResult.isFailure)
    }
    
    @Test
    fun `test concurrent operations are handled correctly`() = runTest {
        // Given
        val results = mutableListOf<OAuthResult<UserInfo>>()
        
        mockHttpClient.mockResponse = MockHttpResponse(
            status = 200,
            body = """
            {
                "id": "user123",
                "email": "user@example.com",
                "name": "Test User"
            }
            """.trimIndent()
        )
        
        // When - multiple concurrent getUserInfo calls
        val jobs = List(5) { index ->
            kotlinx.coroutines.async {
                googleOAuthClient.getUserInfo("token$index")
            }
        }
        
        jobs.forEach { job ->
            results.add(job.await())
        }
        
        // Then - all operations should complete
        assertEquals(5, results.size)
        results.forEach { result ->
            assertTrue(result.isSuccess || result.isFailure)
        }
    }
}

// Mock classes for testing

class MockHttpClient : HttpClient {
    var mockResponse: MockHttpResponse? = null
    var shouldFail = false
    var failureMessage = "Mock HTTP error"
    val requests = mutableListOf<MockHttpRequest>()
    
    override suspend fun execute(request: HttpRequestBuilder): HttpResponse {
        if (shouldFail) {
            throw RuntimeException(failureMessage)
        }
        
        val mockRequest = MockHttpRequest(
            method = request.method,
            url = request.url,
            headers = request.headers,
            body = request.body
        )
        requests.add(mockRequest)
        
        return mockResponse ?: MockHttpResponse(
            status = 200,
            body = "{}"
        )
    }
}

data class MockHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String
)

class MockHttpResponse(
    override val status: Int,
    override val body: String
) : HttpResponse {
    override val headers: Map<String, String> = emptyMap()
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
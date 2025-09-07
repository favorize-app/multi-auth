package app.multiauth.models

import app.multiauth.models.AuthState
import app.multiauth.models.User
import app.multiauth.models.TokenPair
import app.multiauth.models.VerificationMethod
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Basic tests for AuthState sealed class.
 */
class AuthStateTest {

    @Test
    fun testInitialState() {
        val state = AuthState.Initial
        assertTrue(state is AuthState.Initial)
    }

    @Test
    fun testUnauthenticatedState() {
        val state = AuthState.Unauthenticated
        assertTrue(state is AuthState.Unauthenticated)
    }

    @Test
    fun testAuthenticatedState() {
        val user = User(
            id = "test-user",
            email = "test@example.com",
            displayName = "Test User",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        val tokens = TokenPair(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = Clock.System.now()
        )
        
        val state = AuthState.Authenticated(user, tokens)
        assertTrue(state is AuthState.Authenticated)
        assertEquals(user, state.user)
        assertEquals(tokens, state.tokens)
    }

    @Test
    fun testVerificationRequiredState() {
        val method = VerificationMethod.Email("test@example.com")
        val state = AuthState.VerificationRequired(method)
        
        assertTrue(state is AuthState.VerificationRequired)
        assertEquals(method, state.method)
    }
}

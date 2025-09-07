package app.multiauth.models

import app.multiauth.models.User
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Basic tests for User model.
 */
class UserTest {

    @Test
    fun testUserCreation() {
        val now = Clock.System.now()
        val user = User(
            id = "user123",
            email = "user@example.com",
            displayName = "Test User",
            emailVerified = true,
            createdAt = now,
            updatedAt = now
        )
        
        assertEquals("user123", user.id)
        assertEquals("user@example.com", user.email)
        assertEquals("Test User", user.displayName)
        assertTrue(user.emailVerified)
        assertFalse(user.isAnonymous)
    }

    @Test
    fun testAnonymousUser() {
        val now = Clock.System.now()
        val user = User(
            id = "anon123",
            email = null,
            displayName = "Guest User",
            isAnonymous = true,
            anonymousSessionId = "session123",
            createdAt = now,
            updatedAt = now
        )
        
        assertTrue(user.isAnonymous)
        assertEquals("session123", user.anonymousSessionId)
        assertEquals(null, user.email)
    }
}

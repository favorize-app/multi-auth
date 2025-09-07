package app.multiauth

import app.multiauth.core.AuthStateManager
import app.multiauth.models.AuthState
import app.multiauth.models.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Basic tests for AuthStateManager functionality.
 */
class AuthStateManagerTest {

    @Test
    fun testAuthStateManagerInitialization() {
        val authStateManager = AuthStateManager.getInstance()
        assertNotNull(authStateManager)
        assertEquals(AuthState.Initial, authStateManager.authState.value)
    }

    @Test
    fun testUserPreferencesDefault() {
        val authStateManager = AuthStateManager.getInstance()
        val preferences = authStateManager.userPreferences.value
        assertNotNull(preferences)
        assertEquals("en", preferences.language)
        assertEquals("system", preferences.theme)
    }

    @Test
    fun testUpdateUserPreferences() {
        val authStateManager = AuthStateManager.getInstance()
        val newPreferences = UserPreferences(
            language = "es",
            theme = "dark",
            biometricEnabled = true
        )
        
        authStateManager.updateUserPreferences(newPreferences)
        assertEquals(newPreferences, authStateManager.userPreferences.value)
    }
}

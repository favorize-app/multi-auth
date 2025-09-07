package app.multiauth.models

data class UserPreferences(
    val autoSignIn: Boolean = false,
    val biometricEnabled: Boolean = false,
    val rememberMe: Boolean = true,
    val sessionTimeout: Long = 30 * 60 * 1000L, // 30 minutes
    val language: String = "en",
    val theme: String = "system",
    val notifications: NotificationPreferences = NotificationPreferences()
)

data class NotificationPreferences(
    val emailNotifications: Boolean = true,
    val smsNotifications: Boolean = false,
    val pushNotifications: Boolean = true,
    val securityAlerts: Boolean = true
)

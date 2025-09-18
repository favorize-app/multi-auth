package app.multiauth.models

data class UserPreferences(
    val autoSignIn: Boolean = false,
    val biometricEnabled: Boolean = false,
    val rememberMe: Boolean = true,
    @Deprecated(
        message = "Legacy field. Use TimeoutConstants for new code.",
        replaceWith = ReplaceWith("TimeoutConstants.DEFAULT_SESSION_TIMEOUT")
    )
    // TODO: Migrate sessionTimeout to use TimeoutConstants in future versions
    val sessionTimeout: Long = 30 * 60 * 1000L, // 30 minutes - legacy field, use TimeoutConstants for new code
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

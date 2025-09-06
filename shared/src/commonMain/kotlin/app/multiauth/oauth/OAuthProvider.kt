package app.multiauth.oauth

/**
 * Enum representing all supported OAuth providers.
 */
enum class OAuthProvider(val id: String, val displayName: String) {
    GOOGLE("google", "Google"),
    DISCORD("discord", "Discord"),
    GITHUB("github", "GitHub"),
    MICROSOFT("microsoft", "Microsoft"),
    LINKEDIN("linkedin", "LinkedIn"),
    TWITTER("twitter", "Twitter"),
    TWITCH("twitch", "Twitch"),
    REDDIT("reddit", "Reddit"),
    STEAM("steam", "Steam"),
    EPIC_GAMES("epic_games", "Epic Games"),
    SPOTIFY("spotify", "Spotify"),
    FACEBOOK("facebook", "Facebook"),
    APPLE("apple", "Apple");
    
    companion object {
        /**
         * Gets an OAuth provider by its string identifier.
         */
        fun fromString(id: String): OAuthProvider? {
            return values().find { it.id == id.lowercase() }
        }
        
        /**
         * Gets an OAuth provider by its display name.
         */
        fun fromDisplayName(displayName: String): OAuthProvider? {
            return values().find { it.displayName.equals(displayName, ignoreCase = true) }
        }
        
        /**
         * Gets all provider IDs as a list of strings.
         */
        fun getAllIds(): List<String> = values().map { it.id }
        
        /**
         * Gets all display names as a list of strings.
         */
        fun getAllDisplayNames(): List<String> = values().map { it.displayName }
    }
}

package app.multiauth.oauth.clients

/**
 * OAuth Client Organization Summary
 * 
 * This file previously contained all OAuth client implementations in a single 1500+ line file.
 * All implementations have been moved to an organized structure:
 * 
 * ✅ IMPLEMENTED (Working OAuth providers):
 * - Twitch OAuth → implemented/TwitchOAuthClient.kt
 * - Reddit OAuth → implemented/RedditOAuthClient.kt  
 * - Spotify OAuth → implemented/SpotifyOAuthClient.kt
 * - Facebook OAuth → implemented/FacebookOAuthClient.kt
 * - Epic Games OAuth → implemented/EpicGamesOAuthClient.kt
 * 
 * 🔄 PLACEHOLDERS (Special implementations needed):
 * - Steam OAuth → placeholders/SteamOAuthClient.kt (OpenID-based)
 * - Apple OAuth → placeholders/AppleOAuthClient.kt (JWT-based)
 * 
 * ✅ EXISTING (Already implemented in separate files):
 * - Google OAuth → GoogleOAuthClient.kt
 * - GitHub OAuth → GitHubOAuthClient.kt
 * - Discord OAuth → DiscordOAuthClient.kt
 * - Microsoft OAuth → MicrosoftOAuthClient.kt
 * - LinkedIn OAuth → LinkedInOAuthClient.kt
 * - Twitter OAuth → TwitterOAuthClient.kt
 * 
 * TOTAL OAUTH PROVIDERS: 11 working + 2 placeholders = 13 total
 * 
 * All OAuth clients are properly imported in OAuthClientFactory.kt
 */
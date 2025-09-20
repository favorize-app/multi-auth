import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.WriteProperties
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * Gradle plugin for Multi-Auth library configuration.
 * Processes OAuth provider configurations and generates configuration files.
 */
class MultiAuthPlugin : Plugin<Project> {
    
    override fun apply(project: Project) {
        // Create the extension
        val extension = project.extensions.create<MultiAuthExtension>("multiauth")
        
        // Register task to generate OAuth configuration
        project.tasks.register<GenerateOAuthConfigTask>("generateOAuthConfig") {
            group = "multiauth"
            description = "Generates OAuth configuration files from Gradle configuration"
            
            // Configure inputs and outputs
            oauthProviders.set(extension.oauth.providers.mapValues { (_, config) ->
                mapOf(
                    "clientId" to config.clientId,
                    "clientSecret" to config.clientSecret,
                    "redirectUri" to config.redirectUri,
                    "scopes" to config.scopes,
                    "customAuthUrl" to config.customAuthUrl,
                    "customTokenUrl" to config.customTokenUrl,
                    "customUserInfoUrl" to config.customUserInfoUrl,
                    "customRevokeUrl" to config.customRevokeUrl,
                    "usePKCE" to config.usePKCE,
                    "additionalParams" to config.additionalParams,
                    "isEnabled" to config.isEnabled
                )
            })
            outputFile.set(project.file("${project.buildDir}/generated/resources/oauth-config.json"))
        }
        
        // Register task to generate Kotlin configuration
        project.tasks.register<GenerateKotlinOAuthConfigTask>("generateKotlinOAuthConfig") {
            group = "multiauth"
            description = "Generates Kotlin configuration object for OAuth providers"
            
            oauthProviders.set(extension.oauth.providers.mapValues { (_, config) ->
                mapOf(
                    "clientId" to config.clientId,
                    "clientSecret" to config.clientSecret,
                    "redirectUri" to config.redirectUri,
                    "scopes" to config.scopes,
                    "customAuthUrl" to config.customAuthUrl,
                    "customTokenUrl" to config.customTokenUrl,
                    "customUserInfoUrl" to config.customUserInfoUrl,
                    "customRevokeUrl" to config.customRevokeUrl,
                    "usePKCE" to config.usePKCE,
                    "additionalParams" to config.additionalParams,
                    "isEnabled" to config.isEnabled
                )
            })
            outputFile.set(project.file("${project.buildDir}/generated/kotlin/GeneratedOAuthConfig.kt"))
        }
        
        // Register task to generate Android resources
        project.tasks.register<GenerateAndroidOAuthResourcesTask>("generateAndroidOAuthResources") {
            group = "multiauth"
            description = "Generates Android string resources for OAuth configuration"
            
            dependsOn("generateOAuthConfig")
            
            oauthProviders.set(extension.oauth.providers.mapValues { (_, config) ->
                mapOf(
                    "clientId" to config.clientId,
                    "clientSecret" to config.clientSecret,
                    "redirectUri" to config.redirectUri,
                    "scopes" to config.scopes,
                    "customAuthUrl" to config.customAuthUrl,
                    "customTokenUrl" to config.customTokenUrl,
                    "customUserInfoUrl" to config.customUserInfoUrl,
                    "customRevokeUrl" to config.customRevokeUrl,
                    "usePKCE" to config.usePKCE,
                    "additionalParams" to config.additionalParams,
                    "isEnabled" to config.isEnabled
                )
            })
            outputFile.set(project.file("${project.buildDir}/generated/res/values/oauth_config.xml"))
        }
        
        // Make the generate task run before compilation
        project.tasks.named("compileKotlin") {
            dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig")
        }
        
        // For Android projects, also run before Android compilation
        project.plugins.withId("com.android.application") {
            project.tasks.named("compileDebugKotlin") {
                dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig", "generateAndroidOAuthResources")
            }
            project.tasks.named("compileReleaseKotlin") {
                dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig", "generateAndroidOAuthResources")
            }
        }
        
        project.plugins.withId("com.android.library") {
            project.tasks.named("compileDebugKotlin") {
                dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig", "generateAndroidOAuthResources")
            }
            project.tasks.named("compileReleaseKotlin") {
                dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig", "generateAndroidOAuthResources")
            }
        }
    }
}

/**
 * Extension for Multi-Auth configuration.
 */
open class MultiAuthExtension {
    val oauth = OAuthExtension()
}

/**
 * OAuth configuration extension.
 */
open class OAuthExtension {
    val providers = mutableMapOf<String, OAuthProviderConfiguration>()
    
    /**
     * Configures Google OAuth provider.
     */
    fun google(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration().apply(configure).copy(providerId = "google")
        providers["google"] = config
    }
    
    /**
     * Configures GitHub OAuth provider.
     */
    fun github(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration().apply(configure).copy(providerId = "github")
        providers["github"] = config
    }
    
    /**
     * Configures Discord OAuth provider.
     */
    fun discord(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration().apply(configure).copy(providerId = "discord")
        providers["discord"] = config
    }
    
    /**
     * Configures Microsoft OAuth provider.
     */
    fun microsoft(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration().apply(configure).copy(providerId = "microsoft")
        providers["microsoft"] = config
    }
    
    /**
     * Configures LinkedIn OAuth provider.
     */
    fun linkedin(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration().apply(configure).copy(providerId = "linkedin")
        providers["linkedin"] = config
    }
    
    /**
     * Configures Twitter OAuth provider.
     */
    fun twitter(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration().apply(configure).copy(providerId = "twitter")
        providers["twitter"] = config
    }
    
    /**
     * Configures Twitch OAuth provider.
     */
    fun twitch(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration().apply(configure).copy(providerId = "twitch")
        providers["twitch"] = config
    }
    
    /**
     * Configures Reddit OAuth provider.
     */
    fun reddit(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration().apply(configure).copy(providerId = "reddit")
        providers["reddit"] = config
    }
    
    /**
     * Configures Spotify OAuth provider.
     */
    fun spotify(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration().apply(configure).copy(providerId = "spotify")
        providers["spotify"] = config
    }
    
    /**
     * Configures Apple OAuth provider.
     */
    fun apple(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration().apply(configure).copy(providerId = "apple")
        providers["apple"] = config
    }
    
    /**
     * Configures a custom OAuth provider.
     */
    fun custom(providerId: String, configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration().apply(configure).copy(providerId = providerId)
        providers[providerId] = config
    }
}

/**
 * Configuration for an OAuth provider.
 */
open class OAuthProviderConfiguration {
    var clientId: String = ""
    var clientSecret: String? = null
    var redirectUri: String = ""
    var scopes: List<String> = emptyList()
    var customAuthUrl: String? = null
    var customTokenUrl: String? = null
    var customUserInfoUrl: String? = null
    var customRevokeUrl: String? = null
    var usePKCE: Boolean = true
    var additionalParams: Map<String, String> = emptyMap()
    var isEnabled: Boolean = true
    
    // Internal property for provider ID (set by extension methods)
    var providerId: String = ""
}

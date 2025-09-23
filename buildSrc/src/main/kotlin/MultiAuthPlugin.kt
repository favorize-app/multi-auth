import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.WriteProperties
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.named
import java.io.File

/**
 * DSL marker to prevent scope pollution in the OAuth configuration DSL.
 */
@DslMarker
annotation class OAuthDslMarker

/**
 * Gradle plugin for Multi-Auth library configuration with NamedDomainObjectContainer.
 * Processes OAuth provider configurations and generates configuration files.
 * 
 * Enables the more idiomatic DSL syntax:
 * 
 * multiauth {
 *     oauth {
 *         providers {
 *             google {
 *                 clientId = "google-client-id"
 *                 clientSecret = "google-client-secret"
 *             }
 *             github {
 *                 clientId = "github-client-id"
 *                 clientSecret = "github-client-secret"
 *             }
 *         }
 *     }
 * }
 */
class MultiAuthPlugin : Plugin<Project> {
    
    override fun apply(project: Project) {
        // Create the NamedDomainObjectContainer for OAuth providers
        val providersContainer = project.container(OAuthProviderConfiguration::class.java)
        
        // Create the extension with the container
        val extension = project.extensions.create<MultiAuthExtension>("multiauth", providersContainer)
        
        // Register task to generate OAuth configuration
        project.tasks.register<GenerateOAuthConfigTask>("generateOAuthConfig") {
            group = "multiauth"
            description = "Generates OAuth configuration files from Gradle configuration"
            
            // Configure inputs and outputs
            oauthProviders.set(extension.oauth.providers.associate { config ->
                config.getName() to mapOf(
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
            outputFile.set(project.file("${project.layout.buildDirectory.get()}/generated/resources/oauth-config.json"))
        }
        
        // Register task to generate Kotlin configuration
        project.tasks.register<GenerateKotlinOAuthConfigTask>("generateKotlinOAuthConfig") {
            group = "multiauth"
            description = "Generates Kotlin configuration object for OAuth providers"
            
            oauthProviders.set(extension.oauth.providers.associate { config ->
                config.getName() to mapOf(
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
            outputFile.set(project.file("${project.layout.buildDirectory.get()}/generated/kotlin/GeneratedOAuthConfig.kt"))
        }
        
        // Register task to generate Android resources
        project.tasks.register<GenerateAndroidOAuthResourcesTask>("generateAndroidOAuthResources") {
            group = "multiauth"
            description = "Generates Android string resources for OAuth configuration"
            
            dependsOn("generateOAuthConfig")
            
            oauthProviders.set(extension.oauth.providers.associate { config ->
                config.getName() to mapOf(
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
            outputFile.set(project.file("${project.layout.buildDirectory.get()}/generated/res/values/oauth_config.xml"))
        }
        
        // Make the generate task run before compilation
        // Handle both regular Kotlin projects and Kotlin Multiplatform projects
        project.tasks.findByName("compileKotlin")?.let { compileKotlinTask ->
            compileKotlinTask.dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig")
        }
        
        // For Kotlin Multiplatform projects, also run before common compilation
        project.tasks.findByName("compileCommonMainKotlinMetadata")?.let { compileCommonTask ->
            compileCommonTask.dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig")
        }
        
        // For Android projects, also run before Android compilation
        project.plugins.withId("com.android.application") {
            // Try to find Android compilation tasks (they may not exist in all configurations)
            project.tasks.findByName("compileDebugKotlin")?.let { task ->
                task.dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig", "generateAndroidOAuthResources")
            }
            project.tasks.findByName("compileReleaseKotlin")?.let { task ->
                task.dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig", "generateAndroidOAuthResources")
            }
            // For Kotlin Multiplatform Android targets
            project.tasks.findByName("compileDebugKotlinAndroid")?.let { task ->
                task.dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig", "generateAndroidOAuthResources")
            }
            project.tasks.findByName("compileReleaseKotlinAndroid")?.let { task ->
                task.dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig", "generateAndroidOAuthResources")
            }
        }
        
        project.plugins.withId("com.android.library") {
            project.tasks.findByName("compileDebugKotlin")?.let { task ->
                task.dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig", "generateAndroidOAuthResources")
            }
            project.tasks.findByName("compileReleaseKotlin")?.let { task ->
                task.dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig", "generateAndroidOAuthResources")
            }
            // For Kotlin Multiplatform Android targets
            project.tasks.findByName("compileDebugKotlinAndroid")?.let { task ->
                task.dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig", "generateAndroidOAuthResources")
            }
            project.tasks.findByName("compileReleaseKotlinAndroid")?.let { task ->
                task.dependsOn("generateOAuthConfig", "generateKotlinOAuthConfig", "generateAndroidOAuthResources")
            }
        }
    }
}

/**
 * Extension for Multi-Auth configuration with NamedDomainObjectContainer.
 */
@OAuthDslMarker
open class MultiAuthExtension(
    val oauth: OAuthExtension
) {
    constructor(providers: NamedDomainObjectContainer<OAuthProviderConfiguration>) : this(
        OAuthExtension(providers)
    )
    
    fun oauth(configure: OAuthExtension.() -> Unit) {
        configure(oauth)
    }
}

/**
 * OAuth configuration extension with NamedDomainObjectContainer and method-based DSL.
 */
@OAuthDslMarker
open class OAuthExtension(
    val providers: NamedDomainObjectContainer<OAuthProviderConfiguration>
) {
    fun providers(configure: NamedDomainObjectContainer<OAuthProviderConfiguration>.() -> Unit) {
        configure(providers)
    }
    
    /**
     * Configures Google OAuth provider using method-based DSL.
     */
    fun google(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration("google").apply(configure)
        providers.add(config)
    }
    
    /**
     * Configures GitHub OAuth provider using method-based DSL.
     */
    fun github(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration("github").apply(configure)
        providers.add(config)
    }
    
    /**
     * Configures Discord OAuth provider using method-based DSL.
     */
    fun discord(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration("discord").apply(configure)
        providers.add(config)
    }
    
    /**
     * Configures Microsoft OAuth provider using method-based DSL.
     */
    fun microsoft(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration("microsoft").apply(configure)
        providers.add(config)
    }
    
    /**
     * Configures LinkedIn OAuth provider using method-based DSL.
     */
    fun linkedin(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration("linkedin").apply(configure)
        providers.add(config)
    }
    
    /**
     * Configures Twitter OAuth provider using method-based DSL.
     */
    fun twitter(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration("twitter").apply(configure)
        providers.add(config)
    }
    
    /**
     * Configures Twitch OAuth provider using method-based DSL.
     */
    fun twitch(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration("twitch").apply(configure)
        providers.add(config)
    }
    
    /**
     * Configures Reddit OAuth provider using method-based DSL.
     */
    fun reddit(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration("reddit").apply(configure)
        providers.add(config)
    }
    
    /**
     * Configures Spotify OAuth provider using method-based DSL.
     */
    fun spotify(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration("spotify").apply(configure)
        providers.add(config)
    }
    
    /**
     * Configures Apple OAuth provider using method-based DSL.
     */
    fun apple(configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration("apple").apply(configure)
        providers.add(config)
    }
    
    /**
     * Configures a custom OAuth provider using method-based DSL.
     */
    fun custom(providerId: String, configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration(providerId).apply(configure)
        providers.add(config)
    }
}

/**
 * Configuration for an OAuth provider with name support for NamedDomainObjectContainer.
 */
@OAuthDslMarker
open class OAuthProviderConfiguration(private val providerName: String) : org.gradle.api.Named {
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
    
    // Internal property for provider ID (set by name)
    val providerId: String get() = providerName
    
    override fun getName(): String = providerName
    
    /**
     * Creates a copy of this configuration with the specified provider ID.
     */
    fun copy(providerId: String): OAuthProviderConfiguration {
        return OAuthProviderConfiguration(providerId).apply {
            this.clientId = this@OAuthProviderConfiguration.clientId
            this.clientSecret = this@OAuthProviderConfiguration.clientSecret
            this.redirectUri = this@OAuthProviderConfiguration.redirectUri
            this.scopes = this@OAuthProviderConfiguration.scopes
            this.customAuthUrl = this@OAuthProviderConfiguration.customAuthUrl
            this.customTokenUrl = this@OAuthProviderConfiguration.customTokenUrl
            this.customUserInfoUrl = this@OAuthProviderConfiguration.customUserInfoUrl
            this.customRevokeUrl = this@OAuthProviderConfiguration.customRevokeUrl
            this.usePKCE = this@OAuthProviderConfiguration.usePKCE
            this.additionalParams = this@OAuthProviderConfiguration.additionalParams
            this.isEnabled = this@OAuthProviderConfiguration.isEnabled
        }
    }
}
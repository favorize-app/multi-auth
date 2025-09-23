package app.favorize.multiauth

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import app.favorize.multiauth.tasks.GenerateOAuthConfigTask
import app.favorize.multiauth.tasks.GenerateKotlinOAuthConfigTask
import app.favorize.multiauth.tasks.GenerateAndroidOAuthResourcesTask

/**
 * Gradle plugin for Multi-Auth library configuration.
 * Supports configuration for OAuth, email, SMS, and other providers.
 */
class MultiAuthPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<MultiAuthExtension>("multiauth")

        project.tasks.register<GenerateOAuthConfigTask>("generateOAuthConfig") {
            group = "multiauth"
            description = "Generates OAuth configuration files from Gradle configuration"
            oauthProviders.set(extension.oauth.providers.mapValues { (_, config) -> config.toMap() })
            outputFile.set(project.file("${'$'}{project.buildDir}/generated/resources/oauth-config.json"))
        }
        project.tasks.register<GenerateKotlinOAuthConfigTask>("generateKotlinOAuthConfig") {
            group = "multiauth"
            description = "Generates Kotlin configuration object for OAuth providers"
            oauthProviders.set(extension.oauth.providers.mapValues { (_, config) -> config.toMap() })
            outputFile.set(project.file("${'$'}{project.buildDir}/generated/kotlin/GeneratedOAuthConfig.kt"))
        }
        project.tasks.register<GenerateAndroidOAuthResourcesTask>("generateAndroidOAuthResources") {
            group = "multiauth"
            description = "Generates Android string resources for OAuth configuration"
            dependsOn("generateOAuthConfig")
            oauthProviders.set(extension.oauth.providers.mapValues { (_, config) -> config.toMap() })
            outputFile.set(project.file("${'$'}{project.buildDir}/generated/res/values/oauth_config.xml"))
        }
        // Add similar task registration for email, sms, etc. if needed
    }
}

open class MultiAuthExtension {
    val oauth = OAuthExtension()
    // Add other provider extensions here, e.g.:
    // val email = EmailExtension()
    // val sms = SmsExtension()
}

open class OAuthExtension {
    val providers = mutableMapOf<String, OAuthProviderConfiguration>()
    fun google(configure: OAuthProviderConfiguration.() -> Unit) = addProvider("google", configure)
    fun github(configure: OAuthProviderConfiguration.() -> Unit) = addProvider("github", configure)
    fun discord(configure: OAuthProviderConfiguration.() -> Unit) = addProvider("discord", configure)
    fun microsoft(configure: OAuthProviderConfiguration.() -> Unit) = addProvider("microsoft", configure)
    fun linkedin(configure: OAuthProviderConfiguration.() -> Unit) = addProvider("linkedin", configure)
    fun twitter(configure: OAuthProviderConfiguration.() -> Unit) = addProvider("twitter", configure)
    fun twitch(configure: OAuthProviderConfiguration.() -> Unit) = addProvider("twitch", configure)
    fun custom(id: String, configure: OAuthProviderConfiguration.() -> Unit) = addProvider(id, configure)
    private fun addProvider(id: String, configure: OAuthProviderConfiguration.() -> Unit) {
        val config = OAuthProviderConfiguration().apply(configure).copy(providerId = id)
        providers[id] = config
    }
}

data class OAuthProviderConfiguration(
    val providerId: String = "",
    var clientId: String = "",
    var clientSecret: String? = null,
    var redirectUri: String = "",
    var scopes: List<String> = emptyList(),
    var customAuthUrl: String? = null,
    var customTokenUrl: String? = null,
    var customUserInfoUrl: String? = null,
    var customRevokeUrl: String? = null,
    var usePKCE: Boolean = true,
    var additionalParams: Map<String, String> = emptyMap(),
    var isEnabled: Boolean = true
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "clientId" to clientId,
        "clientSecret" to clientSecret,
        "redirectUri" to redirectUri,
        "scopes" to scopes,
        "customAuthUrl" to customAuthUrl,
        "customTokenUrl" to customTokenUrl,
        "customUserInfoUrl" to customUserInfoUrl,
        "customRevokeUrl" to customRevokeUrl,
        "usePKCE" to usePKCE,
        "additionalParams" to additionalParams,
        "isEnabled" to isEnabled
    )
}
// Add similar configuration/data classes for email, sms, etc. as needed


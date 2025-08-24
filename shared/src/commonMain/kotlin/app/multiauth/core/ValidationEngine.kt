package app.multiauth.core

import app.multiauth.events.*
import app.multiauth.util.Logger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Handles validation of tokens, permissions, and authentication state.
 * Provides utilities for token validation, permission checking, and security validation.
 */
class ValidationEngine private constructor(
    private val eventBus: EventBus = EventBus.getInstance()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _validationResults = MutableStateFlow<Map<String, ValidationResult>>(emptyMap())
    val validationResults: StateFlow<Map<String, ValidationResult>> = _validationResults.asStateFlow()
    
    init {
        Logger.info("ValidationEngine", "ValidationEngine initialized")
    }
    
    /**
     * Validate an access token.
     */
    suspend fun validateAccessToken(token: String): ValidationResult {
        Logger.debug("ValidationEngine", "Validating access token")
        
        return try {
            // TODO: Implement actual JWT validation
            // For now, perform basic validation
            
            if (token.isBlank()) {
                return ValidationResult.Failure(ValidationError.InvalidToken("Token is empty"))
            }
            
            if (!token.startsWith("access_token_")) {
                return ValidationResult.Failure(ValidationError.InvalidToken("Invalid token format"))
            }
            
            // Extract user ID and timestamp from token
            val parts = token.split("_")
            if (parts.size < 4) {
                return ValidationResult.Failure(ValidationError.InvalidToken("Malformed token"))
            }
            
            val timestamp = parts.last().toLongOrNull()
            if (timestamp == null) {
                return ValidationResult.Failure(ValidationError.InvalidToken("Invalid timestamp in token"))
            }
            
            val tokenAge = Clock.System.now().toEpochMilliseconds() - timestamp
            val maxAge = 30 * 60 * 1000L // 30 minutes in milliseconds
            
            if (tokenAge > maxAge) {
                return ValidationResult.Failure(ValidationError.TokenExpired("Token has expired"))
            }
            
            val result = ValidationResult.Success(
                TokenValidation(
                    isValid = true,
                    userId = parts[2],
                    issuedAt = Instant.fromEpochMilliseconds(timestamp),
                    expiresAt = Instant.fromEpochMilliseconds(timestamp + maxAge),
                    permissions = extractPermissions(token)
                )
            )
            
            // Cache validation result
            cacheValidationResult(token, result)
            
            eventBus.dispatch(AuthEvent.Validation.TokenValidationCompleted(token, true), "ValidationEngine")
            
            result
            
        } catch (e: Exception) {
            val error = ValidationError.ValidationFailed("Token validation failed: ${e.message}")
            val result = ValidationResult.Failure(error)
            
            cacheValidationResult(token, result)
            eventBus.dispatch(AuthEvent.Validation.TokenValidationCompleted(token, false), "ValidationEngine")
            
            result
        }
    }
    
    /**
     * Validate user permissions for a specific action.
     */
    suspend fun validatePermissions(
        userId: String,
        requiredPermissions: List<String>,
        resource: String? = null
    ): ValidationResult {
        Logger.debug("ValidationEngine", "Validating permissions for user: $userId")
        
        return try {
            // TODO: Implement actual permission validation with backend
            // For now, simulate permission checking
            
            val userPermissions = getUserPermissions(userId)
            val hasAllPermissions = requiredPermissions.all { permission ->
                userPermissions.contains(permission)
            }
            
            if (hasAllPermissions) {
                val result = ValidationResult.Success(
                    PermissionValidation(
                        userId = userId,
                        hasPermission = true,
                        grantedPermissions = requiredPermissions,
                        resource = resource
                    )
                )
                
                eventBus.dispatch(
                    AuthEvent.Validation.PermissionValidationCompleted(userId, requiredPermissions, true),
                    "ValidationEngine"
                )
                
                result
            } else {
                val missingPermissions = requiredPermissions - userPermissions
                val error = ValidationError.InsufficientPermissions(
                    "Missing permissions: ${missingPermissions.joinToString(", ")}",
                    missingPermissions
                )
                
                val result = ValidationResult.Failure(error)
                
                eventBus.dispatch(
                    AuthEvent.Validation.PermissionValidationCompleted(userId, requiredPermissions, false),
                    "ValidationEngine"
                )
                
                result
            }
            
        } catch (e: Exception) {
            val error = ValidationError.ValidationFailed("Permission validation failed: ${e.message}")
            ValidationResult.Failure(error)
        }
    }
    
    /**
     * Validate email format and domain.
     */
    fun validateEmail(email: String): ValidationResult {
        Logger.debug("ValidationEngine", "Validating email: $email")
        
        return try {
            if (email.isBlank()) {
                return ValidationResult.Failure(ValidationError.InvalidEmail("Email is empty"))
            }
            
            if (!email.contains("@")) {
                return ValidationResult.Failure(ValidationError.InvalidEmail("Email must contain @ symbol"))
            }
            
            val parts = email.split("@")
            if (parts.size != 2) {
                return ValidationResult.Failure(ValidationError.InvalidEmail("Invalid email format"))
            }
            
            val localPart = parts[0]
            val domain = parts[1]
            
            if (localPart.isBlank() || domain.isBlank()) {
                return ValidationResult.Failure(ValidationError.InvalidEmail("Local part and domain cannot be empty"))
            }
            
            if (localPart.length > 64 || domain.length > 255) {
                return ValidationResult.Failure(ValidationError.InvalidEmail("Email parts exceed maximum length"))
            }
            
            // Basic domain validation
            if (!domain.contains(".")) {
                return ValidationResult.Failure(ValidationError.InvalidEmail("Domain must contain at least one dot"))
            }
            
            ValidationResult.Success(
                EmailValidation(
                    isValid = true,
                    email = email,
                    localPart = localPart,
                    domain = domain
                )
            )
            
        } catch (e: Exception) {
            ValidationResult.Failure(ValidationError.ValidationFailed("Email validation failed: ${e.message}"))
        }
    }
    
    /**
     * Validate phone number format.
     */
    fun validatePhoneNumber(phoneNumber: String): ValidationResult {
        Logger.debug("ValidationEngine", "Validating phone number: $phoneNumber")
        
        return try {
            if (phoneNumber.isBlank()) {
                return ValidationResult.Failure(ValidationError.InvalidPhoneNumber("Phone number is empty"))
            }
            
            // Remove common separators
            val cleanNumber = phoneNumber.replace(Regex("[\\s\\-\\(\\)\\.]"), "")
            
            if (cleanNumber.length < 7 || cleanNumber.length > 15) {
                return ValidationResult.Failure(ValidationError.InvalidPhoneNumber("Phone number length invalid"))
            }
            
            if (!cleanNumber.all { it.isDigit() }) {
                return ValidationResult.Failure(ValidationError.InvalidPhoneNumber("Phone number must contain only digits"))
            }
            
            ValidationResult.Success(
                PhoneNumberValidation(
                    isValid = true,
                    originalNumber = phoneNumber,
                    cleanNumber = cleanNumber,
                    countryCode = extractCountryCode(cleanNumber)
                )
            )
            
        } catch (e: Exception) {
            ValidationResult.Failure(ValidationError.ValidationFailed("Phone number validation failed: ${e.message}"))
        }
    }
    
    /**
     * Validate password strength.
     */
    fun validatePassword(password: String): ValidationResult {
        Logger.debug("ValidationEngine", "Validating password strength")
        
        return try {
            if (password.isBlank()) {
                return ValidationResult.Failure(ValidationError.WeakPassword("Password is empty"))
            }
            
            if (password.length < 8) {
                return ValidationResult.Failure(ValidationError.WeakPassword("Password must be at least 8 characters"))
            }
            
            val hasUpperCase = password.any { it.isUpperCase() }
            val hasLowerCase = password.any { it.isLowerCase() }
            val hasDigit = password.any { it.isDigit() }
            val hasSpecialChar = password.any { !it.isLetterOrDigit() }
            
            val score = listOf(hasUpperCase, hasLowerCase, hasDigit, hasSpecialChar)
                .count { it }
            
            val strength = when {
                score >= 4 -> PasswordStrength.STRONG
                score >= 3 -> PasswordStrength.MEDIUM
                score >= 2 -> PasswordStrength.WEAK
                else -> PasswordStrength.VERY_WEAK
            }
            
            if (strength == PasswordStrength.VERY_WEAK) {
                return ValidationResult.Failure(ValidationError.WeakPassword("Password is too weak"))
            }
            
            ValidationResult.Success(
                PasswordValidation(
                    isValid = true,
                    strength = strength,
                    score = score,
                    hasUpperCase = hasUpperCase,
                    hasLowerCase = hasLowerCase,
                    hasDigit = hasDigit,
                    hasSpecialChar = hasSpecialChar
                )
            )
            
        } catch (e: Exception) {
            ValidationResult.Failure(ValidationError.ValidationFailed("Password validation failed: ${e.message}"))
        }
    }
    
    /**
     * Clear validation cache for a specific token.
     */
    fun clearValidationCache(token: String) {
        _validationResults.value = _validationResults.value - token
    }
    
    /**
     * Clear all validation cache.
     */
    fun clearAllValidationCache() {
        _validationResults.value = emptyMap()
    }
    
    private fun cacheValidationResult(token: String, result: ValidationResult) {
        _validationResults.value = _validationResults.value + (token to result)
    }
    
    private fun extractPermissions(token: String): List<String> {
        // TODO: Implement actual permission extraction from JWT
        // For now, return basic permissions
        return listOf("read:profile", "write:profile")
    }
    
    private fun getUserPermissions(userId: String): List<String> {
        // TODO: Implement actual permission fetching from backend
        // For now, return mock permissions
        return when {
            userId.startsWith("admin") -> listOf("read:profile", "write:profile", "admin:all")
            userId.startsWith("moderator") -> listOf("read:profile", "write:profile", "moderate:content")
            else -> listOf("read:profile", "write:profile")
        }
    }
    
    private fun extractCountryCode(phoneNumber: String): String? {
        // TODO: Implement proper country code detection
        // For now, assume US numbers start with 1
        return if (phoneNumber.startsWith("1") && phoneNumber.length == 11) "1" else null
    }
    
    companion object {
        private var INSTANCE: ValidationEngine? = null
        
        fun getInstance(): ValidationEngine {
            return INSTANCE ?: ValidationEngine().also { INSTANCE = it }
        }
        
        fun reset() {
            INSTANCE = null
        }
    }
}

/**
 * Result of a validation operation.
 */
sealed class ValidationResult {
    data class Success<T>(val data: T) : ValidationResult()
    data class Failure(val error: ValidationError) : ValidationResult()
    
    fun isSuccess(): Boolean = this is Success<*> // ?
    fun isFailure(): Boolean = this is Failure
    
            fun getOrNull(): Any? = when (this) {
            is Success<*> -> data
            is Failure -> null
        }
}

/**
 * Validation errors that can occur during validation.
 */
sealed class ValidationError(message: String) : Exception(message) {
    data class InvalidToken(override val message: String) : ValidationError(message)
    data class TokenExpired(override val message: String) : ValidationError(message)
    data class InsufficientPermissions(
        override val message: String,
        val missingPermissions: List<String>
    ) : ValidationError(message)
    data class InvalidEmail(override val message: String) : ValidationError(message)
    data class InvalidPhoneNumber(override val message: String) : ValidationError(message)
    data class WeakPassword(override val message: String) : ValidationError(message)
    data class ValidationFailed(override val message: String) : ValidationError(message)
}

/**
 * Token validation result.
 */
data class TokenValidation(
    val isValid: Boolean,
    val userId: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val permissions: List<String>
)

/**
 * Permission validation result.
 */
data class PermissionValidation(
    val userId: String,
    val hasPermission: Boolean,
    val grantedPermissions: List<String>,
    val resource: String?
)

/**
 * Email validation result.
 */
data class EmailValidation(
    val isValid: Boolean,
    val email: String,
    val localPart: String,
    val domain: String
)

/**
 * Phone number validation result.
 */
data class PhoneNumberValidation(
    val isValid: Boolean,
    val originalNumber: String,
    val cleanNumber: String,
    val countryCode: String?
)

/**
 * Password validation result.
 */
data class PasswordValidation(
    val isValid: Boolean,
    val strength: PasswordStrength,
    val score: Int,
    val hasUpperCase: Boolean,
    val hasLowerCase: Boolean,
    val hasDigit: Boolean,
    val hasSpecialChar: Boolean
)

/**
 * Password strength levels.
 */
enum class PasswordStrength {
    VERY_WEAK,
    WEAK,
    MEDIUM,
    STRONG
}
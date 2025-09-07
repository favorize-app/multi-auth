package app.multiauth.models

import kotlinx.serialization.Serializable

/**
 * Errors that can occur during session operations.
 */
@Serializable
sealed class SessionError(open val message: String, val code: String) {
    data class SessionNotFound(override val message: String = "Session not found") : SessionError(message, "SESSION_NOT_FOUND")
    data class SessionExpired(override val message: String = "Session has expired") : SessionError(message, "SESSION_EXPIRED")
    data class SessionRevoked(override val message: String = "Session has been revoked") : SessionError(message, "SESSION_REVOKED")
    data class InvalidSession(override val message: String = "Invalid session") : SessionError(message, "INVALID_SESSION")
    data class StorageFailure(override val message: String = "Session storage failure") : SessionError(message, "STORAGE_FAILURE")
    data class UnknownError(override val message: String = "Unknown session error") : SessionError(message, "UNKNOWN_ERROR")
}

/**
 * Token-related errors.
 */
@Serializable
sealed class TokenError(open val message: String, val code: String) {
    data class InvalidToken(override val message: String = "Invalid token") : TokenError(message, "INVALID_TOKEN")
    data class TokenExpired(override val message: String = "Token has expired") : TokenError(message, "TOKEN_EXPIRED") 
    data class TokenRevoked(override val message: String = "Token has been revoked") : TokenError(message, "TOKEN_REVOKED")
    data class MalformedToken(override val message: String = "Malformed token") : TokenError(message, "MALFORMED_TOKEN")
}

/**
 * Storage-related errors.
 */
@Serializable
sealed class StorageFailure(open val message: String, val code: String) {
    data class ReadFailure(override val message: String = "Failed to read from storage") : StorageFailure(message, "READ_FAILURE")
    data class WriteFailure(override val message: String = "Failed to write to storage") : StorageFailure(message, "WRITE_FAILURE")
    data class DeleteFailure(override val message: String = "Failed to delete from storage") : StorageFailure(message, "DELETE_FAILURE")
    data class CorruptedData(override val message: String = "Data is corrupted") : StorageFailure(message, "CORRUPTED_DATA")
    data class StorageUnavailable(override val message: String = "Storage is unavailable") : StorageFailure(message, "STORAGE_UNAVAILABLE")
}

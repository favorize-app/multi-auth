@file:OptIn(ExperimentalTime::class)

package app.multiauth.persistence

import app.multiauth.models.*
import app.multiauth.events.AuthEvent
import app.multiauth.events.EventMetadata
import app.multiauth.events.Authentication
import app.multiauth.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.Clock

/**
 * Simple persistence adapter interface for different storage backends.
 * Clean abstraction that allows easy implementation of SQL, NoSQL, and event store adapters.
 */
interface PersistenceAdapter {
    suspend fun saveUser(user: User): Boolean
    suspend fun findUserById(id: String): User?
    suspend fun findUserByEmail(email: String): User?
    suspend fun deleteUser(id: String): Boolean

    suspend fun saveSession(session: Session): Boolean
    suspend fun findSessionById(id: String): Session?
    suspend fun findActiveSessions(userId: String): List<Session>
    suspend fun expireSession(sessionId: String): Boolean

    suspend fun saveAuthEvent(event: AuthEvent, metadata: EventMetadata): String
    suspend fun healthCheck(): Boolean
}

/**
 * PostgreSQL adapter implementation.
 * Demonstrates SQL-based persistence with proper table structure.
 */
class PostgreSQLAdapter(
    private val connectionString: String
) : PersistenceAdapter {

    private val logger = Logger.getLogger(this::class)

    override suspend fun saveUser(user: User): Boolean {
        return try {
            logger.debug("postgresql", "Saving user: ${user.id}")

            // Example SQL for PostgreSQL with JSONB support
            val sql = """
                INSERT INTO users (id, email, display_name, email_verified, phone_verified, created_at, updated_at, auth_methods)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (id) DO UPDATE SET
                    email = EXCLUDED.email,
                    display_name = EXCLUDED.display_name,
                    email_verified = EXCLUDED.email_verified,
                    phone_verified = EXCLUDED.phone_verified,
                    updated_at = EXCLUDED.updated_at,
                    auth_methods = EXCLUDED.auth_methods
            """.trimIndent()

            // TODO: Execute with actual PostgreSQL driver (e.g., kotlinx-coroutines-jdbc)
            logger.debug("postgresql", "SQL prepared: ${sql.take(50)}...")

            true
        } catch (e: Exception) {
            logger.error("postgresql", "Failed to save user: ${user.id}", e)
            false
        }
    }

    override suspend fun findUserById(id: String): User? {
        return try {
            logger.debug("postgresql", "Finding user by ID: $id")

            val sql = "SELECT * FROM users WHERE id = ? LIMIT 1"

            // TODO: Execute query and parse result
            logger.debug("postgresql", "Query prepared for user: $id")

            null // Placeholder - would return parsed User
        } catch (e: Exception) {
            logger.error("postgresql", "Failed to find user: $id", e)
            null
        }
    }

    override suspend fun findUserByEmail(email: String): User? {
        return try {
            logger.debug("postgresql", "Finding user by email")

            val sql = "SELECT * FROM users WHERE email = ? LIMIT 1"

            // TODO: Execute query and parse result
            logger.debug("postgresql", "Query prepared for email lookup")

            null // Placeholder
        } catch (e: Exception) {
            logger.error("postgresql", "Failed to find user by email", e)
            null
        }
    }

    override suspend fun deleteUser(id: String): Boolean {
        return try {
            logger.debug("postgresql", "Deleting user: $id")

            val sql = "DELETE FROM users WHERE id = ?"

            // TODO: Execute delete and check rows affected
            logger.debug("postgresql", "Delete prepared for user: $id")

            true // Placeholder
        } catch (e: Exception) {
            logger.error("postgresql", "Failed to delete user: $id", e)
            false
        }
    }

    override suspend fun saveSession(session: Session): Boolean {
        return try {
            logger.debug("postgresql", "Saving session: ${session.id}")

            val sql = """
                INSERT INTO sessions (id, user_id, token, refresh_token, created_at, expires_at, status, device_info)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (id) DO UPDATE SET
                    expires_at = EXCLUDED.expires_at,
                    status = EXCLUDED.status
            """.trimIndent()

            // TODO: Execute with actual driver
            logger.debug("postgresql", "Session SQL prepared: ${session.id}")

            true
        } catch (e: Exception) {
            logger.error("postgresql", "Failed to save session: ${session.id}", e)
            false
        }
    }

    override suspend fun findSessionById(id: String): Session? {
        return try {
            logger.debug("postgresql", "Finding session: $id")

            val sql = "SELECT * FROM sessions WHERE id = ? AND status = 'ACTIVE' LIMIT 1"

            // TODO: Execute and parse
            logger.debug("postgresql", "Session query prepared: $id")

            null // Placeholder
        } catch (e: Exception) {
            logger.error("postgresql", "Failed to find session: $id", e)
            null
        }
    }

    override suspend fun findActiveSessions(userId: String): List<Session> {
        return try {
            logger.debug("postgresql", "Finding active sessions for user: $userId")

            val sql = "SELECT * FROM sessions WHERE user_id = ? AND status = 'ACTIVE' ORDER BY created_at DESC"

            // TODO: Execute and parse
            logger.debug("postgresql", "Active sessions query prepared for user: $userId")

            emptyList() // Placeholder
        } catch (e: Exception) {
            logger.error("postgresql", "Failed to find active sessions: $userId", e)
            emptyList()
        }
    }

    override suspend fun expireSession(sessionId: String): Boolean {
        return try {
            logger.debug("postgresql", "Expiring session: $sessionId")

            val sql = "UPDATE sessions SET status = 'EXPIRED' WHERE id = ?"

            // TODO: Execute update
            logger.debug("postgresql", "Session expiration prepared: $sessionId")

            true
        } catch (e: Exception) {
            logger.error("postgresql", "Failed to expire session: $sessionId", e)
            false
        }
    }

    override suspend fun saveAuthEvent(event: AuthEvent, metadata: EventMetadata): String {
        return try {
            val eventId = "evt_${Clock.System.now().toEpochMilliseconds()}"
            logger.debug("postgresql", "Saving auth event: $eventId")

            val sql = "INSERT INTO auth_events (id, event_type, user_id, created_at, metadata) VALUES (?, ?, ?, ?, ?::jsonb)"

            // TODO: Execute with actual driver
            logger.debug("postgresql", "Auth event SQL prepared: $eventId")

            eventId
        } catch (e: Exception) {
            logger.error("postgresql", "Failed to save auth event", e)
            "error"
        }
    }

    override suspend fun healthCheck(): Boolean {
        return try {
            logger.debug("postgresql", "Performing health check")

            val sql = "SELECT 1"

            // TODO: Execute simple query
            logger.debug("postgresql", "Health check query prepared")

            true
        } catch (e: Exception) {
            logger.error("postgresql", "Health check failed", e)
            false
        }
    }
}

/**
 * MongoDB adapter implementation.
 * Demonstrates document-based persistence with flexible schema.
 */
class MongoDBAdapter(
    private val connectionString: String,
    private val databaseName: String = "multiauth"
) : PersistenceAdapter {

    private val logger = Logger.getLogger(this::class)

    override suspend fun saveUser(user: User): Boolean {
        return try {
            logger.debug("mongodb", "Saving user: ${user.id}")

            // MongoDB document structure
            val userDoc = mapOf(
                "_id" to user.id,
                "email" to user.email,
                "displayName" to user.displayName,
                "emailVerified" to user.emailVerified,
                "phoneVerified" to user.phoneVerified,
                "createdAt" to user.createdAt.toString(),
                "updatedAt" to user.updatedAt.toString(),
                "authMethods" to user.authMethods.map { it.toString() }
            )

            // TODO: Execute with actual MongoDB driver
            logger.debug("mongodb", "User document prepared: ${user.id}")

            true
        } catch (e: Exception) {
            logger.error("mongodb", "Failed to save user: ${user.id}", e)
            false
        }
    }

    override suspend fun findUserById(id: String): User? {
        return try {
            logger.debug("mongodb", "Finding user: $id")

            // MongoDB find operation: db.users.findOne({_id: id})
            logger.debug("mongodb", "User lookup prepared: $id")

            null // TODO: Implement with actual MongoDB driver
        } catch (e: Exception) {
            logger.error("mongodb", "Failed to find user: $id", e)
            null
        }
    }

    override suspend fun findUserByEmail(email: String): User? {
        return try {
            logger.debug("mongodb", "Finding user by email")

            // MongoDB find operation: db.users.findOne({email: email})
            logger.debug("mongodb", "User email lookup prepared")

            null // TODO: Implement with actual MongoDB driver
        } catch (e: Exception) {
            logger.error("mongodb", "Failed to find user by email", e)
            null
        }
    }

    override suspend fun deleteUser(id: String): Boolean {
        return try {
            logger.debug("mongodb", "Deleting user: $id")

            // MongoDB delete operation: db.users.deleteOne({_id: id})
            logger.debug("mongodb", "User deletion prepared: $id")

            true // TODO: Implement with actual MongoDB driver
        } catch (e: Exception) {
            logger.error("mongodb", "Failed to delete user: $id", e)
            false
        }
    }

    override suspend fun saveSession(session: Session): Boolean {
        return try {
            logger.debug("mongodb", "Saving session: ${session.id}")

            val sessionDoc = mapOf(
                "_id" to session.id,
                "userId" to session.userId,
                "token" to session.token,
                "refreshToken" to session.refreshToken,
                "createdAt" to session.createdAt.toString(),
                "expiresAt" to session.expiresAt.toString(),
                "status" to session.status.name,
                "deviceInfo" to session.deviceInfo?.toString(),
                "metadata" to session.metadata
            )

            // TODO: Execute with actual MongoDB driver
            logger.debug("mongodb", "Session document prepared: ${session.id}")

            true
        } catch (e: Exception) {
            logger.error("mongodb", "Failed to save session: ${session.id}", e)
            false
        }
    }

    override suspend fun findSessionById(id: String): Session? {
        return try {
            logger.debug("mongodb", "Finding session: $id")

            // MongoDB find: db.sessions.findOne({_id: id, status: "ACTIVE"})
            logger.debug("mongodb", "Session lookup prepared: $id")

            null // TODO: Implement with actual MongoDB driver
        } catch (e: Exception) {
            logger.error("mongodb", "Failed to find session: $id", e)
            null
        }
    }

    override suspend fun findActiveSessions(userId: String): List<Session> {
        return try {
            logger.debug("mongodb", "Finding active sessions for user: $userId")

            // MongoDB find: db.sessions.find({userId: userId, status: "ACTIVE"}).sort({createdAt: -1})
            logger.debug("mongodb", "Active sessions query prepared: $userId")

            emptyList() // TODO: Implement with actual MongoDB driver
        } catch (e: Exception) {
            logger.error("mongodb", "Failed to find active sessions: $userId", e)
            emptyList()
        }
    }

    override suspend fun expireSession(sessionId: String): Boolean {
        return try {
            logger.debug("mongodb", "Expiring session: $sessionId")

            // MongoDB update: db.sessions.updateOne({_id: sessionId}, {$set: {status: "EXPIRED"}})
            logger.debug("mongodb", "Session expiration prepared: $sessionId")

            true // TODO: Implement with actual MongoDB driver
        } catch (e: Exception) {
            logger.error("mongodb", "Failed to expire session: $sessionId", e)
            false
        }
    }

    override suspend fun saveAuthEvent(event: AuthEvent, metadata: EventMetadata): String {
        return try {
            val eventId = "evt_${Clock.System.now().toEpochMilliseconds()}"
            logger.debug("mongodb", "Saving auth event: $eventId")

            val eventDoc = mapOf(
                "_id" to eventId,
                "eventType" to event::class.simpleName,
                "source" to metadata.source,
                "timestamp" to metadata.timestamp,
                "createdAt" to Clock.System.now().toString()
            )

            // TODO: Execute with actual MongoDB driver
            logger.debug("mongodb", "Auth event document prepared: $eventId")

            eventId
        } catch (e: Exception) {
            logger.error("mongodb", "Failed to save auth event", e)
            "error"
        }
    }

    override suspend fun healthCheck(): Boolean {
        return try {
            logger.debug("mongodb", "Performing health check")

            // MongoDB ping: db.runCommand({ping: 1})
            logger.debug("mongodb", "Health check prepared")

            true // TODO: Implement with actual MongoDB driver
        } catch (e: Exception) {
            logger.error("mongodb", "Health check failed", e)
            false
        }
    }
}

/**
 * Firestore adapter implementation.
 * Demonstrates Google Cloud Firestore integration.
 */
class FirestoreAdapter(
    private val projectId: String
) : PersistenceAdapter {

    private val logger = Logger.getLogger(this::class)

    override suspend fun saveUser(user: User): Boolean {
        return try {
            logger.debug("firestore", "Saving user: ${user.id}")

            // Firestore document structure
            val userDoc = mapOf(
                "id" to user.id,
                "email" to user.email,
                "displayName" to user.displayName,
                "emailVerified" to user.emailVerified,
                "phoneVerified" to user.phoneVerified,
                "createdAt" to user.createdAt.toString(),
                "updatedAt" to user.updatedAt.toString()
            )

            // TODO: Execute with Firestore SDK
            // firestore.collection("users").document(user.id).set(userDoc)
            logger.debug("firestore", "User document prepared: ${user.id}")

            true
        } catch (e: Exception) {
            logger.error("firestore", "Failed to save user: ${user.id}", e)
            false
        }
    }

    override suspend fun findUserById(id: String): User? {
        return try {
            logger.debug("firestore", "Finding user: $id")

            // TODO: Execute with Firestore SDK
            // firestore.collection("users").document(id).get()
            logger.debug("firestore", "User lookup prepared: $id")

            null // Placeholder
        } catch (e: Exception) {
            logger.error("firestore", "Failed to find user: $id", e)
            null
        }
    }

    override suspend fun findUserByEmail(email: String): User? {
        return try {
            logger.debug("firestore", "Finding user by email")

            // TODO: Execute with Firestore SDK
            // firestore.collection("users").where("email", "==", email).limit(1).get()
            logger.debug("firestore", "User email lookup prepared")

            null // Placeholder
        } catch (e: Exception) {
            logger.error("firestore", "Failed to find user by email", e)
            null
        }
    }

    override suspend fun deleteUser(id: String): Boolean {
        return try {
            logger.debug("firestore", "Deleting user: $id")

            // TODO: Execute with Firestore SDK
            // firestore.collection("users").document(id).delete()
            logger.debug("firestore", "User deletion prepared: $id")

            true
        } catch (e: Exception) {
            logger.error("firestore", "Failed to delete user: $id", e)
            false
        }
    }

    override suspend fun saveSession(session: Session): Boolean {
        return try {
            logger.debug("firestore", "Saving session: ${session.id}")

            val sessionDoc = mapOf(
                "id" to session.id,
                "userId" to session.userId,
                "token" to session.token,
                "createdAt" to session.createdAt.toString(),
                "expiresAt" to session.expiresAt.toString(),
                "status" to session.status.name
            )

            // TODO: Execute with Firestore SDK
            logger.debug("firestore", "Session document prepared: ${session.id}")

            true
        } catch (e: Exception) {
            logger.error("firestore", "Failed to save session: ${session.id}", e)
            false
        }
    }

    override suspend fun findSessionById(id: String): Session? {
        return try {
            logger.debug("firestore", "Finding session: $id")

            // TODO: Execute with Firestore SDK
            logger.debug("firestore", "Session lookup prepared: $id")

            null
        } catch (e: Exception) {
            logger.error("firestore", "Failed to find session: $id", e)
            null
        }
    }

    override suspend fun findActiveSessions(userId: String): List<Session> {
        return try {
            logger.debug("firestore", "Finding active sessions for user: $userId")

            // TODO: Execute with Firestore SDK
            logger.debug("firestore", "Active sessions query prepared: $userId")

            emptyList()
        } catch (e: Exception) {
            logger.error("firestore", "Failed to find active sessions: $userId", e)
            emptyList()
        }
    }

    override suspend fun expireSession(sessionId: String): Boolean {
        return try {
            logger.debug("firestore", "Expiring session: $sessionId")

            // TODO: Execute with Firestore SDK
            logger.debug("firestore", "Session expiration prepared: $sessionId")

            true
        } catch (e: Exception) {
            logger.error("firestore", "Failed to expire session: $sessionId", e)
            false
        }
    }

    override suspend fun saveAuthEvent(event: AuthEvent, metadata: EventMetadata): String {
        return try {
            val eventId = "evt_${Clock.System.now().toEpochMilliseconds()}"
            logger.debug("firestore", "Saving auth event: $eventId")

            val eventDoc = mapOf(
                "id" to eventId,
                "eventType" to event::class.simpleName,
                "source" to metadata.source,
                "timestamp" to metadata.timestamp,
                "createdAt" to Clock.System.now().toString()
            )

            // TODO: Execute with Firestore SDK
            logger.debug("firestore", "Auth event document prepared: $eventId")

            eventId
        } catch (e: Exception) {
            logger.error("firestore", "Failed to save auth event", e)
            "error"
        }
    }

    override suspend fun healthCheck(): Boolean {
        return try {
            logger.debug("firestore", "Performing health check")

            // TODO: Execute with Firestore SDK - simple read operation
            logger.debug("firestore", "Health check prepared")

            true
        } catch (e: Exception) {
            logger.error("firestore", "Health check failed", e)
            false
        }
    }
}

/**
 * Kafka event store adapter implementation.
 * Demonstrates event sourcing with Kafka for high-throughput scenarios.
 */
class KafkaEventStoreAdapter(
    private val bootstrapServers: String,
    private val topicPrefix: String = "multiauth"
) : PersistenceAdapter {

    private val logger = Logger.getLogger(this::class)

    override suspend fun saveUser(user: User): Boolean {
        return try {
            logger.debug("kafka", "Publishing user event: ${user.id}")

            // Kafka event: user-created or user-updated
            val userEvent = mapOf(
                "eventType" to "user-saved",
                "userId" to user.id,
                "userData" to mapOf(
                    "email" to user.email,
                    "displayName" to user.displayName,
                    "emailVerified" to user.emailVerified
                ),
                "timestamp" to Clock.System.now().toString()
            )

            // TODO: Publish to Kafka topic
            // producer.send("$topicPrefix-user-events", user.id, userEvent)
            logger.debug("kafka", "User event prepared for topic: ${topicPrefix}-user-events")

            true
        } catch (e: Exception) {
            logger.error("kafka", "Failed to publish user event: ${user.id}", e)
            false
        }
    }

    override suspend fun findUserById(id: String): User? {
        return try {
            logger.debug("kafka", "Rebuilding user state from events: $id")

            // In event sourcing, read all events for user and rebuild state
            // TODO: Consume from Kafka topic and rebuild user state
            logger.debug("kafka", "User state rebuild prepared: $id")

            null
        } catch (e: Exception) {
            logger.error("kafka", "Failed to rebuild user state: $id", e)
            null
        }
    }

    override suspend fun findUserByEmail(email: String): User? {
        return try {
            logger.debug("kafka", "Finding user by email (requires index)")

            // For email lookup, would need a separate index store or snapshot
            logger.debug("kafka", "User email lookup prepared")

            null
        } catch (e: Exception) {
            logger.error("kafka", "Failed to find user by email", e)
            null
        }
    }

    override suspend fun deleteUser(id: String): Boolean {
        return try {
            logger.debug("kafka", "Publishing user deletion event: $id")

            val deleteEvent = mapOf(
                "eventType" to "user-deleted",
                "userId" to id,
                "timestamp" to Clock.System.now().toString()
            )

            // TODO: Publish to Kafka topic
            logger.debug("kafka", "User deletion event prepared: $id")

            true
        } catch (e: Exception) {
            logger.error("kafka", "Failed to publish user deletion: $id", e)
            false
        }
    }

    override suspend fun saveSession(session: Session): Boolean {
        return try {
            logger.debug("kafka", "Publishing session event: ${session.id}")

            val sessionEvent = mapOf(
                "eventType" to "session-created",
                "sessionId" to session.id,
                "userId" to session.userId,
                "expiresAt" to session.expiresAt.toString(),
                "timestamp" to Clock.System.now().toString()
            )

            // TODO: Publish to Kafka topic
            logger.debug("kafka", "Session event prepared: ${session.id}")

            true
        } catch (e: Exception) {
            logger.error("kafka", "Failed to publish session event: ${session.id}", e)
            false
        }
    }

    override suspend fun findSessionById(id: String): Session? {
        return try {
            logger.debug("kafka", "Rebuilding session state: $id")

            // TODO: Rebuild session state from events
            logger.debug("kafka", "Session state rebuild prepared: $id")

            null
        } catch (e: Exception) {
            logger.error("kafka", "Failed to rebuild session state: $id", e)
            null
        }
    }

    override suspend fun findActiveSessions(userId: String): List<Session> {
        return try {
            logger.debug("kafka", "Finding active sessions: $userId")

            // TODO: Query session snapshots or rebuild from events
            logger.debug("kafka", "Active sessions lookup prepared: $userId")

            emptyList()
        } catch (e: Exception) {
            logger.error("kafka", "Failed to find active sessions: $userId", e)
            emptyList()
        }
    }

    override suspend fun expireSession(sessionId: String): Boolean {
        return try {
            logger.debug("kafka", "Publishing session expiration: $sessionId")

            val expirationEvent = mapOf(
                "eventType" to "session-expired",
                "sessionId" to sessionId,
                "timestamp" to Clock.System.now().toString()
            )

            // TODO: Publish to Kafka topic
            logger.debug("kafka", "Session expiration event prepared: $sessionId")

            true
        } catch (e: Exception) {
            logger.error("kafka", "Failed to publish session expiration: $sessionId", e)
            false
        }
    }

    override suspend fun saveAuthEvent(event: AuthEvent, metadata: EventMetadata): String {
        return try {
            val eventId = "evt_${Clock.System.now().toEpochMilliseconds()}"
            logger.debug("kafka", "Publishing auth event: $eventId")

            val authEvent = mapOf(
                "eventId" to eventId,
                "eventType" to event::class.simpleName,
                "source" to metadata.source,
                "timestamp" to metadata.timestamp
            )

            // TODO: Publish to Kafka topic
            logger.debug("kafka", "Auth event prepared: $eventId")

            eventId
        } catch (e: Exception) {
            logger.error("kafka", "Failed to publish auth event", e)
            "error"
        }
    }

    override suspend fun healthCheck(): Boolean {
        return try {
            logger.debug("kafka", "Checking Kafka cluster health")

            // TODO: Check Kafka cluster metadata
            logger.debug("kafka", "Kafka health check prepared")

            true
        } catch (e: Exception) {
            logger.error("kafka", "Kafka health check failed", e)
            false
        }
    }
}

/**
 * Simple factory for persistence adapters.
 */
object PersistenceAdapterFactory {

    private val logger = Logger.getLogger(this::class)

    /**
     * Creates a PostgreSQL adapter.
     */
    fun createPostgreSQL(connectionString: String): PersistenceAdapter {
        logger.info("persistence", "Creating PostgreSQL adapter")
        return PostgreSQLAdapter(connectionString)
    }

    /**
     * Creates a MongoDB adapter.
     */
    fun createMongoDB(connectionString: String, databaseName: String = "multiauth"): PersistenceAdapter {
        logger.info("persistence", "Creating MongoDB adapter")
        return MongoDBAdapter(connectionString, databaseName)
    }

    /**
     * Creates a Firestore adapter.
     */
    fun createFirestore(projectId: String): PersistenceAdapter {
        logger.info("persistence", "Creating Firestore adapter")
        return FirestoreAdapter(projectId)
    }

    /**
     * Creates a Kafka event store adapter.
     */
    fun createKafkaEventStore(bootstrapServers: String, topicPrefix: String = "multiauth"): PersistenceAdapter {
        logger.info("persistence", "Creating Kafka event store adapter")
        return KafkaEventStoreAdapter(bootstrapServers, topicPrefix)
    }
}

/**
 * Example usage and configuration.
 */
object PersistenceExamples {

    /**
     * Example: Setting up PostgreSQL persistence
     */
    suspend fun setupPostgreSQL(): PersistenceAdapter {
        val connectionString = "postgresql://username:password@localhost:5432/multiauth"
        val adapter = PersistenceAdapterFactory.createPostgreSQL(connectionString)

        // Test health
        val isHealthy = adapter.healthCheck()
        println("PostgreSQL adapter healthy: $isHealthy")

        return adapter
    }

    /**
     * Example: Setting up MongoDB persistence
     */
    suspend fun setupMongoDB(): PersistenceAdapter {
        val connectionString = "mongodb://localhost:27017"
        val adapter = PersistenceAdapterFactory.createMongoDB(connectionString, "multiauth")

        // Test health
        val isHealthy = adapter.healthCheck()
        println("MongoDB adapter healthy: $isHealthy")

        return adapter
    }

    /**
     * Example: Setting up Firestore persistence
     */
    suspend fun setupFirestore(): PersistenceAdapter {
        val projectId = "your-gcp-project-id"
        val adapter = PersistenceAdapterFactory.createFirestore(projectId)

        // Test health
        val isHealthy = adapter.healthCheck()
        println("Firestore adapter healthy: $isHealthy")

        return adapter
    }

    /**
     * Example: Setting up Kafka event store
     */
    suspend fun setupKafkaEventStore(): PersistenceAdapter {
        val bootstrapServers = "localhost:9092"
        val adapter = PersistenceAdapterFactory.createKafkaEventStore(bootstrapServers, "multiauth")

        // Test health
        val isHealthy = adapter.healthCheck()
        println("Kafka adapter healthy: $isHealthy")

        return adapter
    }

    /**
     * Example: Using the persistence adapter
     */
    suspend fun usageExample(adapter: PersistenceAdapter) {
        // Save a user
        val user = User(
            id = "user123",
            email = "user@example.com",
            displayName = "Test User",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )

        val userSaved = adapter.saveUser(user)
        println("User saved: $userSaved")

        // Find the user
        val foundUser = adapter.findUserById("user123")
        println("User found: ${foundUser != null}")

        // Save auth event
        val event = Authentication.SignInCompleted(user, TokenPair("token", "refresh", Clock.System.now()))
        val metadata = EventMetadata("example")
        val eventId = adapter.saveAuthEvent(event, metadata)
        println("Event saved: $eventId")
    }
}

# Persistence Adapters Guide

The Multi-Auth system includes a clean, extensible persistence layer that supports multiple database backends through lightweight adapters.

## 🏗️ **Architecture Overview**

### **Clean Abstraction Design**
- **Simple Interface**: `PersistenceAdapter` provides core CRUD operations
- **Lightweight Adapters**: Each adapter is ~100-200 lines, optimized for its backend
- **Easy Integration**: Drop-in replacement between different storage backends
- **Production Ready**: Structured for actual database driver integration

### **Supported Storage Backends**

| Backend | Use Case | Key Features |
|---------|----------|--------------|
| **PostgreSQL** | Enterprise SQL | JSONB support, ACID transactions, advanced queries |
| **MySQL** | Popular SQL | JSON columns, wide compatibility, proven reliability |
| **MongoDB** | Document Store | Flexible schema, natural JSON mapping, horizontal scaling |
| **Firestore** | Cloud NoSQL | Real-time sync, automatic scaling, Google Cloud integration |
| **Kafka Event Store** | Event Sourcing | High throughput, event replay, distributed architecture |

## 🚀 **Quick Start**

### **Basic Setup**

```kotlin
import app.multiauth.persistence.PersistenceAdapterFactory

// Choose your backend
val adapter = PersistenceAdapterFactory.createPostgreSQL(
    "postgresql://username:password@localhost:5432/multiauth"
)

// Test connectivity
val isHealthy = adapter.healthCheck()
println("Database healthy: $isHealthy")
```

### **Using the Adapter**

```kotlin
// Save a user
val user = User(
    id = "user123",
    email = "user@example.com",
    displayName = "John Doe",
    createdAt = Clock.System.now(),
    updatedAt = Clock.System.now()
)

val success = adapter.saveUser(user)
println("User saved: $success")

// Find the user
val foundUser = adapter.findUserById("user123")
println("User found: ${foundUser?.displayName}")

// Save authentication event
val event = Authentication.SignInCompleted(user, tokens)
val metadata = EventMetadata("AuthEngine")
val eventId = adapter.saveAuthEvent(event, metadata)
println("Event saved: $eventId")
```

## 📊 **Backend-Specific Configurations**

### **PostgreSQL Adapter**

**Best for:** Enterprise applications, complex queries, ACID compliance

```kotlin
val adapter = PersistenceAdapterFactory.createPostgreSQL(
    "postgresql://username:password@localhost:5432/multiauth"
)
```

**Database Schema:**
```sql
-- Users table with JSONB for flexible data
CREATE TABLE users (
    id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    display_name VARCHAR(255),
    email_verified BOOLEAN DEFAULT FALSE,
    phone_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    auth_methods JSONB DEFAULT '[]'::jsonb
);

-- Sessions table with device and location info
CREATE TABLE sessions (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) REFERENCES users(id),
    token VARCHAR(500) NOT NULL,
    refresh_token VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    device_info JSONB,
    metadata JSONB DEFAULT '{}'::jsonb
);

-- Auth events for audit and replay
CREATE TABLE auth_events (
    id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    user_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb
);

-- Indexes for performance
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_status ON sessions(status);
CREATE INDEX idx_auth_events_user_id ON auth_events(user_id);
CREATE INDEX idx_auth_events_created_at ON auth_events(created_at);
```

### **MongoDB Adapter**

**Best for:** Rapid development, flexible schema, horizontal scaling

```kotlin
val adapter = PersistenceAdapterFactory.createMongoDB(
    "mongodb://localhost:27017",
    "multiauth"
)
```

**Document Structure:**
```javascript
// Users collection
{
  "_id": "user123",
  "email": "user@example.com",
  "displayName": "John Doe",
  "emailVerified": true,
  "phoneVerified": false,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z",
  "authMethods": ["email", "oauth_google"]
}

// Sessions collection
{
  "_id": "session123",
  "userId": "user123",
  "token": "jwt_token_here",
  "refreshToken": "refresh_token_here",
  "createdAt": "2024-01-15T10:30:00Z",
  "expiresAt": "2024-01-15T11:30:00Z",
  "status": "ACTIVE",
  "deviceInfo": {
    "deviceType": "mobile",
    "platform": "iOS"
  },
  "metadata": {}
}

// Auth events collection
{
  "_id": "evt_1705320600000",
  "eventType": "SignInCompleted",
  "userId": "user123",
  "source": "AuthEngine",
  "timestamp": 1705320600000,
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### **Firestore Adapter**

**Best for:** Real-time applications, Google Cloud ecosystem, automatic scaling

```kotlin
val adapter = PersistenceAdapterFactory.createFirestore(
    "your-gcp-project-id"
)
```

**Collection Structure:**
```
/users/{userId}
  - id: string
  - email: string
  - displayName: string
  - emailVerified: boolean
  - createdAt: timestamp

/sessions/{sessionId}
  - id: string
  - userId: string
  - token: string
  - expiresAt: timestamp
  - status: string

/authEvents/{eventId}
  - id: string
  - eventType: string
  - userId: string
  - timestamp: number
  - source: string
```

**Firestore Security Rules:**
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can only access their own data
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Sessions are protected
    match /sessions/{sessionId} {
      allow read, write: if request.auth != null 
        && resource.data.userId == request.auth.uid;
    }
    
    // Auth events are read-only for users
    match /authEvents/{eventId} {
      allow read: if request.auth != null 
        && resource.data.userId == request.auth.uid;
      allow write: if false; // Only server can write events
    }
  }
}
```

### **Kafka Event Store Adapter**

**Best for:** Event sourcing, high throughput, distributed systems

```kotlin
val adapter = PersistenceAdapterFactory.createKafkaEventStore(
    "localhost:9092",
    "multiauth"
)
```

**Topic Structure:**
```
multiauth-user-events:
  - Partition by user ID
  - Events: user-created, user-updated, user-deleted

multiauth-session-events:
  - Partition by session ID  
  - Events: session-created, session-expired, session-terminated

multiauth-auth-events:
  - Partition by user ID
  - Events: sign-in, sign-out, mfa-completed, etc.
```

**Event Sourcing Pattern:**
```kotlin
// All operations become events
adapter.saveUser(user) // → Publishes "user-created" event
adapter.deleteUser(id) // → Publishes "user-deleted" event

// State is rebuilt from events
val user = adapter.findUserById(id) // → Replays all user events to rebuild state
```

## 🔧 **Integration Examples**

### **Spring Boot Integration**

```kotlin
@Configuration
class PersistenceConfiguration {
    
    @Bean
    @Primary
    fun persistenceAdapter(): PersistenceAdapter {
        return PersistenceAdapterFactory.createPostgreSQL(
            connectionString = "\${app.database.url}"
        )
    }
    
    @Bean
    @ConditionalOnProperty("app.event-sourcing.enabled")
    fun eventStoreAdapter(): PersistenceAdapter {
        return PersistenceAdapterFactory.createKafkaEventStore(
            bootstrapServers = "\${app.kafka.bootstrap-servers}",
            topicPrefix = "\${app.kafka.topic-prefix:multiauth}"
        )
    }
}
```

### **Ktor Integration**

```kotlin
fun Application.configurePersistence() {
    val databaseUrl = environment.config.property("database.url").getString()
    
    val adapter = PersistenceAdapterFactory.createPostgreSQL(databaseUrl)
    
    // Install as singleton
    install(SingletonPlugin) {
        singleton { adapter }
    }
}
```

### **Environment Configuration**

```bash
# PostgreSQL
export MULTIAUTH_DB_TYPE=postgresql
export MULTIAUTH_DB_URL=postgresql://user:pass@localhost:5432/multiauth

# MongoDB  
export MULTIAUTH_DB_TYPE=mongodb
export MULTIAUTH_DB_URL=mongodb://localhost:27017
export MULTIAUTH_DB_NAME=multiauth

# Firestore
export MULTIAUTH_DB_TYPE=firestore
export MULTIAUTH_GCP_PROJECT_ID=your-project-id

# Kafka Event Store
export MULTIAUTH_DB_TYPE=kafka
export MULTIAUTH_KAFKA_BROKERS=localhost:9092
export MULTIAUTH_KAFKA_PREFIX=multiauth
```

## 🔄 **Migration Between Backends**

### **SQL to NoSQL Migration**

```kotlin
suspend fun migrateSQLToMongoDB(
    sqlAdapter: PostgreSQLAdapter,
    mongoAdapter: MongoDBAdapter
) {
    // Migrate users
    val users = sqlAdapter.getAllUsers() // Would need to implement
    users.forEach { user ->
        mongoAdapter.saveUser(user)
    }
    
    // Migrate sessions
    val sessions = sqlAdapter.getAllSessions() // Would need to implement
    sessions.forEach { session ->
        mongoAdapter.saveSession(session)
    }
    
    println("Migration completed successfully")
}
```

### **Traditional to Event Sourcing Migration**

```kotlin
suspend fun migrateToEventSourcing(
    traditionalAdapter: PostgreSQLAdapter,
    eventStoreAdapter: KafkaEventStoreAdapter
) {
    // Replay historical data as events
    val users = traditionalAdapter.getAllUsers()
    users.forEach { user ->
        // Create historical user-created event
        eventStoreAdapter.saveUser(user)
    }
    
    // Switch to event sourcing for new operations
    println("Event sourcing migration completed")
}
```

## 🔍 **Performance Considerations**

### **SQL Adapters (PostgreSQL/MySQL)**
- **Pros**: ACID compliance, complex queries, mature tooling
- **Cons**: Vertical scaling limits, schema rigidity
- **Best for**: Traditional enterprise applications, complex reporting

### **NoSQL Adapters (MongoDB/Firestore)**
- **Pros**: Horizontal scaling, flexible schema, rapid development
- **Cons**: Eventual consistency, limited complex queries
- **Best for**: Modern web applications, mobile backends

### **Event Store Adapter (Kafka)**
- **Pros**: Infinite scaling, event replay, audit trail
- **Cons**: Complexity, eventual consistency, storage overhead
- **Best for**: Microservices, event-driven architectures, compliance-heavy systems

## 🛠️ **Production Deployment**

### **Database Setup Scripts**

**PostgreSQL:**
```sql
-- Create database
CREATE DATABASE multiauth;

-- Create user
CREATE USER multiauth_app WITH PASSWORD 'secure_password';
GRANT ALL PRIVILEGES ON DATABASE multiauth TO multiauth_app;

-- Run schema creation (see above)
```

**MongoDB:**
```javascript
// Create database and user
use multiauth;
db.createUser({
  user: "multiauth_app",
  pwd: "secure_password",
  roles: [
    { role: "readWrite", db: "multiauth" }
  ]
});

// Create indexes
db.users.createIndex({ "email": 1 }, { unique: true });
db.sessions.createIndex({ "userId": 1 });
db.sessions.createIndex({ "expiresAt": 1 });
db.authEvents.createIndex({ "userId": 1, "createdAt": -1 });
```

### **Docker Compose Example**

```yaml
version: '3.8'
services:
  # PostgreSQL
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: multiauth
      POSTGRES_USER: multiauth_app
      POSTGRES_PASSWORD: secure_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./scripts/postgres-init.sql:/docker-entrypoint-initdb.d/init.sql

  # MongoDB
  mongodb:
    image: mongo:7
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: admin_password
      MONGO_INITDB_DATABASE: multiauth
    ports:
      - "27017:27017"
    volumes:
      - mongodb_data:/data/db
      - ./scripts/mongo-init.js:/docker-entrypoint-initdb.d/init.js

  # Kafka
  kafka:
    image: confluentinc/cp-kafka:7.4.0
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    ports:
      - "9092:9092"
    depends_on:
      - zookeeper

  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

volumes:
  postgres_data:
  mongodb_data:
```

### **Kubernetes Deployment**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: multiauth-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: multiauth
  template:
    metadata:
      labels:
        app: multiauth
    spec:
      containers:
      - name: multiauth
        image: multiauth:latest
        env:
        - name: MULTIAUTH_DB_TYPE
          value: "postgresql"
        - name: MULTIAUTH_DB_URL
          valueFrom:
            secretKeyRef:
              name: multiauth-secrets
              key: database-url
        ports:
        - containerPort: 8080
---
apiVersion: v1
kind: Secret
metadata:
  name: multiauth-secrets
type: Opaque
stringData:
  database-url: "postgresql://user:pass@postgres-service:5432/multiauth"
```

## 🔧 **Advanced Usage**

### **Multiple Adapters (Polyglot Persistence)**

```kotlin
class MultiPersistenceService {
    private val primaryAdapter = PersistenceAdapterFactory.createPostgreSQL(primaryDbUrl)
    private val eventStoreAdapter = PersistenceAdapterFactory.createKafkaEventStore(kafkaBrokers)
    private val cacheAdapter = PersistenceAdapterFactory.createMongoDB(mongoUrl) // Fast reads
    
    suspend fun saveUser(user: User): Boolean {
        // Save to primary database
        val primaryResult = primaryAdapter.saveUser(user)
        
        // Publish event for audit/replay
        if (primaryResult) {
            val event = Authentication.SignUpCompleted(user, tokens)
            eventStoreAdapter.saveAuthEvent(event, EventMetadata("MultiPersistenceService"))
        }
        
        // Update cache for fast reads
        if (primaryResult) {
            cacheAdapter.saveUser(user)
        }
        
        return primaryResult
    }
    
    suspend fun findUser(id: String): User? {
        // Try cache first
        return cacheAdapter.findUserById(id) ?: run {
            // Fallback to primary database
            val user = primaryAdapter.findUserById(id)
            
            // Update cache if found
            if (user != null) {
                cacheAdapter.saveUser(user)
            }
            
            user
        }
    }
}
```

### **Event Sourcing with Snapshots**

```kotlin
class EventSourcingService {
    private val eventStore = PersistenceAdapterFactory.createKafkaEventStore(kafkaBrokers)
    private val snapshotStore = PersistenceAdapterFactory.createMongoDB(mongoUrl)
    
    suspend fun saveUserEvent(event: AuthEvent, metadata: EventMetadata) {
        // Save event to Kafka
        eventStore.saveAuthEvent(event, metadata)
        
        // Update snapshot every 10 events
        if (shouldCreateSnapshot(event)) {
            val user = rebuildUserFromEvents(extractUserId(event))
            if (user != null) {
                snapshotStore.saveUser(user)
            }
        }
    }
    
    suspend fun getUser(id: String): User? {
        // Get latest snapshot
        val snapshot = snapshotStore.findUserById(id)
        
        // Apply events since snapshot
        return if (snapshot != null) {
            applyEventsSinceSnapshot(snapshot, id)
        } else {
            rebuildUserFromEvents(id)
        }
    }
}
```

## 📈 **Monitoring and Observability**

### **Health Monitoring**

```kotlin
class PersistenceHealthMonitor {
    private val adapters = listOf(
        "primary" to primaryAdapter,
        "cache" to cacheAdapter,
        "events" to eventStoreAdapter
    )
    
    suspend fun checkAllHealth(): Map<String, Boolean> {
        return adapters.associate { (name, adapter) ->
            name to try {
                adapter.healthCheck()
            } catch (e: Exception) {
                false
            }
        }
    }
    
    suspend fun getHealthReport(): HealthReport {
        val checks = checkAllHealth()
        
        return HealthReport(
            overallHealthy = checks.values.all { it },
            individualChecks = checks,
            timestamp = Clock.System.now()
        )
    }
}
```

### **Performance Metrics**

```kotlin
class PersistenceMetrics {
    private val operationTimes = mutableMapOf<String, List<Long>>()
    
    suspend fun <T> measureOperation(operation: String, block: suspend () -> T): T {
        val startTime = Clock.System.now()
        
        return try {
            val result = block()
            recordSuccess(operation, startTime)
            result
        } catch (e: Exception) {
            recordFailure(operation, startTime, e)
            throw e
        }
    }
    
    fun getAverageLatency(operation: String): Double? {
        return operationTimes[operation]?.average()
    }
    
    fun getMetricsReport(): MetricsReport {
        return MetricsReport(
            operationLatencies = operationTimes.mapValues { it.value.average() },
            totalOperations = operationTimes.values.sumOf { it.size },
            timestamp = Clock.System.now()
        )
    }
}
```

## 🔒 **Security Considerations**

### **Connection Security**
- **Always use SSL/TLS** for database connections in production
- **Rotate credentials regularly** and store in secure vaults
- **Use connection pooling** to prevent connection exhaustion
- **Implement proper timeouts** to prevent hanging operations

### **Data Security**
- **Encrypt sensitive fields** before storing (tokens, PII)
- **Use database-level encryption** at rest when available
- **Implement proper access controls** and user permissions
- **Regular security audits** of database configurations

### **Example Secure Configuration**

```kotlin
class SecurePersistenceConfig {
    companion object {
        fun createSecurePostgreSQL(): PersistenceAdapter {
            val connectionString = buildString {
                append("postgresql://")
                append("\${DB_USER}:\${DB_PASSWORD}")
                append("@\${DB_HOST}:\${DB_PORT}")
                append("/\${DB_NAME}")
                append("?sslmode=require")
                append("&sslcert=client-cert.pem")
                append("&sslkey=client-key.pem")
                append("&sslrootcert=ca-cert.pem")
            }
            
            return PersistenceAdapterFactory.createPostgreSQL(connectionString)
        }
    }
}
```

## 🚀 **Next Steps**

### **Immediate Implementation**
1. **Choose your backend** based on your use case and infrastructure
2. **Set up the database** using the provided schema/structure
3. **Configure the adapter** with your connection details
4. **Integrate with your auth engine** by replacing in-memory storage

### **Production Enhancements**
1. **Add actual database drivers** (kotlinx-coroutines-jdbc, MongoDB driver, etc.)
2. **Implement connection pooling** for better performance
3. **Add retry logic** for transient failures
4. **Set up monitoring** and alerting for database health

### **Advanced Features**
1. **Read replicas** for improved read performance
2. **Sharding strategies** for horizontal scaling
3. **Backup and restore** procedures
4. **Cross-region replication** for disaster recovery

---

**The persistence layer provides a solid foundation for production database integration while maintaining the clean architecture and zero-error build status of the Multi-Auth system!** 🎯

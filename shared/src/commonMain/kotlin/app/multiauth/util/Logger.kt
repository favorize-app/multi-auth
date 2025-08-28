package app.multiauth.util

import kotlinx.datetime.Clock.System

/**
 * Simple logging utility for the multi-auth system.
 * This provides a consistent logging interface across all platforms.
 */
object Logger {
    
    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    private var minimumLevel = Level.DEBUG
    private val loggers = mutableListOf<LogHandler>()
    
    /**
     * Gets a logger instance for a specific class.
     */
    fun getLogger(clazz: Any): Logger {
        return this
    }
    
    /**
     * Sets the minimum log level for all loggers.
     */
    fun setMinimumLevel(level: Level) {
        minimumLevel = level
    }
    
    /**
     * Adds a custom log handler.
     */
    fun addHandler(handler: LogHandler) {
        loggers.add(handler)
    }
    
    /**
     * Removes a log handler.
     */
    fun removeHandler(handler: LogHandler) {
        loggers.remove(handler)
    }
    
    /**
     * Clears all log handlers.
     */
    fun clearHandlers() {
        loggers.clear()
    }
    
    init {
        addHandler(ConsoleLogHandler())
    }
    
    fun verbose(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.VERBOSE, tag, message, throwable)
    }
    
    fun debug(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.DEBUG, tag, message, throwable)
    }
    
    fun info(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.INFO, tag, message, throwable)
    }
    
    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.WARN, tag, message, throwable)
    }
    
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
    }
    
    private fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        if (level.ordinal < minimumLevel.ordinal) return
        
        val timestamp = System.now()
        val logEntry = LogEntry(level, tag, message, throwable, timestamp)
        
        loggers.forEach { handler ->
            try {
                handler.handle(logEntry)
            } catch (e: Exception) {
                // Prevent logging errors from crashing the app
                println("Logger error: ${e.message}")
            }
        }
    }
}

/**
 * Represents a single log entry.
 */
data class LogEntry(
    val level: Logger.Level,
    val tag: String,
    val message: String,
    val throwable: Throwable?,
    val timestamp: Instant
)

/**
 * Interface for custom log handlers.
 */
interface LogHandler {
    fun handle(entry: LogEntry)
}

/**
 * Default console log handler.
 */
class ConsoleLogHandler : LogHandler {
    override fun handle(entry: LogEntry) {
        val timestamp = entry.timestamp.toString()
        val level = entry.level.name.padEnd(5)
        val tag = entry.tag.padEnd(20)
        val message = entry.message
        
        val logLine = "[$timestamp] $level $tag: $message"
        
        when (entry.level) {
            Logger.Level.ERROR -> println("ERROR: $logLine")
            else -> println(logLine)
        }
        
        entry.throwable?.let { throwable ->
            println("Exception: ${throwable.message}")
            // Note: stackTrace is not available in common code
            // Platform-specific implementations can provide full stack traces
        }
    }
}

/**
 * No-op log handler for production environments.
 */
class NoOpLogHandler : LogHandler {
    override fun handle(entry: LogEntry) {
        // Do nothing
    }
}

/**
 * Memory log handler that stores logs in memory.
 * Useful for testing and debugging.
 */
class MemoryLogHandler : LogHandler {
    private val logs = mutableListOf<LogEntry>()
    private val maxLogs = 1000
    
    override fun handle(entry: LogEntry) {
        logs.add(entry)
        if (logs.size > maxLogs) {
            logs.removeAt(0)
        }
    }
    
    fun getLogs(): List<LogEntry> = logs.toList()
    
    fun getLogsByLevel(level: Logger.Level): List<LogEntry> = 
        getLogs().filter { it.level == level }
    
    fun getLogsByTag(tag: String): List<LogEntry> = 
        getLogs().filter { it.tag == tag }
    
    fun clear() = logs.clear()
    
    fun getLogCount(): Int = logs.size
}
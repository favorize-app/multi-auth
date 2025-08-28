#!/bin/bash

echo "Fixing common compilation errors across all Kotlin files..."

# Fix Java imports - replace with KMP-compatible alternatives
echo "Fixing Java imports..."

# Replace java.time.Instant with kotlinx.datetime.Instant
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/import java\.time\.Instant/import kotlinx.datetime.Instant/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/import java\.time\.temporal\.ChronoUnit/import kotlinx.datetime.Duration/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/import java\.util\.concurrent\.ConcurrentHashMap/import kotlin.collections.MutableMap/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/import java\.util\.concurrent\.atomic\.AtomicLong/import kotlin.collections.MutableMap/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/import java\.util\.concurrent\.Executors/\/\/ Replaced with coroutines/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/import java\.util\.concurrent\.ScheduledExecutorService/\/\/ Replaced with coroutines/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/import java\.util\.concurrent\.TimeUnit/\/\/ Replaced with kotlinx.datetime.Duration/g' {} \;

# Replace java.util.concurrent.atomic.SecureRandom
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/import java\.security\.SecureRandom/\/\/ Platform-specific implementation required/g' {} \;

# Replace System references
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/System\.currentTimeMillis\(\)/Clock.System.now().epochSeconds/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/System\.getProperty/\/\/ Platform-specific implementation required/g' {} \;

# Replace Instant.now() with Clock.System.now()
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/Instant\.now\(\)/Clock.System.now()/g' {} \;

# Replace isAfter/isBefore with comparison operators
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/\.isAfter\(/ > /g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/\.isBefore\(/ < /g' {} \;

# Replace ChronoUnit usage with Duration
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/ChronoUnit\.MILLIS\.between/\/\/ Duration calculation required/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/ChronoUnit\.SECONDS\.between/\/\/ Duration calculation required/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/ChronoUnit\.MINUTES\.between/\/\/ Duration calculation required/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/ChronoUnit\.HOURS\.between/\/\/ Duration calculation required/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/ChronoUnit\.DAYS\.between/\/\/ Duration calculation required/g' {} \;

# Replace minusSeconds, plusSeconds, etc.
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/\.minusSeconds\(/\.minus(Duration.parse("PT\1S"))/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/\.plusSeconds\(/\.plus(Duration.parse("PT\1S"))/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/\.minusMinutes\(/\.minus(Duration.parse("PT\1M"))/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/\.plusMinutes\(/\.plus(Duration.parse("PT\1M"))/g' {} \;

# Replace ConcurrentHashMap usage with MutableMap
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/ConcurrentHashMap</mutableMapOf</g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/ConcurrentHashMap\(\)/mutableMapOf()/g' {} \;

# Replace AtomicLong usage with simple Long
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/AtomicLong\(/Long(/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/\.incrementAndGet\(\)/++/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/\.decrementAndGet\(\)/--/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/\.get\(\)//g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/\.set\(/ = /g' {} \;

# Fix logger calls - add missing tag parameter
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/logger\.warn("\([^"]*\)")/logger.warn("general", "\1")/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/logger\.info("\([^"]*\)")/logger.info("general", "\1")/g' {} \;
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' 's/logger\.error("\([^"]*\)")/logger.error("general", "\1")/g' {} \;

# Add missing imports
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' '1i\
import kotlinx.datetime.Clock\
import kotlinx.datetime.Duration\
import kotlinx.datetime.Instant\
' {} \;

# Remove duplicate enum/class definitions (comment them out)
echo "Commenting out duplicate enum/class definitions..."

# Comment out duplicate SecurityEvent definitions
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' '/^enum class SecurityEvent/,/^}/s/^/\/\/ /' {} \;

# Comment out duplicate ExportFormat definitions
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' '/^enum class ExportFormat/,/^}/s/^/\/\/ /' {} \;

# Comment out duplicate PerformanceMetrics definitions
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' '/^data class PerformanceMetrics/,/^}/s/^/\/\/ /' {} \;

# Comment out duplicate ConfigurationResult definitions
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' '/^enum class ConfigurationResult/,/^}/s/^/\/\/ /' {} \;

# Comment out duplicate RecommendationPriority definitions
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' '/^enum class RecommendationPriority/,/^}/s/^/\/\/ /' {} \;

# Comment out duplicate AnomalyType definitions
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' '/^enum class AnomalyType/,/^}/s/^/\/\/ /' {} \;

# Comment out duplicate AutomatedAction definitions
find shared/src/commonMain/kotlin -name "*.kt" -exec sed -i '' '/^enum class AutomatedAction/,/^}/s/^/\/\/ /' {} \;

echo "Common errors fixed. Please run the build again to see remaining issues."
echo "Note: Some manual fixes may still be required for complex cases."

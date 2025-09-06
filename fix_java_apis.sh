#!/bin/bash

echo "Replacing Java-specific APIs with Kotlin alternatives..."

# Replace TimeUnit with kotlin.time.DurationUnit
find . -name "*.kt" -type f -exec sed -i '' 's/import java\.util\.concurrent\.TimeUnit/import kotlin.time.DurationUnit/g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's/TimeUnit\.SECONDS/DurationUnit.SECONDS/g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's/TimeUnit\.MINUTES/DurationUnit.MINUTES/g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's/TimeUnit\.HOURS/DurationUnit.HOURS/g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's/TimeUnit\.MILLISECONDS/DurationUnit.MILLISECONDS/g' {} \;

# Replace ScheduledExecutorService and Executors with simple coroutine-based alternatives
find . -name "*.kt" -type f -exec sed -i '' 's/import java\.util\.concurrent\.ScheduledExecutorService//g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's/import java\.util\.concurrent\.Executors//g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's/ScheduledExecutorService/CoroutineScope/g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's/Executors\.newScheduledThreadPool/CoroutineScope/g' {} \;

# Replace AtomicLong with simple Long
find . -name "*.kt" -type f -exec sed -i '' 's/import java\.util\.concurrent\.atomic\.AtomicLong//g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's/AtomicLong/Long/g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's/\.incrementAndGet()/++/g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's/\.getAndIncrement()/++/g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's/\.get()//g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's/\.set(/=/g' {} \;

# Replace SecureRandom with kotlin.random.Random
find . -name "*.kt" -type f -exec sed -i '' 's/import java\.security\.SecureRandom/import kotlin.random.Random/g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's/SecureRandom/Random/g' {} \;
find . -name "*.kt" -type f -exec sed -i '' 's/\.nextBytes(/\.nextBytes(/g' {} \;

# Replace javaClass with ::class
find . -name "*.kt" -type f -exec sed -i '' 's/\.javaClass/::class/g' {} \;

echo "Java APIs replaced with Kotlin alternatives!"


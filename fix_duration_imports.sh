#!/bin/bash

echo "Adding missing Duration imports..."

# Add Duration import to files that use Duration but don't have the import
find . -name "*.kt" -type f -exec grep -l "Duration" {} \; | while read file; do
    if ! grep -q "import kotlinx.datetime.Duration" "$file"; then
        # Add Duration import after the first import line
        sed -i '' '1,/^import/s/^import kotlinx.datetime.Clock/import kotlinx.datetime.Clock\
import kotlinx.datetime.Duration/' "$file"
    fi
done

echo "Duration imports added!"

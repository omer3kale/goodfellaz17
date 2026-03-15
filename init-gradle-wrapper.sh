#!/bin/bash
# Initialize Gradle wrapper if not already present
# Run once: bash ./init-gradle-wrapper.sh

set -e

echo "🔧 Initializing Gradle 8.5 wrapper..."

# Download gradle-wrapper.jar if it doesn't exist
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "📥 Downloading Gradle wrapper JAR..."
    mkdir -p gradle/wrapper
    
    # Use curl to download the wrapper JAR directly
    curl -L -o gradle/wrapper/gradle-wrapper.jar \
        https://repo.gradle.org/gradle/dist/gradle-8.5-wrapper.jar
    
    if [ $? -eq 0 ]; then
        echo "✅ Gradle wrapper JAR downloaded"
    else
        echo "❌ Failed to download Gradle wrapper JAR"
        echo "   Try: brew install gradle"
        echo "   Then: gradle wrapper --gradle-version 8.5"
        exit 1
    fi
fi

# Create gradlew if it doesn't exist
if [ ! -f "gradlew" ]; then
    echo "📝 Creating gradlew script..."
    cat > gradlew << 'GRADLEW_SCRIPT'
#!/bin/sh
app_path=$(dirname "$0")
cd "$app_path"
exec java -classpath "gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
GRADLEW_SCRIPT
    chmod +x gradlew
fi

# Create gradlew.bat for Windows
if [ ! -f "gradlew.bat" ]; then
    echo "📝 Creating gradlew.bat script..."
    cat > gradlew.bat << 'GRADLEW_BAT'
@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
java -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
GRADLEW_BAT
fi

echo ""
echo "✅ Gradle wrapper initialized!"
echo ""
echo "Next: ./gradlew clean build"
echo ""

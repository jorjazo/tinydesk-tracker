#!/bin/bash

# Start script for TinyDesk Tracker Spring Boot application

set -e

echo "======================================"
echo "  TinyDesk Tracker - Spring Boot"
echo "======================================"
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed"
    echo "Please install Java 17 or higher"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Error: Java 17 or higher is required"
    echo "Current version: $(java -version 2>&1 | head -n 1)"
    exit 1
fi

echo "✓ Java version: $(java -version 2>&1 | head -n 1)"

# Check if .env file exists
if [ ! -f .env ]; then
    echo "⚠ Warning: .env file not found"
    echo "Creating from .env.example..."
    cp .env.example .env
    echo "Please edit .env and add your YOUTUBE_API_KEY"
    echo "Then run this script again"
    exit 1
fi

# Load environment variables
export $(cat .env | grep -v '^#' | xargs)

# Check if API key is set
if [ -z "$YOUTUBE_API_KEY" ] || [ "$YOUTUBE_API_KEY" = "your_youtube_api_key_here" ]; then
    echo "❌ Error: YOUTUBE_API_KEY not set in .env file"
    echo "Please edit .env and add your YouTube API key"
    exit 1
fi

echo "✓ Configuration loaded"

# Create data directory
mkdir -p data

# Check if JAR exists
if [ ! -f build/libs/tinydesk-tracker-*.jar ]; then
    echo "Building application..."
    ./gradlew bootJar
fi

echo ""
echo "Starting TinyDesk Tracker..."
echo ""

# Run the application
java -jar build/libs/tinydesk-tracker-*.jar

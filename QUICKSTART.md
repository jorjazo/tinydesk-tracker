# Quick Start Guide - Spring Boot Edition

Get your TinyDesk Tracker up and running in minutes!

## Prerequisites

- Java 17 or higher
- YouTube Data API key
- Docker (optional)

## 🚀 5-Minute Setup

### Step 1: Get YouTube API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project (or select existing)
3. Enable **YouTube Data API v3**
4. Create credentials → **API Key**
5. Copy your API key

### Step 2: Configure

```bash
# Copy example configuration
cp .env.example .env

# Edit and add your API key
nano .env
```

Set your API key in `.env`:
```bash
YOUTUBE_API_KEY=your_actual_api_key_here
```

### Step 3: Run!

#### Option A: Docker (Easiest)
```bash
docker-compose up -d
```

#### Option B: Gradle
```bash
./gradlew bootRun
```

#### Option C: Compiled JAR
```bash
./gradlew bootJar
java -jar build/libs/tinydesk-tracker-2.0.0.jar
```

#### Option D: Start Script
```bash
./start.sh
```

### Step 4: Access

Open your browser:
- **Main Page:** http://localhost:5000
- **Dashboard:** http://localhost:5000/dashboard
- **History:** http://localhost:5000/history

## 📊 What Happens on First Run

1. Connects to PostgreSQL database (Docker Compose starts it automatically)
2. Creates database schema automatically
3. Performs initial fetch of all Tiny Desk videos
4. Starts web server on port 5000
5. Schedules automatic updates (every 30 minutes by default)

Initial fetch takes ~2-3 minutes for ~1000 videos.

## 🎯 Common Tasks

### Check Status
```bash
curl http://localhost:5000/api/status
```

### Trigger Manual Update
```bash
curl -X POST http://localhost:5000/api/update
```

### View Logs

**Docker:**
```bash
docker-compose logs -f
```

**Gradle:**
Logs appear in console

**JAR:**
Logs appear in console

### Stop Application

**Docker:**
```bash
docker-compose down
```

**Gradle/JAR:**
Press `Ctrl+C`

## ⚙️ Configuration

### Change Update Frequency

Edit `.env`:
```bash
# Update every 2 hours
UPDATE_CRON=0 0 */2 * * *

# Update daily at 3 AM
UPDATE_CRON=0 0 3 * * *

# Update every 15 minutes
UPDATE_CRON=0 */15 * * * *
```

Cron format: `seconds minutes hours day month weekday`

### Change Port

Edit `.env` or `application.yml`:
```bash
SERVER_PORT=8080
```

### Configure PostgreSQL Connection

The application uses PostgreSQL by default. To customize connection settings, edit `.env`:
```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/tinydesk
DATABASE_DRIVER=org.postgresql.Driver
DATABASE_USERNAME=tinydesk
DATABASE_PASSWORD=yourpassword
HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
```

## 🐛 Troubleshooting

### "YOUTUBE_API_KEY not set"
- Check `.env` file exists
- Verify API key is set correctly
- No quotes around the key

### "Port 5000 already in use"
```bash
# Find what's using the port
lsof -i :5000

# Change port in .env
SERVER_PORT=5001
```

### "Java not found"
```bash
# Check Java version
java -version

# Should be 17 or higher
# Install if needed:
# Ubuntu: sudo apt install openjdk-17-jdk
# macOS: brew install openjdk@17
```

### Database Errors
```bash
# Check permissions
ls -la data/

# Reset database (WARNING: deletes all data)
rm -rf data/
# Restart application
```

### API Quota Exceeded
- Default schedule uses ~288 units/day
- Free tier is 10,000 units/day
- Check usage in Google Cloud Console
- Reduce update frequency if needed

## 📱 Access from Other Devices

Find your computer's IP:
```bash
# Linux/Mac
hostname -I

# Then access from any device on your network:
# http://YOUR_IP:5000
```

## 🔄 Updates

Pull latest changes:
```bash
git pull
./gradlew clean bootJar
./start.sh
```

Docker:
```bash
docker-compose down
docker-compose build
docker-compose up -d
```

## 📖 Next Steps

- [Full README](README.md) - Complete documentation
- [Migration Guide](MIGRATION_GUIDE.md) - From Python version
- [Docker Guide](DOCKER.md) - Advanced Docker setup

## 💡 Tips

1. **First run is slow** - Fetching 1000+ videos takes time
2. **Subsequent runs are fast** - Only fetches new data
3. **Database grows slowly** - ~1MB per month with default settings
4. **Check logs** - Application prints helpful status messages
5. **API quota is generous** - Default schedule well within free tier

## 🎉 You're Done!

Enjoy tracking your favorite Tiny Desk concerts! 🎵

## Support

- Check logs for error messages
- Verify API key is valid
- Ensure port 5000 is available
- Check Java version is 17+

For issues, check the main [README](README.md) or create an issue on GitHub.

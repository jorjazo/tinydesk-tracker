# YouTube Tiny Desk Concert Tracker - Spring Boot Edition

A modern Spring Boot application for tracking NPR Tiny Desk concert view counts over time, with historical analytics and a beautiful web interface.

## Features

- 🎵 Tracks top 100 Tiny Desk concerts by view count
- 📊 Stores historical view count data with timestamps
- 🌐 Beautiful web interface with Thymeleaf templates
- ⏰ Automatic updates via Spring Scheduler (configurable cron)
- 📈 Real-time analytics and trending metrics
- 💾 SQLite or PostgreSQL database support
- 🔒 Distributed locking for multiple instances
- 🐳 Docker and Docker Compose ready
- ⚙️ Easy configuration via environment variables or application.yml

## Technology Stack

- **Java 17**
- **Spring Boot 3.2**
- **Spring MVC** for REST APIs
- **Thymeleaf** for server-side templating
- **Spring Data JPA** with Hibernate
- **SQLite** (default) or **PostgreSQL**
- **Gradle** for build management

## Requirements

- Java 17 or higher
- YouTube Data API v3 key (free from Google)
- Docker (optional)

## Quick Start

### Option A: Docker (Recommended)

1. **Configure environment:**
```bash
cp .env.example .env
nano .env  # Add your YouTube API key
```

2. **Run with Docker Compose:**
```bash
docker-compose up -d
```

3. **Access:**
- Web Interface: http://localhost:5000
- Dashboard: http://localhost:5000/dashboard
- History: http://localhost:5000/history

### Option B: Run Locally with Gradle

1. **Get YouTube API Key:**
   - Visit https://console.cloud.google.com/
   - Create a new project
   - Enable "YouTube Data API v3"
   - Create credentials → API Key

2. **Configure:**
```bash
cp .env.example .env
nano .env  # Add your YouTube API key
```

3. **Run:**
```bash
./gradlew bootRun
```

Or build and run the JAR:
```bash
./gradlew bootJar
java -jar build/libs/tinydesk-tracker-2.0.0.jar
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `YOUTUBE_API_KEY` | Required | Your YouTube Data API v3 key |
| `NPR_MUSIC_CHANNEL_ID` | `UC4eYXhJI4-7wSWc8UNRwD4A` | NPR Music channel ID |
| `TINY_DESK_PLAYLIST_ID` | `PL1B627337ED6F55F0` | Tiny Desk playlist ID |
| `DATABASE_URL` | `jdbc:sqlite:./data/tinydesk.db` | Database connection URL |
| `UPDATE_CRON` | `0 */30 * * * *` | Cron expression (every 30 min) |
| `UPDATE_INTERVAL_HOURS` | `6` | Fallback update interval |
| `SCHEDULER_ENABLED` | `true` | Enable/disable scheduled updates |
| `MAX_RESULTS_PER_REQUEST` | `50` | YouTube API page size |

### Using PostgreSQL

Update your `.env` file:
```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/tinydesk
DATABASE_DRIVER=org.postgresql.Driver
DATABASE_USERNAME=tinydesk
DATABASE_PASSWORD=yourpassword
HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
```

## API Endpoints

### Main Endpoints
- `GET /` - Main page with top 100 videos
- `GET /dashboard` - Analytics dashboard
- `GET /history` - Ranking history page

### REST API
- `GET /api/top` - Get top 100 videos with metadata
- `GET /api/data` - Get raw historical data
- `GET /api/status` - System status and database stats
- `GET /api/history/{videoId}` - View history for specific video
- `GET /api/ranking-history` - Get ranking evolution over time
- `GET /api/analytics` - Get trending and performance analytics
- `POST /api/update` - Trigger manual update
- `POST /api/add-video/{videoId}` - Add specific video to tracker

### Example Response

```json
{
  "videos": [
    {
      "rank": 1,
      "videoId": "vWwgrjjIMXA",
      "title": "Tyler, The Creator: NPR Music Tiny Desk Concert",
      "views": 12500000,
      "publishedAt": "2017-12-27T19:00:01Z",
      "url": "https://www.youtube.com/watch?v=vWwgrjjIMXA"
    }
  ],
  "lastUpdate": 1698768000,
  "nextUpdate": 1698789600,
  "total": 100
}
```

## Building

### Build JAR
```bash
./gradlew bootJar
```

### Build Docker Image
```bash
docker build -t tinydesk-tracker:latest .
```

### Build for ARM64 (Raspberry Pi)
```bash
docker buildx build --platform linux/arm64 -t tinydesk-tracker:arm64 .
```

## Development

### Run Tests
```bash
./gradlew test
```

### Run with Hot Reload
```bash
./gradlew bootRun
```

### Code Structure
```
src/main/java/com/tinydesk/tracker/
├── TinyDeskTrackerApplication.java   # Main application class
├── config/                            # Configuration classes
│   ├── AppConfig.java
│   └── WebConfig.java
├── controller/                        # MVC Controllers
│   ├── WebController.java            # Page routes
│   └── ApiController.java            # REST API
├── entity/                            # JPA Entities
│   ├── Video.java
│   ├── History.java
│   ├── Metadata.java
│   └── Lock.java
├── repository/                        # Spring Data repositories
│   ├── VideoRepository.java
│   ├── HistoryRepository.java
│   ├── MetadataRepository.java
│   └── LockRepository.java
├── service/                           # Business logic
│   ├── YouTubeService.java
│   ├── DatabaseService.java
│   ├── TinyDeskTrackerService.java
│   └── SchedulerService.java
└── dto/                               # Data Transfer Objects
    ├── YouTubePlaylistResponse.java
    └── YouTubeVideoResponse.java

src/main/resources/
├── application.yml                    # Application configuration
└── templates/                         # Thymeleaf templates
    ├── index.html
    ├── dashboard.html
    └── history.html
```

## Database Schema

### Tables

**videos** - Main video information
```sql
CREATE TABLE videos (
    video_id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    current_views INTEGER NOT NULL DEFAULT 0,
    last_updated INTEGER NOT NULL,
    published_at TEXT
);
```

**history** - Historical view count data
```sql
CREATE TABLE history (
    id BIGSERIAL PRIMARY KEY,
    video_id TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    view_count INTEGER NOT NULL,
    FOREIGN KEY (video_id) REFERENCES videos(video_id)
);
CREATE INDEX idx_history_video_id ON history(video_id);
CREATE INDEX idx_history_timestamp ON history(timestamp);
```

**metadata** - System metadata
```sql
CREATE TABLE metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
```

**locks** - Distributed locking
```sql
CREATE TABLE locks (
    key TEXT PRIMARY KEY,
    owner TEXT NOT NULL,
    expires_at INTEGER NOT NULL
);
```

## Deployment

### Systemd Service

Create `/etc/systemd/system/tinydesk-tracker.service`:

```ini
[Unit]
Description=YouTube Tiny Desk Concert Tracker
After=network.target

[Service]
Type=simple
User=tinydesk
WorkingDirectory=/opt/tinydesk-tracker
Environment="YOUTUBE_API_KEY=your_key_here"
ExecStart=/usr/bin/java -jar /opt/tinydesk-tracker/tinydesk-tracker-2.0.0.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl daemon-reload
sudo systemctl enable tinydesk-tracker
sudo systemctl start tinydesk-tracker
```

### Docker Compose Production

```yaml
version: '3.8'
services:
  tinydesk-tracker:
    image: tinydesk-tracker:latest
    restart: always
    ports:
      - "5000:5000"
    environment:
      YOUTUBE_API_KEY: ${YOUTUBE_API_KEY}
      DATABASE_URL: jdbc:postgresql://postgres:5432/tinydesk
      DATABASE_USERNAME: tinydesk
      DATABASE_PASSWORD: ${DB_PASSWORD}
    volumes:
      - ./data:/app/data
    depends_on:
      - postgres

  postgres:
    image: postgres:16-alpine
    restart: always
    environment:
      POSTGRES_DB: tinydesk
      POSTGRES_USER: tinydesk
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

## Performance

- **Automatic history cleanup**: Keeps last 100 entries per video
- **Connection pooling**: Managed by Spring Boot
- **Distributed locking**: Prevents duplicate updates in multi-instance deployments
- **Efficient queries**: Indexed on video_id and timestamp
- **Lazy loading**: JPA relationships loaded on demand

## Monitoring

### Health Check
```bash
curl http://localhost:5000/api/status
```

### View Logs
```bash
# Docker
docker-compose logs -f

# Systemd
sudo journalctl -u tinydesk-tracker -f
```

## Troubleshooting

### API Key Issues
- Verify key in `.env` or environment variables
- Check YouTube Data API is enabled in Google Cloud Console
- Check API quota usage

### Database Issues
```bash
# Check database file permissions
ls -la data/tinydesk.db

# For PostgreSQL, check connection
psql -h localhost -U tinydesk -d tinydesk
```

### Port Already in Use
```bash
# Find process using port 5000
lsof -i :5000
# or
netstat -tlnp | grep 5000
```

## Migration from Python Version

The Spring Boot version maintains backward compatibility with the Python version's database schema and API endpoints. You can:

1. Copy your existing `data/tinydesk.db` SQLite file
2. Update environment variables to match the new format
3. Run the Spring Boot version - it will use the existing data

## Comparison: Spring Boot vs Python/Flask

| Feature | Spring Boot | Python/Flask |
|---------|-------------|--------------|
| Language | Java 17 | Python 3.8+ |
| Framework | Spring Boot 3.2 | Flask 3.0 |
| Template Engine | Thymeleaf | Jinja2 |
| ORM | Spring Data JPA | SQLAlchemy |
| Configuration | YAML/Properties | .env file |
| Scheduling | @Scheduled | schedule library |
| Memory | ~150MB | ~50MB |
| Startup | ~5 seconds | ~1 second |
| Performance | High (JVM) | Medium (interpreted) |
| Type Safety | Strong | Dynamic |

## License

Same as original Python version

## Acknowledgments

- NPR Music for the Tiny Desk Concert series
- YouTube Data API v3
- Spring Boot and Java ecosystem

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## API Quota

- Free tier: 10,000 quota units/day
- This tracker uses ~6 units per update
- Default cron (every 30 min) = ~288 units/day
- Well within free quota limits

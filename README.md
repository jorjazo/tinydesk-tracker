# YouTube Tiny Desk Concert Tracker - Raspberry Pi Edition

Python version of the ESP32 Tiny Desk tracker, designed for Raspberry Pi 5 with `.env` configuration.

## Features

- 🎵 Tracks top 100 Tiny Desk concerts by view count
- 📊 Stores historical view count data with timestamps
- 🌐 Beautiful Flask web interface
- ⏰ Automatic updates every 6 hours (configurable)
- 🕐 GMT timestamps
- 💾 SQLite database storage (fast, reliable, in-memory caching)
- ⚙️ Simple `.env` file configuration
- 🍓 Optimized for Raspberry Pi 5

## Requirements

- Raspberry Pi 5 (or any system with Python 3.8+)
- Python 3.8 or higher
- YouTube Data API v3 key (free from Google)

## Installation

Choose either **Docker** (recommended) or **Python** installation:

### Option A: Docker Installation (Recommended)

#### Quick Start with Docker Compose

1. **Clone and configure:**
```bash
cd /home/jorjazo/dev/priv/rpi-tinytracker
cp .env.example .env
nano .env  # Add your YouTube API key
```

2. **Run with docker-compose:**
```bash
docker-compose up -d
```

3. **Access:**
- Web Interface: `http://localhost:5000`
- Dashboard: `http://localhost:5000/dashboard`

#### Cross-Build for Raspberry Pi (from AMD64)

```bash
# Build ARM64 image on AMD64 machine
./build-arm64.sh

# Export image
docker save tinydesk-tracker:latest | gzip > tinydesk-tracker-arm64.tar.gz

# Transfer to Raspberry Pi
scp tinydesk-tracker-arm64.tar.gz pi@raspberrypi:~/

# On Raspberry Pi: Load and run
gunzip -c tinydesk-tracker-arm64.tar.gz | docker load
docker-compose up -d
```

#### Using Makefile

```bash
make -f Makefile.docker build-arm64  # Build for ARM64
make -f Makefile.docker save         # Save to file
make -f Makefile.docker run          # Run with docker-compose
make -f Makefile.docker logs         # View logs
make -f Makefile.docker stop         # Stop container
```

### Option B: Python Installation

### 1. Get YouTube API Key

1. Visit https://console.cloud.google.com/
2. Create a new project
3. Enable "YouTube Data API v3"
4. Create credentials → API Key
5. Copy the API key

### 2. Clone and Setup

```bash
cd /home/jorjazo/dev/priv/rpi-tinytracker

# Create virtual environment
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt
```

### 3. Configure

```bash
# Copy example env file
cp .env.example .env

# Edit with your settings
nano .env
```

Add your YouTube API key:
```env
YOUTUBE_API_KEY=your_actual_api_key_here
NPR_MUSIC_CHANNEL_ID=UC4eYXhJI4-7wSWc8UNRwD4A
DB_FILE=./data/tinydesk.db
UPDATE_INTERVAL_HOURS=6
```

### 4. Run

```bash
python3 -m tinydesk_tracker
```

Or:
```bash
./start.sh
```

## Usage

### Access Web Interface

- Local: `http://localhost:5000/`
- Network: `http://<raspberry-pi-ip>:5000/`

### API Endpoints

- `GET /api/top` - Get top 100 videos sorted by views
- `GET /api/data` - Get raw historical data (backward compatible)
- `GET /api/status` - Get system status and database stats
- `GET /api/history/<video_id>` - Get view history for specific video

### Example API Response

```json
{
  "videos": [
    {
      "rank": 1,
      "videoId": "vWwgrjjIMXA",
      "title": "Tyler, The Creator: NPR Music Tiny Desk Concert",
      "views": 12500000,
      "url": "https://www.youtube.com/watch?v=vWwgrjjIMXA"
    }
  ],
  "lastUpdate": 1698768000,
  "total": 100
}
```

## Run as Service (systemd)

Create a systemd service to run on boot:

```bash
sudo nano /etc/systemd/system/tinydesk-tracker.service
```

Add:
```ini
[Unit]
Description=YouTube Tiny Desk Concert Tracker
After=network.target

[Service]
Type=simple
User=pi
WorkingDirectory=/home/jorjazo/dev/priv/rpi-tinytracker
Environment="PATH=/home/jorjazo/dev/priv/rpi-tinytracker/venv/bin"
ExecStart=/home/jorjazo/dev/priv/rpi-tinytracker/venv/bin/python3 -m tinydesk_tracker
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

# Check status
sudo systemctl status tinydesk-tracker

# View logs
sudo journalctl -u tinydesk-tracker -f
```

## Configuration Options

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `YOUTUBE_API_KEY` | Required | Your YouTube Data API v3 key |
| `NPR_MUSIC_CHANNEL_ID` | `UC4eYXhJI4-7wSWc8UNRwD4A` | NPR Music channel ID |
| `DB_FILE` | `./data/tinydesk.db` | Path to SQLite database file |
| `UPDATE_INTERVAL_HOURS` | `6` | Hours between updates |

## Data Storage

Data is stored in SQLite database at `./data/tinydesk.db` with the following schema:

### Database Schema

**videos** - Main video information
- `video_id` (TEXT PRIMARY KEY) - YouTube video ID
- `title` (TEXT) - Video title
- `current_views` (INTEGER) - Current view count
- `last_updated` (INTEGER) - Unix timestamp of last update

**history** - Historical view count data
- `id` (INTEGER PRIMARY KEY) - Auto-increment ID
- `video_id` (TEXT) - Foreign key to videos table
- `timestamp` (INTEGER) - Unix timestamp of data point
- `view_count` (INTEGER) - View count at that timestamp

**metadata** - System metadata
- `key` (TEXT PRIMARY KEY) - Metadata key
- `value` (TEXT) - Metadata value

### Performance Features
- **WAL mode** for better concurrency
- **10MB cache** for faster queries
- **Automatic indexing** on video_id and timestamp
- **In-memory temp storage** for operations
- **History limited** to 100 entries per video

## Development

### Run in Debug Mode

```python
# Edit tinydesk_tracker/__main__.py, change:
app.run(host="0.0.0.0", port=5000, debug=True)
```

### Manual Update

```python
from tinydesk_tracker import TinyDeskTracker

tracker = TinyDeskTracker()
tracker.update()
```

## Troubleshooting

### API Key Issues
- Verify key in `.env` file
- Check YouTube Data API is enabled
- Check API quota in Google Cloud Console

### Permission Errors
```bash
python3 -m tinydesk_tracker
mkdir -p data
chmod 755 data
```

### Port Already in Use
```bash
# Change port in code or kill existing process
sudo lsof -i :5000
sudo kill -9 <PID>
```

## Comparison: Raspberry Pi vs ESP32-C3

| Feature | Raspberry Pi 5 | ESP32-C3 |
|---------|---------------|----------|
| Language | Python | C++ |
| Configuration | .env file | Serial commands |
| Memory | 8GB RAM | 320KB RAM |
| Batch Size | 50 videos | 10 videos |
| Web Server | Flask | AsyncWebServer |
| Storage | SQLite database | LittleFS |
| Updates | schedule library | Timer interrupts |
| Query Speed | Very fast (indexed) | Fast (linear) |

## License

Same as ESP32 version

## Acknowledgments

- NPR Music for Tiny Desk Concert series
- YouTube Data API v3
- Flask and Python community

## Docker Commands Reference

### Build Commands
```bash
# Build for current architecture
docker build -t tinydesk-tracker .

# Build for ARM64 (Raspberry Pi) from AMD64
./build-arm64.sh

# Build for multiple architectures
docker buildx build --platform linux/amd64,linux/arm64 -t tinydesk-tracker .
```

### Run Commands
```bash
# Run with docker-compose (recommended)
docker-compose up -d

# Run directly
docker run -d \
  -p 5000:5000 \
  --env-file .env \
  -v $(pwd)/data:/app/data \
  --name tinydesk-tracker \
  tinydesk-tracker:latest

# View logs
docker-compose logs -f
# or
docker logs -f tinydesk-tracker

# Stop
docker-compose down
# or
docker stop tinydesk-tracker
```

### Maintenance
```bash
# Update and restart
docker-compose pull
docker-compose up -d

# Backup database
docker cp tinydesk-tracker:/app/data/tinydesk.db ./backup-$(date +%Y%m%d).db

# Access container shell
docker exec -it tinydesk-tracker bash
```

## Notes

- Free API tier: 10,000 quota units/day
- This tracker uses ~6 units per update
- ~24 units per day = well within free quota
- Historical data limited to 100 entries per video
- Docker image size: ~150MB (Python slim base)


# 🚀 Quick Start Guide

## Choose Your Installation Method

### 🐳 Docker (Recommended)

**Easiest way to deploy on Raspberry Pi:**

```bash
# 1. On your AMD64 machine (laptop/desktop)
cd /home/jorjazo/dev/priv/rpi-tinytracker
./build-arm64.sh

# 2. Transfer to Raspberry Pi (follow script instructions)
# The script will guide you through the process

# 3. On Raspberry Pi
docker-compose up -d

# Done! Access at http://raspberrypi.local:5000
```

**Or run locally (native Docker):**

```bash
docker-compose up -d
```

### 🐍 Python (Traditional)

```bash
# 1. Setup
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# 2. Configure
cp .env.example .env
nano .env  # Add YouTube API key

# 3. Run
python3 -m tinydesk_tracker

# Or use the helper script
./start.sh
```

## 📚 Documentation

- **[DOCKER.md](DOCKER.md)** - Complete Docker guide
- **[README.md](README.md)** - Full documentation
- **Makefile.docker** - Run `make -f Makefile.docker help`

## 🌐 Access Points

Once running:
- **Main List**: http://localhost:5000
- **Analytics Dashboard**: http://localhost:5000/dashboard
- **API Status**: http://localhost:5000/api/status
- **API Analytics**: http://localhost:5000/api/analytics

## 🔧 Common Tasks

### Docker
```bash
# View logs
docker-compose logs -f

# Stop
docker-compose down

# Restart
docker-compose restart

# Backup database
docker cp tinydesk-tracker:/app/data/tinydesk.db ./backup.db
```

### Python
```bash
# Stop (Ctrl+C)

# Or if running in background
pkill -f "python3 -m tinydesk_tracker"

# Backup database
cp data/tinydesk.db backup.db
```

## ⚙️ Configuration

Edit `.env` file:
```env
YOUTUBE_API_KEY=your_key_here
UPDATE_INTERVAL_HOURS=0.5  # 30 minutes
```

## 🆘 Need Help?

1. Check **[DOCKER.md](DOCKER.md)** for Docker troubleshooting
2. Check **[README.md](README.md)** for Python troubleshooting
3. View logs: `docker-compose logs` or check terminal output

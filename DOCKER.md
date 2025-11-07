# Docker Deployment Guide

Quick reference for deploying Tiny Desk Tracker with Docker.

## 🚀 Quick Start

### On Raspberry Pi (ARM64)

```bash
# 1. Setup
git clone <repo>
cd rpi-tinytracker
cp .env.example .env
nano .env  # Add your YouTube API key

# 2. Run
docker-compose up -d

# 3. Access
# http://localhost:5000
# http://localhost:5000/dashboard
```

## 📦 Cross-Build from AMD64 to ARM64

### Method 1: Using the build script (Easiest)

```bash
# On your AMD64 machine (laptop/desktop)
./build-arm64.sh

# Follow the interactive prompts
# The script will build and show you transfer instructions
```

### Method 2: Manual steps

```bash
# On AMD64 machine
# 1. Setup buildx
docker buildx create --name multiarch --use
docker buildx inspect --bootstrap

# 2. Build for ARM64
docker buildx build \
  --platform linux/arm64 \
  --tag tinydesk-tracker:latest \
  --load \
  .

# 3. Save to file
docker save tinydesk-tracker:latest | gzip > tinydesk-tracker-arm64.tar.gz

# 4. Transfer to Raspberry Pi
scp tinydesk-tracker-arm64.tar.gz pi@raspberrypi.local:~/

# 5. On Raspberry Pi
gunzip -c tinydesk-tracker-arm64.tar.gz | docker load

# 6. Create .env file on Raspberry Pi
cat > .env << EOF
YOUTUBE_API_KEY=your_api_key_here
NPR_MUSIC_CHANNEL_ID=UC4eYXhJI4-7wSWc8UNRwD4A
DB_FILE=/app/data/tinydesk.db
UPDATE_INTERVAL_HOURS=0.5
EOF

# 7. Run
docker-compose up -d
```

### Method 3: Using Makefile

```bash
make -f Makefile.docker build-arm64
make -f Makefile.docker save
# ... transfer file ...
# On Raspberry Pi:
make -f Makefile.docker load
make -f Makefile.docker run
```

## 🔧 Common Commands

### Container Management
```bash
# Start
docker-compose up -d

# Stop
docker-compose down

# Restart
docker-compose restart

# View logs
docker-compose logs -f

# Check status
docker-compose ps
```

### Database Management
```bash
# Backup database
docker cp tinydesk-tracker:/app/data/tinydesk.db ./backup.db

# Restore database
docker cp ./backup.db tinydesk-tracker:/app/data/tinydesk.db
docker-compose restart

# View database size
docker exec tinydesk-tracker ls -lh /app/data/
```

### Updates
```bash
# Rebuild and restart
docker-compose build
docker-compose up -d

# Or with no-cache
docker-compose build --no-cache
docker-compose up -d --force-recreate
```

## 🐛 Troubleshooting

### Container won't start
```bash
# Check logs
docker-compose logs

# Check if port is in use
sudo lsof -i :5000

# Remove old containers
docker-compose down -v
docker-compose up -d
```

### API key issues
```bash
# Verify environment variables
docker exec tinydesk-tracker env | grep YOUTUBE

# Update .env and restart
nano .env
docker-compose restart
```

### Database issues
```bash
# Reset database (WARNING: deletes all data)
docker-compose down
rm -rf data/
docker-compose up -d
```

## 📊 Health Check

```bash
# Check container health
docker ps

# Test API
curl http://localhost:5000/api/status

# View statistics
curl http://localhost:5000/api/analytics | jq
```

## 🔒 Security Tips

1. **Never commit .env files** - Already in .gitignore
2. **Use Docker secrets** for production:
   ```yaml
   # docker-compose.yml
   secrets:
     youtube_api_key:
       file: ./secrets/youtube_api_key.txt
   ```
3. **Run as non-root user** (add to Dockerfile):
   ```dockerfile
   USER nobody:nogroup
   ```
4. **Use reverse proxy** (Nginx/Caddy) for HTTPS

## 🌐 Access from Network

The container binds to `0.0.0.0:5000`, accessible from:
- Local: `http://localhost:5000`
- Network: `http://<raspberry-pi-ip>:5000`

Find Raspberry Pi IP:
```bash
hostname -I
```

## 📈 Performance

- **Memory usage**: ~50-100MB
- **CPU usage**: Very low (spikes during updates)
- **Disk usage**: ~150MB image + database size
- **Network**: Minimal except during YouTube API calls

## 🔄 Auto-start on Boot

Docker-compose with `restart: unless-stopped` automatically starts on boot if Docker is enabled:

```bash
# Enable Docker on boot
sudo systemctl enable docker

# Verify
sudo systemctl status docker
```

## 📦 Multi-Architecture Support

Build for multiple platforms:
```bash
docker buildx build \
  --platform linux/amd64,linux/arm64,linux/arm/v7 \
  --tag yourname/tinydesk-tracker:latest \
  --push \
  .
```

## 🎯 Production Deployment

For production, consider:

1. **Use a registry** (Docker Hub, GitHub Container Registry)
2. **Add nginx reverse proxy** with SSL
3. **Set up automatic backups**
4. **Monitor with Prometheus/Grafana**
5. **Use docker-compose override** for secrets

Example production setup:
```bash
# Pull from registry
docker pull yourname/tinydesk-tracker:latest

# Run with production config
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```




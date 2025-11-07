# Multi-stage Dockerfile for Raspberry Pi Tiny Desk Tracker
# Supports both native ARM64 and cross-compilation from AMD64

FROM python:3.11-slim

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    sqlite3 \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first for better caching
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy application files
COPY tinydesk_tracker.py .
COPY templates/ templates/

# Create data directory
RUN mkdir -p /app/data

# Expose Flask port
EXPOSE 5000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c "import requests; requests.get('http://localhost:5000/api/status')" || exit 1

# Run the application
CMD ["python3", "tinydesk_tracker.py"]




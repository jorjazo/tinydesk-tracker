#!/bin/bash
#
# Quick start script for Tiny Desk Tracker
#

set -e

echo "========================================"
echo "  Tiny Desk Tracker - Quick Start"
echo "========================================"
echo ""

# Check if virtual environment exists
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
    echo "✓ Virtual environment created"
fi

# Activate virtual environment
echo "Activating virtual environment..."
source venv/bin/activate

# Check if dependencies are installed
if ! python -c "import requests" &> /dev/null; then
    echo "Installing dependencies..."
    pip install -r requirements.txt
    echo "✓ Dependencies installed"
fi

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo ""
    echo "⚠️  .env file not found!"
    echo ""
    echo "Please create a .env file with your configuration:"
    echo "  cp .env.example .env"
    echo "  nano .env"
    echo ""
    echo "Add your YouTube API key to the .env file, then run this script again."
    exit 1
fi

# Create data directory if it doesn't exist
mkdir -p data

# Start the tracker
echo ""
echo "Starting Tiny Desk Tracker..."
echo ""
python3 -m tinydesk_tracker


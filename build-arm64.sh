#!/bin/bash
#
# Cross-build script for Raspberry Pi (ARM64) from AMD64
# This script builds a Docker image for ARM64 architecture
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
IMAGE_NAME="tinydesk-tracker"
IMAGE_TAG="latest"
PLATFORM="linux/arm64"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Tiny Desk Tracker - ARM64 Builder${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed${NC}"
    echo "Please install Docker first: https://docs.docker.com/get-docker/"
    exit 1
fi

# Check if buildx is available
if ! docker buildx version &> /dev/null; then
    echo -e "${YELLOW}Setting up Docker buildx...${NC}"
    docker buildx create --name multiarch --use 2>/dev/null || docker buildx use multiarch
fi

echo -e "${GREEN}✓ Docker buildx is ready${NC}"
echo ""

# Display build information
echo -e "${BLUE}Build Configuration:${NC}"
echo "  Image Name: ${IMAGE_NAME}:${IMAGE_TAG}"
echo "  Platform: ${PLATFORM}"
echo "  Architecture: ARM64 (Raspberry Pi 5)"
echo ""

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}Warning: .env file not found${NC}"
    echo "Make sure to create .env file with your YouTube API key before running"
    echo ""
fi

# Ask for confirmation
read -p "$(echo -e ${YELLOW}Start build? [y/N]:${NC} )" -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${RED}Build cancelled${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}Building Docker image for ARM64...${NC}"
echo ""

# Build the image
docker buildx build \
    --platform ${PLATFORM} \
    --tag ${IMAGE_NAME}:${IMAGE_TAG} \
    --load \
    .

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  Build Successful!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo -e "${BLUE}Image details:${NC}"
    docker images ${IMAGE_NAME}:${IMAGE_TAG}
    echo ""
    echo -e "${BLUE}Next steps:${NC}"
    echo ""
    echo -e "${GREEN}1. Export image to file (for transfer to Raspberry Pi):${NC}"
    echo "   docker save ${IMAGE_NAME}:${IMAGE_TAG} | gzip > ${IMAGE_NAME}-arm64.tar.gz"
    echo ""
    echo -e "${GREEN}2. Transfer to Raspberry Pi:${NC}"
    echo "   scp ${IMAGE_NAME}-arm64.tar.gz pi@raspberrypi:~/"
    echo ""
    echo -e "${GREEN}3. Load on Raspberry Pi:${NC}"
    echo "   gunzip -c ${IMAGE_NAME}-arm64.tar.gz | docker load"
    echo ""
    echo -e "${GREEN}4. Run with docker-compose:${NC}"
    echo "   docker-compose up -d"
    echo ""
    echo -e "${GREEN}Or run directly on this machine (if ARM64):${NC}"
    echo "   docker run -d -p 5000:5000 --env-file .env ${IMAGE_NAME}:${IMAGE_TAG}"
    echo ""
else
    echo ""
    echo -e "${RED}========================================${NC}"
    echo -e "${RED}  Build Failed!${NC}"
    echo -e "${RED}========================================${NC}"
    exit 1
fi




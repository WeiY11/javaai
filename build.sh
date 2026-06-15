#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo "  EviMind standalone build"
echo "=========================================="

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "[1/3] Building frontend..."
cd "$SCRIPT_DIR/frontend"
npm install
npm run build

echo "[2/3] Copying frontend into backend static resources..."
cd "$SCRIPT_DIR"
rm -rf src/main/resources/static
cp -r frontend/dist src/main/resources/static
echo "Frontend files copied."

echo "[3/3] Building backend JAR..."
./mvnw package -DskipTests -q

echo ""
echo "=========================================="
echo "  Build complete"
echo "  Output: target/evimind-0.0.1-SNAPSHOT.jar"
echo "  Start: ./start.sh"
echo "=========================================="

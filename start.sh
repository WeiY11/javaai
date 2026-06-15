#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo "  EviMind standalone service"
echo "  http://localhost:8080"
echo "=========================================="

JAR_FILE="target/evimind-0.0.1-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "JAR not found. Run ./build.sh first."
    exit 1
fi

if [ -z "${DEEPSEEK_API_KEY:-}" ]; then
    echo "[warn] DEEPSEEK_API_KEY is not set. AI features may be limited."
fi

mkdir -p data

echo ""
echo "Starting service..."
java -jar "$JAR_FILE" --spring.profiles.active=standalone

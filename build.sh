#!/bin/bash
set -e

echo "=========================================="
echo "  evimind 独立包构建脚本"
echo "=========================================="

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "[1/3] 构建前端..."
cd "$SCRIPT_DIR/frontend"
npm install
npm run build

echo "[2/3] 复制前端到后端..."
cd "$SCRIPT_DIR"
rm -rf src/main/resources/static
cp -r frontend/dist src/main/resources/static
echo "前端文件已复制"

echo "[3/3] 构建后端 JAR..."
./mvnw package -DskipTests -q

echo ""
echo "=========================================="
echo "  构建完成！"
echo "  输出: target/evimind-0.0.1-SNAPSHOT.jar"
echo "  启动: ./start.sh"
echo "=========================================="

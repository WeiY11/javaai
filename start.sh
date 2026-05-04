#!/bin/bash
echo "=========================================="
echo "  javaai - AI 数据分析平台"
echo "  http://localhost:8080"
echo "=========================================="

JAR_FILE="target/javaai-0.0.1-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "未找到 JAR 文件，请先运行 build.sh"
    exit 1
fi

if [ -z "$DEEPSEEK_API_KEY" ]; then
    echo "[警告] DEEPSEEK_API_KEY 未设置，AI 功能可能无法使用"
fi

mkdir -p data

echo ""
echo "启动服务..."
java -jar "$JAR_FILE" --spring.profiles.active=standalone

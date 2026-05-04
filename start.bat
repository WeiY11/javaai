@echo off
chcp 65001 >nul
title javaai 独立应用

echo ==========================================
echo   javaai - AI 数据分析平台
echo   http://localhost:8080
echo ==========================================

REM 检查 JAR 是否存在
if not exist "target\javaai-0.0.1-SNAPSHOT.jar" (
    echo 未找到 JAR 文件，请先运行 build.bat
    pause
    exit /b 1
)

REM 设置必要的环境变量（如果未设置）
if "%DEEPSEEK_API_KEY%"=="" (
    echo [警告] DEEPSEEK_API_KEY 未设置，AI 功能可能无法使用
    echo 请设置环境变量或编辑 .env 文件
)

REM 创建数据目录
if not exist "data" mkdir data

echo.
echo 启动服务...
java -jar target\javaai-0.0.1-SNAPSHOT.jar --spring.profiles.active=standalone

pause

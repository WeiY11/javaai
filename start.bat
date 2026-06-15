@echo off
chcp 65001 >nul
title EviMind standalone service

echo ==========================================
echo   EviMind standalone service
echo   http://localhost:8080
echo ==========================================

if not exist "target\evimind-0.0.1-SNAPSHOT.jar" (
    echo JAR not found. Run build.bat first.
    pause
    exit /b 1
)

if "%DEEPSEEK_API_KEY%"=="" (
    echo [warn] DEEPSEEK_API_KEY is not set. AI features may be limited.
)

if not exist "data" mkdir data

echo.
echo Starting service...
java -jar target\evimind-0.0.1-SNAPSHOT.jar --spring.profiles.active=standalone

pause

@echo off
chcp 65001 >nul
cd /d "%~dp0"

if not exist "target\evimind-0.0.1-SNAPSHOT.jar" (
    echo Missing target\evimind-0.0.1-SNAPSHOT.jar. Run: mvnw.cmd package -DskipTests
    exit /b 1
)

"C:\Program Files\Java\jdk-22\bin\java.exe" -jar target\evimind-0.0.1-SNAPSHOT.jar --spring.profiles.active=standalone > evimind-backend.out.log 2> evimind-backend.err.log

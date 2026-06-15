@echo off
chcp 65001 >nul
echo ==========================================
echo   EviMind standalone build
echo ==========================================

echo [1/3] Building frontend...
cd /d "%~dp0frontend"
call npm install
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo Frontend build failed.
    exit /b 1
)

echo [2/3] Copying frontend into backend static resources...
cd /d "%~dp0"
if exist "src\main\resources\static" (
    rmdir /s /q "src\main\resources\static"
)
xcopy /e /i "frontend\dist" "src\main\resources\static"
if %ERRORLEVEL% NEQ 0 (
    echo Failed to copy frontend files.
    exit /b 1
)
echo Frontend files copied.

echo [3/3] Building backend JAR...
call mvnw.cmd package -DskipTests -q
if %ERRORLEVEL% NEQ 0 (
    echo Backend build failed.
    exit /b 1
)

echo.
echo ==========================================
echo   Build complete
echo   Output: target\evimind-0.0.1-SNAPSHOT.jar
echo   Start: start.bat
echo ==========================================
pause

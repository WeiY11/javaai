@echo off
chcp 65001 >nul
echo ==========================================
echo   evimind 独立包构建脚本
echo ==========================================

echo [1/3] 构建前端...
cd /d "%~dp0frontend"
call npm install
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo 前端构建失败！
    exit /b 1
)

echo [2/3] 复制前端到后端...
cd /d "%~dp0"
if exist "src\main\resources\static" (
    rmdir /s /q "src\main\resources\static"
)
xcopy /e /i "frontend\dist" "src\main\resources\static"
echo 前端文件已复制

echo [3/3] 构建后端 JAR...
call mvnw.cmd package -DskipTests -q
if %ERRORLEVEL% NEQ 0 (
    echo 后端构建失败！
    exit /b 1
)

echo.
echo ==========================================
echo   构建完成！
echo   输出: target\evimind-0.0.1-SNAPSHOT.jar
echo   启动: start.bat
echo ==========================================
pause

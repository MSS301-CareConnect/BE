@echo off
rem Dừng toàn bộ stack Docker CareConnect (compose.yaml, project "careconnect").
rem Chỉ "docker compose down": xóa container + network của stack. KHÔNG xóa volume/dữ liệu, KHÔNG xóa image.
rem Nhấn đúp để chạy, hoặc: stop.bat --no-pause
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"
title CareConnect - stop

set "PAUSE_AT_END=1"
if /i "%~1"=="--no-pause" set "PAUSE_AT_END=0"
set "RC=1"

echo ============================================================
echo  CareConnect - dừng toàn bộ Docker services
echo ============================================================

where docker >nul 2>&1
if errorlevel 1 (
    echo [LỖI] Không tìm thấy lệnh "docker".
    goto :done
)
docker info >nul 2>&1
if errorlevel 1 (
    echo [LỖI] Docker Engine chưa chạy - không có container nào của stack để dừng.
    goto :done
)

docker compose down
if errorlevel 1 (
    echo.
    echo [LỖI] Dừng KHÔNG thành công. Kiểm tra: docker compose ps -a
    goto :done
)

echo.
echo [THÀNH CÔNG] Đã dừng stack careconnect. Volume dữ liệu được giữ nguyên.
echo Khởi động lại: start.bat
set "RC=0"

:done
echo.
if "%PAUSE_AT_END%"=="1" pause
exit /b %RC%

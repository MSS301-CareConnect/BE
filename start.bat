@echo off
rem Khởi động toàn bộ stack Docker CareConnect (compose.yaml, project "careconnect").
rem Nhấn đúp để chạy, hoặc: start.bat --no-pause (không dừng màn hình ở cuối, dùng trong terminal/CI).
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"
title CareConnect - start

set "PAUSE_AT_END=1"
if /i "%~1"=="--no-pause" set "PAUSE_AT_END=0"
set "RC=1"
set "SERVICES=IdentityService CareRecipientService CareCatalogService ProviderService BookingService MediaService PaymentService ApiGateway"
rem Project Compose riêng cũ của từng service (docker-compose.yml trong thư mục service) - dùng chung volume/port.
set "LEGACY_PROJECTS=identityservice carerecipientservice carecatalogservice providerservice bookingservice mediaservice paymentservice apigateway"
rem Volume dữ liệu dùng lại: <tên volume>:<project cũ>:<khóa volume trong compose cũ>.
rem Không dùng "=" trong danh sách: lệnh for của cmd coi "=" là dấu phân tách phần tử.
set "VOLUMES=identityservice_identity_postgres:identityservice:identity_postgres identityservice_identity_redis:identityservice:identity_redis carerecipientservice_care_recipient_postgres:carerecipientservice:care_recipient_postgres carecatalogservice_care_catalog_postgres:carecatalogservice:care_catalog_postgres providerservice_provider_postgres:providerservice:provider_postgres bookingservice_booking_postgres:bookingservice:booking_postgres mediaservice_media_postgres_data:mediaservice:media_postgres_data paymentservice_payment_postgres:paymentservice:payment_postgres"

echo ============================================================
echo  CareConnect - khởi động toàn bộ Docker services
echo ============================================================

rem ---- 1. Docker CLI ----------------------------------------------------------
where docker >nul 2>&1
if errorlevel 1 (
    echo [LỖI] Không tìm thấy lệnh "docker". Cài Docker Desktop rồi chạy lại.
    goto :done
)

rem ---- 2. Docker Engine sẵn sàng (tự mở Docker Desktop nếu chưa chạy) -----------
docker info >nul 2>&1
if not errorlevel 1 goto :docker_ready
set "DOCKER_DESKTOP=%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
if not exist "%DOCKER_DESKTOP%" (
    echo [LỖI] Docker Engine chưa chạy. Mở Docker Desktop, đợi trạng thái "Engine running" rồi chạy lại.
    goto :done
)
echo Docker Engine chưa chạy - đang mở Docker Desktop, chờ tối đa 3 phút...
start "" "%DOCKER_DESKTOP%"
for /l %%i in (1,1,60) do (
    ping -n 4 127.0.0.1 >nul
    docker info >nul 2>&1
    if not errorlevel 1 goto :docker_ready
)
echo [LỖI] Docker Engine vẫn chưa sẵn sàng sau 3 phút. Kiểm tra Docker Desktop rồi chạy lại.
goto :done

:docker_ready
echo [OK] Docker Engine đang chạy.

rem ---- 3. Docker Compose v2.20+ (cần "include") --------------------------------
set "COMPOSE_VERSION="
for /f "usebackq delims=" %%v in (`docker compose version --short 2^>nul`) do set "COMPOSE_VERSION=%%v"
if not defined COMPOSE_VERSION (
    echo [LỖI] Không có Docker Compose v2 ^("docker compose"^). Cập nhật Docker Desktop.
    goto :done
)
set "COMPOSE_VERSION=%COMPOSE_VERSION:v=%"
for /f "tokens=1,2 delims=.-" %%a in ("%COMPOSE_VERSION%") do (
    set /a "COMPOSE_MAJOR=%%a, COMPOSE_MINOR=%%b" 2>nul
)
set "COMPOSE_OK=0"
if !COMPOSE_MAJOR! GTR 2 set "COMPOSE_OK=1"
if !COMPOSE_MAJOR! EQU 2 if !COMPOSE_MINOR! GEQ 20 set "COMPOSE_OK=1"
if "%COMPOSE_OK%"=="0" (
    echo [LỖI] Docker Compose %COMPOSE_VERSION% quá cũ - cần 2.20 trở lên. Cập nhật Docker Desktop.
    goto :done
)
echo [OK] Docker Compose %COMPOSE_VERSION%.

rem ---- 4. File cấu hình bắt buộc ----------------------------------------------
set "MISSING="
for %%s in (%SERVICES%) do (
    if not exist "%%s\.env" set "MISSING=!MISSING! %%s\.env"
)
if not exist "IdentityService\keys\private.pem" set "MISSING=!MISSING! IdentityService\keys\private.pem"
if not exist "IdentityService\keys\public.pem" set "MISSING=!MISSING! IdentityService\keys\public.pem"
if defined MISSING (
    echo [LỖI] Thiếu file cấu hình:
    for %%f in (!MISSING!) do echo        - %%f
    echo        Tạo .env từ .env.example của từng service; tạo khóa RSA bằng IdentityService\scripts\New-DevKeys.ps1.
    echo        Xem DOCKER.md.
    goto :done
)
echo [OK] Đủ .env của 8 service và khóa RSA của IdentityService.

rem ---- 5. Container cũ đang giữ volume/port của stack ---------------------------
rem Hai PostgreSQL cùng mở một volume sẽ hỏng dữ liệu -> phải dừng container cũ trước (chỉ dừng, không xóa).
set "CONFLICTS= "
for %%p in (%LEGACY_PROJECTS%) do (
    for /f "usebackq delims=" %%c in (`docker ps --filter "label=com.docker.compose.project=%%p" --format "{{.Names}}"`) do (
        call :add_conflict %%c
    )
)
for %%e in (%VOLUMES%) do (
    for /f "tokens=1 delims=:" %%n in ("%%e") do (
        for /f "usebackq delims=" %%c in (`docker ps --filter "volume=%%n" --format "{{.Names}}"`) do (
            call :add_conflict %%c
        )
    )
)
if not "%CONFLICTS%"==" " (
    echo.
    echo Các container sau đang chạy ngoài stack careconnect và dùng chung volume dữ liệu / port:
    for %%c in (%CONFLICTS%) do echo        - %%c
    echo Cần dừng chúng trước. Chỉ "docker stop" - không xóa container, volume hay dữ liệu.
    choice /c YN /n /m "Dừng các container trên và tiếp tục? [Y/N]: "
    if errorlevel 2 (
        echo Đã hủy. Không thay đổi gì.
        goto :done
    )
    docker stop %CONFLICTS% >nul
    if errorlevel 1 (
        echo [LỖI] Không dừng được container cũ. Kiểm tra bằng: docker ps
        goto :done
    )
    echo [OK] Đã dừng container cũ.
)

rem ---- 6. Volume dữ liệu (dùng lại volume cũ; máy mới thì tạo volume trống) --------
for %%e in (%VOLUMES%) do (
    for /f "tokens=1,2,3 delims=:" %%n in ("%%e") do (
        docker volume inspect %%n >nul 2>&1
        if errorlevel 1 (
            docker volume create --label com.docker.compose.project=%%o --label com.docker.compose.volume=%%p %%n >nul
            if errorlevel 1 (
                echo [LỖI] Không tạo được volume %%n.
                goto :done
            )
            echo [MỚI] Tạo volume trống %%n - DB mới, schema SQL sẽ được áp tự động lần đầu.
        )
    )
)
echo [OK] Volume dữ liệu sẵn sàng.

rem ---- 7. Build + start, chờ mọi service healthy ---------------------------------
echo.
echo Đang build image và khởi động container. Lần đầu có thể mất vài phút ^(Maven tải dependency^)...
echo.
docker compose up --build -d --wait --wait-timeout 300
if errorlevel 1 (
    echo.
    echo [LỖI] Khởi động KHÔNG thành công. Trạng thái hiện tại:
    docker compose ps -a
    echo.
    echo Xem log service lỗi: docker compose logs --tail 200 ^<tên-service^>
    echo Lỗi "port is already allocated" / "address already in use": một app Spring hoặc container khác đang chiếm port
    echo 8080-8087, 5441-5447 hoặc 6381 - tắt nó rồi chạy lại.
    goto :done
)

echo.
docker compose ps
echo.
echo ============================================================
echo  [THÀNH CÔNG] Mọi service đã chạy và healthy.
echo   API Gateway      http://localhost:8080   ^(Swagger: /swagger-ui.html^)
echo   Identity         http://localhost:8081
echo   CareRecipient    http://localhost:8082
echo   CareCatalog      http://localhost:8083
echo   Provider         http://localhost:8084
echo   Booking          http://localhost:8085
echo   Media            http://localhost:8086
echo   Payment          http://localhost:8087
echo  Dừng: stop.bat     Log: docker compose logs -f ^<tên-service^>
echo ============================================================
set "RC=0"

:done
echo.
if "%PAUSE_AT_END%"=="1" pause
exit /b %RC%

rem Thêm tên container vào danh sách xung đột (bỏ container của chính stack, bỏ trùng).
:add_conflict
set "NAME=%~1"
if /i "%NAME:~0,12%"=="careconnect-" exit /b 0
echo "%CONFLICTS%" | findstr /i /c:" %NAME% " >nul
if not errorlevel 1 exit /b 0
set "CONFLICTS=%CONFLICTS%%NAME% "
exit /b 0

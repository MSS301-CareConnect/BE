#requires -Version 5.1
<#
.SYNOPSIS
  End-to-end test CareConnect: IdentityService + CareRecipientService + CareCatalogService + ProviderService +
  BookingService + MediaService + ApiGateway on real PostgreSQL/Redis (PaymentService replaced by payment_stub.py).

.DESCRIPTION
  1. Builds the 7 JARs (skip with -SkipBuild).
  2. Starts ISOLATED infrastructure from each service's own docker-compose.yml under separate compose projects
     (careconnect-e2e-*, own volumes, ports 55601/56601/55602/55603/55604/55605/55606) - dev databases are never touched.
  3. Applies schema/*.sql, starts the 7 JARs with each service's .env + e2e overrides. Every service calls the others
     through ApiGateway (http://localhost:8080, public /api/v1/** only - no /internal, no X-Internal-Secret). Machine
     calls use SERVICE tokens (POST /api/v1/auth/service-token, IAM-01): client secrets for booking / care-recipient /
     provider / payment are generated per run, their BCrypt hashes go to IDENTITY_SERVICE_CLIENTS - all passed as process
     environment only, no .env file is modified and BookingService needs no .env. PaymentService is not in this repo
     yet: payment_stub.py answers the Payment APIs Booking calls (port 8087) and e2e.py confirms the deposit with the
     "payment" SERVICE token on POST /api/v1/bookings/{id}/deposit-paid.
     Seeds CUSTOMER / CENTER_MANAGER / CAREGIVER accounts into the e2e Identity DB, one care service + center offering +
     hourly price plan into the e2e Catalog DB, and one center + two caregivers (skills, daily shifts / schedules for
     120 days) into the e2e Provider DB (accounts with a password need an e-mail OTP in the real flow).
  4. Runs scripts/e2e/e2e.py phases a (mobile), b (web cookie + CSRF), c (old /internal + per-role paths gone, SERVICE
     token), e (booking flow); stops IdentityService and runs phase d (fallback); stops CareRecipientService and runs
     phase f (booking without CareRecipient). 5. Stops everything and removes the e2e volumes (keep them with -KeepInfra).
  Requires: JDK 21, Docker, Python 3 on PATH; .env in IdentityService, CareRecipientService, CareCatalogService,
  ProviderService, MediaService, ApiGateway; ports 8080-8087 free. Logs: %TEMP%\careconnect-e2e\*.log. Secrets are
  never printed.

.EXAMPLE
  .\scripts\e2e\Run-E2E.ps1
  .\scripts\e2e\Run-E2E.ps1 -SkipBuild -KeepInfra
#>
param(
    [switch] $SkipBuild,
    [switch] $KeepInfra
)
$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$logDirectory = Join-Path ([System.IO.Path]::GetTempPath()) 'careconnect-e2e'
New-Item -ItemType Directory -Force $logDirectory | Out-Null

$identityDir = Join-Path $root 'IdentityService'
$careDir = Join-Path $root 'CareRecipientService'
$catalogDir = Join-Path $root 'CareCatalogService'
$providerDir = Join-Path $root 'ProviderService'
$bookingDir = Join-Path $root 'BookingService'
$mediaDir = Join-Path $root 'MediaService'
$gatewayDir = Join-Path $root 'ApiGateway'
$identityProject = 'careconnect-e2e-identity'
$careProject = 'careconnect-e2e-care-recipient'
$catalogProject = 'careconnect-e2e-care-catalog'
$providerProject = 'careconnect-e2e-provider'
$bookingProject = 'careconnect-e2e-booking'
$mediaProject = 'careconnect-e2e-media'
$identityDbPort = 55601
$identityRedisPort = 56601
$careDbPort = 55602
$catalogDbPort = 55603
$providerDbPort = 55604
$bookingDbPort = 55605
$mediaDbPort = 55606
$paymentStubPort = 8087
$adminEmail = 'e2e.admin@careconnect.test'
$adminPassword = 'E2e-Admin#2026'
$customerPassword = 'E2e-Pass#2026'
# Every service reaches the others through ApiGateway (public /api/v1/** only); JWKS is read directly from Identity.
$gatewayUrl = 'http://localhost:8080'

# Fixed ids shared by the Identity seed (users.center_id, user_id), the Catalog seed (offering of the center) and the
# Provider seed (care_centers, caregiver_profiles, caregiver_services).
$centerId = [guid]::NewGuid().ToString()
$managerUserId = [guid]::NewGuid().ToString()
$caregiverUserId = [guid]::NewGuid().ToString()
$caregiverProfileId = [guid]::NewGuid().ToString()
$caregiver2UserId = [guid]::NewGuid().ToString()
$caregiver2ProfileId = [guid]::NewGuid().ToString()
$careServiceId = [guid]::NewGuid().ToString()
$centerServiceId = [guid]::NewGuid().ToString()
$pricePlanId = [guid]::NewGuid().ToString()

function New-Secret {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return -join ($bytes | ForEach-Object { $_.ToString('x2') })
}

# Generated per run, only passed to child processes (never written to disk, never printed).
# SERVICE token clients (IAM-01): raw secret goes to <SERVICE>_SERVICE_CLIENT_SECRET of the caller, BCrypt hash to
# IDENTITY_SERVICE_CLIENTS. "payment" stands in for PaymentService (e2e.py confirms deposits with it).
$serviceClientSecrets = [ordered]@{
    'booking'        = New-Secret
    'care-recipient' = New-Secret
    'provider'       = New-Secret
    'payment'        = New-Secret
}
$bookingDbUser = 'booking_e2e'
$bookingDbPassword = New-Secret
$cancellationPreviewSecret = New-Secret

function Read-EnvFile([string] $directory, [switch] $Optional) {
    $path = Join-Path $directory '.env'
    $values = @{}
    if (-not (Test-Path -LiteralPath $path)) {
        if ($Optional) { return $values }
        throw "Missing $path - copy .env.example to .env first."
    }
    foreach ($line in Get-Content -LiteralPath $path) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
        $parts = $trimmed -split '=', 2
        if ($parts.Count -eq 2) { $values[$parts[0]] = $parts[1].Trim() }
    }
    return $values
}

# DB name = last path segment of the JDBC URL in .env (same DB name, e2e port).
function Get-E2eDbUrl([string] $jdbcUrl, [int] $port) {
    $databaseName = ($jdbcUrl -split '/')[-1].Split('?')[0]
    return "jdbc:postgresql://localhost:$port/$databaseName"
}

# Windows PowerShell 5.1 turns native stderr (docker progress output) into errors under 'Stop' - judge by exit code only.
function Invoke-Native([string] $what, [scriptblock] $command) {
    $ErrorActionPreference = 'Continue'
    & $command
    if ($LASTEXITCODE -ne 0) { throw "$what failed (exit $LASTEXITCODE)." }
}

function Invoke-Phase([string] $phase) {
    $ErrorActionPreference = 'Continue'
    # Out-Host: python output must not become the function's return value (only the exit code is returned).
    & python (Join-Path $PSScriptRoot 'e2e.py') $phase | Out-Host
    return $LASTEXITCODE
}

function Assert-PortFree([int] $port) {
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($listener) { throw "Port $port is already in use - stop the running service first." }
}

function Stop-ByPort([int] $port) {
    $listeners = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($listener in $listeners) {
        Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue
    }
}

# Child process gets its own environment (.env + overrides) - the current PowerShell session is not modified.
function Start-ServiceJar([string] $directory, [string] $jarName, [string] $logName, [hashtable] $overrides) {
    $environment = Read-EnvFile $directory -Optional
    foreach ($key in $overrides.Keys) { $environment[$key] = $overrides[$key] }
    $jar = Join-Path $directory "target\$jarName"
    if (-not (Test-Path -LiteralPath $jar)) { throw "Missing $jar - run without -SkipBuild." }
    $log = Join-Path $logDirectory $logName
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = 'cmd.exe'
    $startInfo.Arguments = "/c java -jar `"$jar`" > `"$log`" 2>&1"
    $startInfo.WorkingDirectory = $directory
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    foreach ($key in $environment.Keys) { $startInfo.EnvironmentVariables[$key] = [string] $environment[$key] }
    [System.Diagnostics.Process]::Start($startInfo) | Out-Null
}

function Wait-Healthy([string] $name, [int] $port) {
    for ($i = 0; $i -lt 90; $i++) {
        try {
            $health = Invoke-RestMethod "http://localhost:$port/actuator/health" -TimeoutSec 2
            if ($health.status -eq 'UP') { Write-Host "  $name UP (port $port)"; return }
        } catch { }
        Start-Sleep -Seconds 2
    }
    throw "$name did not become healthy on port $port - see $logDirectory."
}

function Wait-Postgres([string] $container) {
    $ErrorActionPreference = 'Continue'
    for ($i = 0; $i -lt 30; $i++) {
        docker exec $container sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' *> $null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 1
    }
    throw "PostgreSQL $container not ready."
}

function Invoke-Psql([string] $container, [string] $sqlFile) {
    Invoke-Native "copy $sqlFile" { docker cp $sqlFile "${container}:/tmp/e2e.sql" | Out-Null }
    Invoke-Native "psql $sqlFile" { docker exec $container sh -c 'psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -f /tmp/e2e.sql' }
}

# docker compose reads <service>/.env for credentials; the given variables (ports, e2e credentials) apply to this call only.
function Invoke-Compose([string] $directory, [string] $project, [hashtable] $variables, [string[]] $arguments) {
    foreach ($key in $variables.Keys) { Set-Item "Env:$key" ([string] $variables[$key]) }
    Push-Location $directory
    try {
        Invoke-Native "docker compose $($arguments -join ' ')" { docker compose -p $project @arguments }
    } finally {
        Pop-Location
        foreach ($key in $variables.Keys) { Remove-Item "Env:$key" -ErrorAction SilentlyContinue }
    }
}

function Get-ComposeContainer([string] $directory, [string] $project, [string] $service) {
    $ErrorActionPreference = 'Continue'
    Push-Location $directory
    try { return (docker compose -p $project ps -q $service).Trim() } finally { Pop-Location }
}

function Find-M2Jar([string] $relativeDirectory, [string] $versionPrefix) {
    $base = Join-Path $env:USERPROFILE ".m2\repository\$relativeDirectory"
    $jar = Get-ChildItem -Path $base -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources|javadoc' -and $_.Directory.Name.StartsWith($versionPrefix) } |
        Sort-Object { [version] ($_.Directory.Name -replace '[^0-9.].*$', '') } | Select-Object -Last 1
    if (-not $jar) { throw "Cannot find $relativeDirectory $versionPrefix* in ~/.m2 - build IdentityService once." }
    return $jar.FullName
}

$identityPorts = @{ IDENTITY_DB_PORT = $identityDbPort; IDENTITY_REDIS_PORT = $identityRedisPort }
$carePorts = @{ CARE_RECIPIENT_DB_PORT = $careDbPort }
$catalogPorts = @{ CARE_CATALOG_DB_PORT = $catalogDbPort }
$providerPorts = @{ PROVIDER_DB_PORT = $providerDbPort }
$bookingVariables = @{ BOOKING_DB_PORT = $bookingDbPort; BOOKING_DB_USERNAME = $bookingDbUser; BOOKING_DB_PASSWORD = $bookingDbPassword }
$mediaPorts = @{ MEDIA_DB_PORT = $mediaDbPort }
# One isolated PostgreSQL (+ Redis for Identity) per service, from the service's own docker-compose.yml (service "postgres").
$infrastructure = [ordered]@{
    identity = @{ Dir = $identityDir; Project = $identityProject; Variables = $identityPorts; Schema = 'schema\identity.sql' }
    care     = @{ Dir = $careDir; Project = $careProject; Variables = $carePorts; Schema = 'schema\care_recipient.sql' }
    catalog  = @{ Dir = $catalogDir; Project = $catalogProject; Variables = $catalogPorts; Schema = 'schema\catalog.sql' }
    provider = @{ Dir = $providerDir; Project = $providerProject; Variables = $providerPorts; Schema = 'schema\provider.sql' }
    booking  = @{ Dir = $bookingDir; Project = $bookingProject; Variables = $bookingVariables; Schema = 'schema\booking.sql' }
    media    = @{ Dir = $mediaDir; Project = $mediaProject; Variables = $mediaPorts; Schema = 'schema\media.sql' }
}
$servicePorts = 8080, 8081, 8082, 8083, 8084, 8085, 8086, $paymentStubPort
$databasePorts = $identityDbPort, $identityRedisPort, $careDbPort, $catalogDbPort, $providerDbPort, $bookingDbPort, $mediaDbPort
# Variables read by e2e.py (set in this session only, removed in finally, never printed).
$e2eVariables = 'E2E_CENTER_ID', 'E2E_CAREGIVER_ID', 'E2E_CAREGIVER_USER_ID', 'E2E_CARE_SERVICE_ID', 'E2E_CENTER_SERVICE_ID',
    'E2E_PRICE_PLAN_ID', 'PAYMENT_SERVICE_CLIENT_ID', 'PAYMENT_SERVICE_CLIENT_SECRET'
$failed = @()

foreach ($port in $servicePorts + $databasePorts) { Assert-PortFree $port }
$identityEnv = Read-EnvFile $identityDir
$careEnv = Read-EnvFile $careDir
$catalogEnv = Read-EnvFile $catalogDir
$providerEnv = Read-EnvFile $providerDir
$mediaEnv = Read-EnvFile $mediaDir
Read-EnvFile $gatewayDir | Out-Null
$jwtIssuer = $identityEnv['IDENTITY_JWT_ISSUER']
$jwksUri = 'http://localhost:8081/.well-known/jwks.json'

try {
    if (-not $SkipBuild) {
        Write-Host '== Build JARs (tests are run separately with mvnw test)'
        foreach ($directory in $identityDir, $careDir, $catalogDir, $providerDir, $bookingDir, $mediaDir, $gatewayDir) {
            Push-Location $directory
            try { Invoke-Native "package $directory" { & .\mvnw.cmd -q package -DskipTests } } finally { Pop-Location }
        }
    }

    Write-Host '== Isolated infrastructure (fresh volumes)'
    foreach ($item in $infrastructure.Values) { Invoke-Compose $item.Dir $item.Project $item.Variables @('down', '-v') }
    foreach ($item in $infrastructure.Values) { Invoke-Compose $item.Dir $item.Project $item.Variables @('up', '-d') }
    $containers = @{}
    foreach ($name in $infrastructure.Keys) {
        $item = $infrastructure[$name]
        $containers[$name] = Get-ComposeContainer $item.Dir $item.Project 'postgres'
        Wait-Postgres $containers[$name]
        Invoke-Psql $containers[$name] (Join-Path $item.Dir $item.Schema)
    }

    Write-Host '== SERVICE token clients (IDENTITY_SERVICE_CLIENTS = clientId:BCrypt hash)'
    $classpath = (Find-M2Jar 'org\springframework\security\spring-security-crypto' '7.') + ';' +
        (Find-M2Jar 'commons-logging\commons-logging' '1.3')
    $seedCustomers = Join-Path $PSScriptRoot 'SeedCustomers.java'
    $serviceClientEntries = @()
    $ErrorActionPreference = 'Continue'
    foreach ($clientId in $serviceClientSecrets.Keys) {
        $hash = & java -cp $classpath $seedCustomers '--bcrypt' $serviceClientSecrets[$clientId]
        if ($LASTEXITCODE -ne 0) { throw "BCrypt hash for service client $clientId failed." }
        $serviceClientEntries += "${clientId}:$(([string] $hash).Trim())"
    }
    $ErrorActionPreference = 'Stop'

    Write-Host '== Start services (+ PaymentService stub)'
    $keyDirectory = 'file:' + ((Join-Path $identityDir 'keys') -replace '\\', '/')
    Start-ServiceJar $identityDir 'IdentityService-0.0.1-SNAPSHOT.jar' 'identity.log' @{
        IDENTITY_DB_URL             = (Get-E2eDbUrl $identityEnv['IDENTITY_DB_URL'] $identityDbPort)
        IDENTITY_REDIS_PORT         = "$identityRedisPort"
        IDENTITY_JWT_PRIVATE_KEY    = "$keyDirectory/private.pem"
        IDENTITY_JWT_PUBLIC_KEY     = "$keyDirectory/public.pem"
        IDENTITY_COOKIE_SECURE      = 'false'
        IDENTITY_OPENAPI_SERVER_URL = '/api/v1'
        IDENTITY_BOOTSTRAP_ENABLED  = 'true'
        IDENTITY_BOOTSTRAP_EMAIL    = $adminEmail
        IDENTITY_BOOTSTRAP_PASSWORD = $adminPassword
        IDENTITY_SERVICE_CLIENTS    = ($serviceClientEntries -join ',')
    }
    Wait-Healthy 'IdentityService' 8081
    Start-ServiceJar $careDir 'CareRecipientService-0.0.1-SNAPSHOT.jar' 'care-recipient.log' @{
        CARE_RECIPIENT_DB_URL                = (Get-E2eDbUrl $careEnv['CARE_RECIPIENT_DB_URL'] $careDbPort)
        CARE_RECIPIENT_OPENAPI_SERVER_URL    = '/api/v1'
        CARE_RECIPIENT_JWT_ISSUER            = $jwtIssuer
        CARE_RECIPIENT_JWT_JWKS_URI          = $jwksUri
        CARE_RECIPIENT_IDENTITY_URL          = $gatewayUrl
        CARE_RECIPIENT_BOOKING_URL           = $gatewayUrl
        CARE_RECIPIENT_SERVICE_CLIENT_ID     = 'care-recipient'
        CARE_RECIPIENT_SERVICE_CLIENT_SECRET = $serviceClientSecrets['care-recipient']
    }
    Start-ServiceJar $catalogDir 'CareCatalogService-0.0.1-SNAPSHOT.jar' 'care-catalog.log' @{
        CARE_CATALOG_DB_URL             = (Get-E2eDbUrl $catalogEnv['CARE_CATALOG_DB_URL'] $catalogDbPort)
        CARE_CATALOG_OPENAPI_SERVER_URL = '/api/v1'
        CARE_CATALOG_JWT_ISSUER         = $jwtIssuer
        CARE_CATALOG_JWT_JWKS_URI       = $jwksUri
    }
    Start-ServiceJar $providerDir 'ProviderService-0.0.1-SNAPSHOT.jar' 'provider.log' @{
        PROVIDER_DB_URL                = (Get-E2eDbUrl $providerEnv['PROVIDER_DB_URL'] $providerDbPort)
        PROVIDER_OPENAPI_SERVER_URL    = '/api/v1'
        PROVIDER_JWT_ISSUER            = $jwtIssuer
        PROVIDER_JWT_JWKS_URI          = $jwksUri
        PROVIDER_IDENTITY_URL          = $gatewayUrl
        PROVIDER_CARE_CATALOG_URL      = $gatewayUrl
        PROVIDER_BOOKING_URL           = $gatewayUrl
        PROVIDER_MEDIA_URL             = $gatewayUrl
        PROVIDER_SERVICE_CLIENT_ID     = 'provider'
        PROVIDER_SERVICE_CLIENT_SECRET = $serviceClientSecrets['provider']
    }
    Start-ServiceJar $bookingDir 'BookingService-0.0.1-SNAPSHOT.jar' 'booking.log' @{
        BOOKING_DB_URL                      = "jdbc:postgresql://localhost:$bookingDbPort/CARECONNECT_BOOKING"
        BOOKING_DB_USERNAME                 = $bookingDbUser
        BOOKING_DB_PASSWORD                 = $bookingDbPassword
        BOOKING_OPENAPI_SERVER_URL          = '/api/v1'
        BOOKING_JWT_ISSUER                  = $jwtIssuer
        BOOKING_JWT_JWKS_URI                = $jwksUri
        BOOKING_IDENTITY_URL                = $gatewayUrl
        BOOKING_CARE_RECIPIENT_URL          = $gatewayUrl
        BOOKING_PROVIDER_URL                = $gatewayUrl
        BOOKING_CARE_CATALOG_URL            = $gatewayUrl
        BOOKING_PAYMENT_URL                 = $gatewayUrl
        BOOKING_SERVICE_CLIENT_ID           = 'booking'
        BOOKING_SERVICE_CLIENT_SECRET       = $serviceClientSecrets['booking']
        BOOKING_CANCELLATION_PREVIEW_SECRET = $cancellationPreviewSecret
        # Schedulers must not interfere between phases (every e2e occurrence is in the future).
        BOOKING_EXPIRY_SCAN_INTERVAL        = '1h'
        BOOKING_MISSED_SCAN_INTERVAL        = '1h'
    }
    $mediaOverrides = @{
        MEDIA_DB_URL       = (Get-E2eDbUrl $mediaEnv['MEDIA_DB_URL'] $mediaDbPort)
        MEDIA_JWT_ISSUER   = $jwtIssuer
        MEDIA_JWT_JWKS_URI = $jwksUri
    }
    # e2e uploads nothing: placeholder R2 settings (when .env leaves them blank) only let MediaService start.
    $r2Placeholders = @{
        MEDIA_R2_ENDPOINT          = 'https://e2e-r2.invalid'
        MEDIA_R2_BUCKET            = 'e2e-bucket'
        MEDIA_R2_ACCESS_KEY_ID     = 'e2e-access-key'
        MEDIA_R2_SECRET_ACCESS_KEY = 'e2e-secret-key'
    }
    foreach ($key in $r2Placeholders.Keys) {
        if ([string]::IsNullOrWhiteSpace($mediaEnv[$key])) { $mediaOverrides[$key] = $r2Placeholders[$key] }
    }
    Start-ServiceJar $mediaDir 'MediaService-0.0.1-SNAPSHOT.jar' 'media.log' $mediaOverrides
    # PaymentService is not in this repo yet (planned, port 8087): the stub answers the Payment APIs Booking calls.
    Start-Process -FilePath 'python' -ArgumentList @("`"$(Join-Path $PSScriptRoot 'payment_stub.py')`"", "$paymentStubPort") `
        -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDirectory 'payment-stub.log') `
        -RedirectStandardError (Join-Path $logDirectory 'payment-stub.err.log') | Out-Null
    Start-ServiceJar $gatewayDir 'ApiGateway-0.0.1-SNAPSHOT.jar' 'gateway.log' @{
        GATEWAY_IDENTITY_URL       = 'http://localhost:8081'
        GATEWAY_CARE_RECIPIENT_URL = 'http://localhost:8082'
        GATEWAY_CARE_CATALOG_URL   = 'http://localhost:8083'
        GATEWAY_PROVIDER_URL       = 'http://localhost:8084'
        GATEWAY_BOOKING_URL        = 'http://localhost:8085'
        GATEWAY_MEDIA_URL          = 'http://localhost:8086'
        GATEWAY_PAYMENT_URL        = "http://localhost:$paymentStubPort"
        GATEWAY_JWT_ISSUER         = $jwtIssuer
        GATEWAY_JWT_JWKS_URI       = $jwksUri
    }
    Wait-Healthy 'CareRecipientService' 8082
    Wait-Healthy 'CareCatalogService' 8083
    Wait-Healthy 'ProviderService' 8084
    Wait-Healthy 'BookingService' 8085
    Wait-Healthy 'MediaService' 8086
    Wait-Healthy 'PaymentService stub' $paymentStubPort
    Wait-Healthy 'ApiGateway' 8080

    Write-Host '== Seed accounts (Identity), care service / offering / price plan (Catalog), center / caregivers (Provider)'
    $seedSql = Join-Path $logDirectory 'seed.sql'
    $ErrorActionPreference = 'Continue'
    $seed = & java -cp $classpath $seedCustomers $customerPassword `
        'e2e.owner@careconnect.test:E2E Owner' 'e2e.relative@careconnect.test:E2E Relative' 'e2e.third@careconnect.test:E2E Third' `
        "e2e.manager@careconnect.test:E2E Manager:CENTER_MANAGER:${managerUserId}:${centerId}" `
        "e2e.caregiver@careconnect.test:E2E Caregiver:CAREGIVER:${caregiverUserId}:${centerId}" `
        "e2e.caregiver2@careconnect.test:E2E Caregiver Two:CAREGIVER:${caregiver2UserId}:${centerId}"
    if ($LASTEXITCODE -ne 0) { throw 'SeedCustomers failed.' }
    $ErrorActionPreference = 'Stop'
    [System.IO.File]::WriteAllLines($seedSql, [string[]] $seed)
    Invoke-Psql $containers['identity'] $seedSql
    # Hourly plan 100000 VND (HOUR requires 60 minutes - CAT-01); e2e.py checks booking totals against this price.
    $catalogSeedSql = Join-Path $logDirectory 'seed-catalog.sql'
    [System.IO.File]::WriteAllLines($catalogSeedSql, [string[]] @(
        'BEGIN;',
        "INSERT INTO care_services (care_services_id, name, description, category, status, created_at, updated_at) VALUES ('$careServiceId', 'E2E Elderly care', 'E2E', 'ELDERLY_CARE', 'ACTIVE', now(), now());",
        "INSERT INTO center_services (center_services_id, care_services_id, center_id, description, status, created_at, updated_at) VALUES ('$centerServiceId', '$careServiceId', '$centerId', 'E2E', 'ACTIVE', now(), now());",
        "INSERT INTO price_plans (price_id, center_service_id, plan_name, pricing_unit, duration_minutes, base_price, is_active, created_at, updated_at) VALUES ('$pricePlanId', '$centerServiceId', 'E2E hourly', 'HOUR', 60, 100000, true, now(), now());",
        'COMMIT;'))
    Invoke-Psql $containers['catalog'] $catalogSeedSql
    # Skills (caregiver_services = center offering, PRV-01) and an approved 06:00-20:00 shift every day for 120 days for
    # both caregivers (caregiver_schedules SCHEDULED) so the Provider availability check (PRV-02) accepts every e2e slot.
    $providerSeedSql = Join-Path $logDirectory 'seed-provider.sql'
    [System.IO.File]::WriteAllLines($providerSeedSql, [string[]] @(
        'BEGIN;',
        "INSERT INTO care_centers (center_id, center_name, email, address, center_status, manager_user_id, created_by, version, created_at, updated_at) VALUES ('$centerId', 'E2E Center', 'e2e.center@careconnect.test', 'Ha Noi', 'ACTIVE', '$managerUserId', '$managerUserId', 0, now(), now());",
        "INSERT INTO caregiver_profiles (caregiver_id, user_id, center_id, full_name, experience_years, rating_average, rating_count, verification_status, caregiver_status, created_by, version, created_at, updated_at) VALUES ('$caregiverProfileId', '$caregiverUserId', '$centerId', 'E2E Caregiver', 3, 0, 0, 'VERIFIED', 'ACTIVE', '$managerUserId', 0, now(), now());",
        "INSERT INTO caregiver_profiles (caregiver_id, user_id, center_id, full_name, experience_years, rating_average, rating_count, verification_status, caregiver_status, created_by, version, created_at, updated_at) VALUES ('$caregiver2ProfileId', '$caregiver2UserId', '$centerId', 'E2E Caregiver Two', 2, 0, 0, 'VERIFIED', 'ACTIVE', '$managerUserId', 0, now(), now());",
        "INSERT INTO caregiver_services (caregiver_id, center_service_id, care_service_id, service_name, assigned_by, assigned_at) SELECT caregiver_id, '$centerServiceId', '$careServiceId', 'E2E Elderly care', '$managerUserId', now() FROM caregiver_profiles WHERE center_id = '$centerId';",
        "INSERT INTO work_shifts (work_shift_id, center_id, work_date, start_time, end_time, shift_type, shift_status, capacity, created_by, version, created_at, updated_at) SELECT gen_random_uuid(), '$centerId', day::date, '06:00', '20:00', 'FULL_DAY', 'OPEN', 2, '$managerUserId', 0, now(), now() FROM generate_series(current_date, current_date + 120, interval '1 day') AS day;",
        "INSERT INTO shift_registrations (registration_id, work_shift_id, caregiver_id, registration_status, reviewed_by, reviewed_at, version, created_at, updated_at) SELECT gen_random_uuid(), w.work_shift_id, c.caregiver_id, 'APPROVED', '$managerUserId', now(), 0, now(), now() FROM work_shifts w JOIN caregiver_profiles c ON c.center_id = w.center_id WHERE w.center_id = '$centerId';",
        "INSERT INTO caregiver_schedules (schedule_id, registration_id, caregiver_id, work_shift_id, schedule_status, assigned_by, assigned_at, version, created_at, updated_at) SELECT gen_random_uuid(), registration_id, caregiver_id, work_shift_id, 'SCHEDULED', '$managerUserId', now(), 0, now(), now() FROM shift_registrations;",
        'COMMIT;'))
    Invoke-Psql $containers['provider'] $providerSeedSql

    $env:E2E_CENTER_ID = $centerId
    $env:E2E_CAREGIVER_ID = $caregiverProfileId
    $env:E2E_CAREGIVER_USER_ID = $caregiverUserId
    $env:E2E_CARE_SERVICE_ID = $careServiceId
    $env:E2E_CENTER_SERVICE_ID = $centerServiceId
    $env:E2E_PRICE_PLAN_ID = $pricePlanId
    # Machine identity standing in for PaymentService: e2e.py gets a SERVICE token with it to confirm deposits.
    $env:PAYMENT_SERVICE_CLIENT_ID = 'payment'
    $env:PAYMENT_SERVICE_CLIENT_SECRET = $serviceClientSecrets['payment']

    foreach ($phase in 'a', 'b', 'c', 'e') {
        Write-Host "== Phase $phase"
        if ((Invoke-Phase $phase) -ne 0) { $failed += $phase }
    }
    Write-Host '== Phase d (IdentityService stopped)'
    Stop-ByPort 8081
    Start-Sleep -Seconds 2
    if ((Invoke-Phase 'd') -ne 0) { $failed += 'd' }
    Write-Host '== Phase f (CareRecipientService stopped)'
    Stop-ByPort 8082
    Start-Sleep -Seconds 2
    if ((Invoke-Phase 'f') -ne 0) { $failed += 'f' }
} finally {
    Write-Host '== Cleanup'
    foreach ($name in $e2eVariables) {
        Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    }
    foreach ($port in $servicePorts) { Stop-ByPort $port }
    if (-not $KeepInfra) {
        foreach ($item in $infrastructure.Values) { Invoke-Compose $item.Dir $item.Project $item.Variables @('down', '-v') }
    }
    Write-Host "Logs: $logDirectory"
}

if ($failed.Count -gt 0) {
    Write-Host "E2E FAILED phases: $($failed -join ', ')" -ForegroundColor Red
    exit 1
}
Write-Host 'E2E PASSED (a, b, c, e, d, f)' -ForegroundColor Green

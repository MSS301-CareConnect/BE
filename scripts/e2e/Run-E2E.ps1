#requires -Version 5.1
<#
.SYNOPSIS
  End-to-end test CareConnect: IdentityService + CareRecipientService + ProviderService + BookingService + ApiGateway
  on real PostgreSQL/Redis.

.DESCRIPTION
  1. Builds the 5 JARs (skip with -SkipBuild).
  2. Starts ISOLATED infrastructure from each service's own docker-compose.yml under separate compose projects
     (careconnect-e2e-*, own volumes, ports 55601/56601/55602/55604/55605) - dev databases are never touched.
  3. Applies schema/*.sql, starts the 5 JARs with each service's .env + e2e overrides. Cross-service settings added for
     Booking (URLs, JWT issuer/JWKS, internal secrets) are generated per run and passed as process environment only -
     no .env file is modified and BookingService needs no .env. Seeds CUSTOMER / CENTER_MANAGER / CAREGIVER accounts
     into the e2e Identity DB and one center + two caregivers into the e2e Provider DB (accounts with a password need an
     e-mail OTP in the real flow).
  4. Runs scripts/e2e/e2e.py phases a (mobile), b (web cookie + CSRF), c (/internal blocked), e (booking flow);
     stops IdentityService and runs phase d (fallback); stops CareRecipientService and runs phase f (booking without
     CareRecipient). 5. Stops everything and removes the e2e volumes (keep them with -KeepInfra).
  Requires: JDK 21, Docker, Python 3 on PATH; .env in IdentityService, CareRecipientService, ProviderService,
  ApiGateway; ports 8080-8082, 8084-8085 free. Logs: %TEMP%\careconnect-e2e\*.log. Secrets are never printed.

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
$providerDir = Join-Path $root 'ProviderService'
$bookingDir = Join-Path $root 'BookingService'
$gatewayDir = Join-Path $root 'ApiGateway'
$identityProject = 'careconnect-e2e-identity'
$careProject = 'careconnect-e2e-care-recipient'
$providerProject = 'careconnect-e2e-provider'
$bookingProject = 'careconnect-e2e-booking'
$identityDbPort = 55601
$identityRedisPort = 56601
$careDbPort = 55602
$providerDbPort = 55604
$bookingDbPort = 55605
$adminEmail = 'e2e.admin@careconnect.test'
$adminPassword = 'E2e-Admin#2026'
$customerPassword = 'E2e-Pass#2026'

# Fixed ids shared by the Identity seed (users.center_id, user_id) and the Provider seed (care_centers, caregiver_profiles).
$centerId = [guid]::NewGuid().ToString()
$managerUserId = [guid]::NewGuid().ToString()
$caregiverUserId = [guid]::NewGuid().ToString()
$caregiverProfileId = [guid]::NewGuid().ToString()
$caregiver2UserId = [guid]::NewGuid().ToString()
$caregiver2ProfileId = [guid]::NewGuid().ToString()

function New-Secret {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return -join ($bytes | ForEach-Object { $_.ToString('x2') })
}

# Generated per run, only passed to child processes (never written to disk, never printed).
$providerSecret = New-Secret
$bookingSecret = New-Secret
$bookingDbUser = 'booking_e2e'
$bookingDbPassword = New-Secret

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
$providerPorts = @{ PROVIDER_DB_PORT = $providerDbPort }
$bookingVariables = @{ BOOKING_DB_PORT = $bookingDbPort; BOOKING_DB_USERNAME = $bookingDbUser; BOOKING_DB_PASSWORD = $bookingDbPassword }
$servicePorts = 8080, 8081, 8082, 8084, 8085
$failed = @()

foreach ($port in $servicePorts + @($identityDbPort, $identityRedisPort, $careDbPort, $providerDbPort, $bookingDbPort)) { Assert-PortFree $port }
$identityEnv = Read-EnvFile $identityDir
$careEnv = Read-EnvFile $careDir
$providerEnv = Read-EnvFile $providerDir
Read-EnvFile $gatewayDir | Out-Null
$jwtIssuer = $identityEnv['IDENTITY_JWT_ISSUER']
$jwksUri = 'http://localhost:8081/.well-known/jwks.json'

try {
    if (-not $SkipBuild) {
        Write-Host '== Build JARs (tests are run separately with mvnw test)'
        foreach ($directory in $identityDir, $careDir, $providerDir, $bookingDir, $gatewayDir) {
            Push-Location $directory
            try { Invoke-Native "package $directory" { & .\mvnw.cmd -q package -DskipTests } } finally { Pop-Location }
        }
    }

    Write-Host '== Isolated infrastructure (fresh volumes)'
    Invoke-Compose $identityDir $identityProject $identityPorts @('down', '-v')
    Invoke-Compose $careDir $careProject $carePorts @('down', '-v')
    Invoke-Compose $providerDir $providerProject $providerPorts @('down', '-v')
    Invoke-Compose $bookingDir $bookingProject $bookingVariables @('down', '-v')
    Invoke-Compose $identityDir $identityProject $identityPorts @('up', '-d')
    Invoke-Compose $careDir $careProject $carePorts @('up', '-d')
    Invoke-Compose $providerDir $providerProject $providerPorts @('up', '-d')
    Invoke-Compose $bookingDir $bookingProject $bookingVariables @('up', '-d')
    $identityPg = Get-ComposeContainer $identityDir $identityProject 'postgres'
    $carePg = Get-ComposeContainer $careDir $careProject 'postgres'
    $providerPg = Get-ComposeContainer $providerDir $providerProject 'postgres'
    $bookingPg = Get-ComposeContainer $bookingDir $bookingProject 'postgres'
    foreach ($container in $identityPg, $carePg, $providerPg, $bookingPg) { Wait-Postgres $container }
    Invoke-Psql $identityPg (Join-Path $identityDir 'schema\identity.sql')
    Invoke-Psql $carePg (Join-Path $careDir 'schema\care_recipient.sql')
    Invoke-Psql $providerPg (Join-Path $providerDir 'schema\provider.sql')
    Invoke-Psql $bookingPg (Join-Path $bookingDir 'schema\booking.sql')

    Write-Host '== Start services'
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
    }
    Wait-Healthy 'IdentityService' 8081
    Start-ServiceJar $careDir 'CareRecipientService-0.0.1-SNAPSHOT.jar' 'care-recipient.log' @{
        CARE_RECIPIENT_DB_URL                  = (Get-E2eDbUrl $careEnv['CARE_RECIPIENT_DB_URL'] $careDbPort)
        CARE_RECIPIENT_OPENAPI_SERVER_URL      = '/api/v1'
        CARE_RECIPIENT_BOOKING_URL             = 'http://localhost:8085'
        CARE_RECIPIENT_BOOKING_INTERNAL_SECRET = $bookingSecret
    }
    Start-ServiceJar $providerDir 'ProviderService-0.0.1-SNAPSHOT.jar' 'provider.log' @{
        PROVIDER_DB_URL                   = (Get-E2eDbUrl $providerEnv['PROVIDER_DB_URL'] $providerDbPort)
        PROVIDER_JWT_ISSUER               = $jwtIssuer
        PROVIDER_JWT_JWKS_URI             = $jwksUri
        PROVIDER_INTERNAL_SECRET          = $providerSecret
        PROVIDER_IDENTITY_URL             = 'http://localhost:8081'
        PROVIDER_IDENTITY_INTERNAL_SECRET = $identityEnv['IDENTITY_INTERNAL_SECRET']
    }
    Start-ServiceJar $bookingDir 'BookingService-0.0.1-SNAPSHOT.jar' 'booking.log' @{
        BOOKING_DB_URL                         = "jdbc:postgresql://localhost:$bookingDbPort/CARECONNECT_BOOKING"
        BOOKING_DB_USERNAME                    = $bookingDbUser
        BOOKING_DB_PASSWORD                    = $bookingDbPassword
        BOOKING_OPENAPI_SERVER_URL             = '/api/v1'
        BOOKING_JWT_ISSUER                     = $jwtIssuer
        BOOKING_JWT_JWKS_URI                   = $jwksUri
        BOOKING_INTERNAL_SECRET                = $bookingSecret
        BOOKING_IDENTITY_URL                   = 'http://localhost:8081'
        BOOKING_IDENTITY_INTERNAL_SECRET       = $identityEnv['IDENTITY_INTERNAL_SECRET']
        BOOKING_CARE_RECIPIENT_URL             = 'http://localhost:8082'
        BOOKING_CARE_RECIPIENT_INTERNAL_SECRET = $careEnv['CARE_RECIPIENT_INTERNAL_SECRET']
        BOOKING_PROVIDER_URL                   = 'http://localhost:8084'
        BOOKING_PROVIDER_INTERNAL_SECRET       = $providerSecret
        BOOKING_EXPIRY_SCAN_INTERVAL           = '1h'
    }
    Start-ServiceJar $gatewayDir 'ApiGateway-0.0.1-SNAPSHOT.jar' 'gateway.log' @{
        GATEWAY_BOOKING_URL = 'http://localhost:8085'
    }
    Wait-Healthy 'CareRecipientService' 8082
    Wait-Healthy 'ProviderService' 8084
    Wait-Healthy 'BookingService' 8085
    Wait-Healthy 'ApiGateway' 8080

    Write-Host '== Seed accounts (e2e Identity DB) and center / caregivers (e2e Provider DB)'
    $classpath = (Find-M2Jar 'org\springframework\security\spring-security-crypto' '7.') + ';' +
        (Find-M2Jar 'commons-logging\commons-logging' '1.3')
    $seedSql = Join-Path $logDirectory 'seed.sql'
    $ErrorActionPreference = 'Continue'
    $seed = & java -cp $classpath (Join-Path $PSScriptRoot 'SeedCustomers.java') $customerPassword `
        'e2e.owner@careconnect.test:E2E Owner' 'e2e.relative@careconnect.test:E2E Relative' 'e2e.third@careconnect.test:E2E Third' `
        "e2e.manager@careconnect.test:E2E Manager:CENTER_MANAGER:${managerUserId}:${centerId}" `
        "e2e.caregiver@careconnect.test:E2E Caregiver:CAREGIVER:${caregiverUserId}:${centerId}" `
        "e2e.caregiver2@careconnect.test:E2E Caregiver Two:CAREGIVER:${caregiver2UserId}:${centerId}"
    if ($LASTEXITCODE -ne 0) { throw 'SeedCustomers failed.' }
    $ErrorActionPreference = 'Stop'
    [System.IO.File]::WriteAllLines($seedSql, [string[]] $seed)
    Invoke-Psql $identityPg $seedSql
    $providerSeedSql = Join-Path $logDirectory 'seed-provider.sql'
    [System.IO.File]::WriteAllLines($providerSeedSql, [string[]] @(
        'BEGIN;',
        "INSERT INTO care_centers (center_id, center_name, email, address, center_status, manager_user_id, created_by, version, created_at, updated_at) VALUES ('$centerId', 'E2E Center', 'e2e.center@careconnect.test', 'Ha Noi', 'ACTIVE', '$managerUserId', '$managerUserId', 0, now(), now());",
        "INSERT INTO caregiver_profiles (caregiver_id, user_id, center_id, full_name, experience_years, rating_average, rating_count, verification_status, caregiver_status, created_by, version, created_at, updated_at) VALUES ('$caregiverProfileId', '$caregiverUserId', '$centerId', 'E2E Caregiver', 3, 0, 0, 'VERIFIED', 'ACTIVE', '$managerUserId', 0, now(), now());",
        "INSERT INTO caregiver_profiles (caregiver_id, user_id, center_id, full_name, experience_years, rating_average, rating_count, verification_status, caregiver_status, created_by, version, created_at, updated_at) VALUES ('$caregiver2ProfileId', '$caregiver2UserId', '$centerId', 'E2E Caregiver Two', 2, 0, 0, 'VERIFIED', 'ACTIVE', '$managerUserId', 0, now(), now());",
        'COMMIT;'))
    Invoke-Psql $providerPg $providerSeedSql

    # Read by e2e.py phase e/f; removed in finally (child processes only, never printed).
    $env:E2E_BOOKING_INTERNAL_SECRET = $bookingSecret
    $env:E2E_CENTER_ID = $centerId
    $env:E2E_CAREGIVER_ID = $caregiverProfileId
    $env:E2E_CAREGIVER_USER_ID = $caregiverUserId

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
    foreach ($name in 'E2E_BOOKING_INTERNAL_SECRET', 'E2E_CENTER_ID', 'E2E_CAREGIVER_ID', 'E2E_CAREGIVER_USER_ID') {
        Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    }
    foreach ($port in $servicePorts) { Stop-ByPort $port }
    if (-not $KeepInfra) {
        Invoke-Compose $identityDir $identityProject $identityPorts @('down', '-v')
        Invoke-Compose $careDir $careProject $carePorts @('down', '-v')
        Invoke-Compose $providerDir $providerProject $providerPorts @('down', '-v')
        Invoke-Compose $bookingDir $bookingProject $bookingVariables @('down', '-v')
    }
    Write-Host "Logs: $logDirectory"
}

if ($failed.Count -gt 0) {
    Write-Host "E2E FAILED phases: $($failed -join ', ')" -ForegroundColor Red
    exit 1
}
Write-Host 'E2E PASSED (a, b, c, e, d, f)' -ForegroundColor Green

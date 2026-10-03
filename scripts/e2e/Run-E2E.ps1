#requires -Version 5.1
<#
.SYNOPSIS
  End-to-end test CareConnect: IdentityService + CareRecipientService + ApiGateway on real PostgreSQL/Redis.

.DESCRIPTION
  1. Builds the 3 JARs (skip with -SkipBuild).
  2. Starts ISOLATED infrastructure from each service's own docker-compose.yml under separate compose projects
     (careconnect-e2e-*, own volumes, ports 55601/56601/55602) - dev databases are never touched.
  3. Applies schema/*.sql, starts the 3 JARs with each service's .env + e2e overrides (DB ports, absolute key paths,
     bootstrap ADMIN), seeds 3 CUSTOMER accounts (real sign-up needs an e-mail OTP).
  4. Runs scripts/e2e/e2e.py phases a (mobile), b (web cookie + CSRF), c (/internal blocked); stops IdentityService;
     runs phase d (fallback). 5. Stops everything and removes the e2e volumes (keep them with -KeepInfra).
  Requires: JDK 21, Docker, Python 3 on PATH; .env in IdentityService, CareRecipientService, ApiGateway; ports
  8080-8082 free. Logs: %TEMP%\careconnect-e2e\*.log. Secrets are never printed.

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
$gatewayDir = Join-Path $root 'ApiGateway'
$identityProject = 'careconnect-e2e-identity'
$careProject = 'careconnect-e2e-care-recipient'
$identityDbPort = 55601
$identityRedisPort = 56601
$careDbPort = 55602
$adminEmail = 'e2e.admin@careconnect.test'
$adminPassword = 'E2e-Admin#2026'
$customerPassword = 'E2e-Pass#2026'

function Read-EnvFile([string] $directory) {
    $path = Join-Path $directory '.env'
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing $path - copy .env.example to .env first." }
    $values = @{}
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
    $environment = Read-EnvFile $directory
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

# docker compose reads <service>/.env for credentials; ports are overridden only for this call.
function Invoke-Compose([string] $directory, [string] $project, [hashtable] $ports, [string[]] $arguments) {
    foreach ($key in $ports.Keys) { Set-Item "Env:$key" ([string] $ports[$key]) }
    Push-Location $directory
    try {
        Invoke-Native "docker compose $($arguments -join ' ')" { docker compose -p $project @arguments }
    } finally {
        Pop-Location
        foreach ($key in $ports.Keys) { Remove-Item "Env:$key" -ErrorAction SilentlyContinue }
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
$failed = @()

foreach ($port in 8080, 8081, 8082, $identityDbPort, $identityRedisPort, $careDbPort) { Assert-PortFree $port }
$identityEnv = Read-EnvFile $identityDir
$careEnv = Read-EnvFile $careDir
Read-EnvFile $gatewayDir | Out-Null

try {
    if (-not $SkipBuild) {
        Write-Host '== Build JARs (tests are run separately with mvnw test)'
        foreach ($directory in $identityDir, $careDir, $gatewayDir) {
            Push-Location $directory
            try { Invoke-Native "package $directory" { & .\mvnw.cmd -q package -DskipTests } } finally { Pop-Location }
        }
    }

    Write-Host '== Isolated infrastructure (fresh volumes)'
    Invoke-Compose $identityDir $identityProject $identityPorts @('down', '-v')
    Invoke-Compose $careDir $careProject $carePorts @('down', '-v')
    Invoke-Compose $identityDir $identityProject $identityPorts @('up', '-d')
    Invoke-Compose $careDir $careProject $carePorts @('up', '-d')
    $identityPg = Get-ComposeContainer $identityDir $identityProject 'postgres'
    $carePg = Get-ComposeContainer $careDir $careProject 'postgres'
    Wait-Postgres $identityPg
    Wait-Postgres $carePg
    Invoke-Psql $identityPg (Join-Path $identityDir 'schema\identity.sql')
    Invoke-Psql $carePg (Join-Path $careDir 'schema\care_recipient.sql')

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
        CARE_RECIPIENT_DB_URL             = (Get-E2eDbUrl $careEnv['CARE_RECIPIENT_DB_URL'] $careDbPort)
        CARE_RECIPIENT_OPENAPI_SERVER_URL = '/api/v1'
    }
    Start-ServiceJar $gatewayDir 'ApiGateway-0.0.1-SNAPSHOT.jar' 'gateway.log' @{}
    Wait-Healthy 'CareRecipientService' 8082
    Wait-Healthy 'ApiGateway' 8080

    Write-Host '== Seed CUSTOMER accounts (e2e DB only)'
    $classpath = (Find-M2Jar 'org\springframework\security\spring-security-crypto' '7.') + ';' +
        (Find-M2Jar 'commons-logging\commons-logging' '1.3')
    $seedSql = Join-Path $logDirectory 'seed.sql'
    $ErrorActionPreference = 'Continue'
    $seed = & java -cp $classpath (Join-Path $PSScriptRoot 'SeedCustomers.java') $customerPassword `
        'e2e.owner@careconnect.test:E2E Owner' 'e2e.relative@careconnect.test:E2E Relative' 'e2e.third@careconnect.test:E2E Third'
    if ($LASTEXITCODE -ne 0) { throw 'SeedCustomers failed.' }
    $ErrorActionPreference = 'Stop'
    [System.IO.File]::WriteAllLines($seedSql, [string[]] $seed)
    Invoke-Psql $identityPg $seedSql

    foreach ($phase in 'a', 'b', 'c') {
        Write-Host "== Phase $phase"
        if ((Invoke-Phase $phase) -ne 0) { $failed += $phase }
    }
    Write-Host '== Phase d (IdentityService stopped)'
    Stop-ByPort 8081
    Start-Sleep -Seconds 2
    if ((Invoke-Phase 'd') -ne 0) { $failed += 'd' }
} finally {
    Write-Host '== Cleanup'
    foreach ($port in 8080, 8081, 8082) { Stop-ByPort $port }
    if (-not $KeepInfra) {
        Invoke-Compose $identityDir $identityProject $identityPorts @('down', '-v')
        Invoke-Compose $careDir $careProject $carePorts @('down', '-v')
    }
    Write-Host "Logs: $logDirectory"
}

if ($failed.Count -gt 0) {
    Write-Host "E2E FAILED phases: $($failed -join ', ')" -ForegroundColor Red
    exit 1
}
Write-Host 'E2E PASSED (a, b, c, d)' -ForegroundColor Green

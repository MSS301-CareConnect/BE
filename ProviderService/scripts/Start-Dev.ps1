#requires -Version 5.1
$ErrorActionPreference = 'Stop'
$serviceDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$envPath = Join-Path $serviceDirectory '.env'
if (-not (Test-Path -LiteralPath $envPath)) { throw 'Copy .env.example to .env and configure local credentials first.' }
foreach ($line in Get-Content -LiteralPath $envPath) {
    $trimmed = $line.Trim()
    if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
    $parts = $trimmed -split '=', 2
    if ($parts.Count -ne 2 -or $parts[0] -notmatch '^[A-Z][A-Z0-9_]*$') { throw 'Invalid .env entry. Use KEY=value without executable expressions.' }
    [Environment]::SetEnvironmentVariable($parts[0], $parts[1].Trim(), 'Process')
}
if ([string]::IsNullOrWhiteSpace($env:PROVIDER_DB_URL) -or -not $env:PROVIDER_DB_URL.StartsWith('jdbc:')) {
    throw 'PROVIDER_DB_URL must be configured and start with jdbc:. Do not wrap the value in quotes.'
}
Push-Location $serviceDirectory
try {
    & .\mvnw.cmd spring-boot:run

    if ($LASTEXITCODE -ne 0) {
        throw 'ProviderService failed to start.'
    }
}
finally {
    Pop-Location
}

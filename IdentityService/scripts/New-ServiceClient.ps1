#requires -Version 5.1
# Sinh secret + BCrypt hash cho một service client (token SERVICE). Ví dụ: .\scripts\New-ServiceClient.ps1 booking
param([Parameter(Mandatory = $true)][string]$ClientId)
$ErrorActionPreference = 'Stop'
$serviceDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$classpathFile = Join-Path ([System.IO.Path]::GetTempPath()) 'careconnect-identity-classpath.txt'
Push-Location $serviceDirectory
try {
    & .\mvnw.cmd -q dependency:build-classpath "-Dmdep.outputFile=$classpathFile" "-Dmdep.includeScope=runtime"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve IdentityService classpath.' }
    $classpath = Get-Content -Raw -LiteralPath $classpathFile
    & java -cp $classpath (Join-Path $PSScriptRoot 'HashServiceSecret.java') $ClientId
    if ($LASTEXITCODE -ne 0) { throw 'HashServiceSecret failed.' }
}
finally {
    Remove-Item -LiteralPath $classpathFile -ErrorAction SilentlyContinue
    Pop-Location
}

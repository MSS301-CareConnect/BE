#requires -Version 5.1
$ErrorActionPreference = 'Stop'
$keyDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../keys'))
$privatePath = Join-Path $keyDirectory 'private.pem'
$publicPath = Join-Path $keyDirectory 'public.pem'
if ((Test-Path -LiteralPath $privatePath) -or (Test-Path -LiteralPath $publicPath)) {
    throw 'Keys already exist. This script never overwrites active signing keys.'
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java 21 must be installed and available on PATH.'
}
# Use the project's JDK: PEM export APIs are unavailable in Windows PowerShell 5.1.
& java (Join-Path $PSScriptRoot 'GenerateDevKeys.java') $keyDirectory
if ($LASTEXITCODE -ne 0) { throw 'Development key generation failed.' }
Write-Output 'Development key pair created in keys/. Keep the private key local.'

<#
.SYNOPSIS
    Starts the Digital Lending Platform development stack.
.DESCRIPTION
    Creates .env from .env.example on first run, then brings the compose stack up
    and waits for every service to report healthy.
.PARAMETER Profile
    Optional compose profile: gateway or monitoring.
#>
[CmdletBinding()]
param(
    [ValidateSet('gateway', 'monitoring')]
    [string[]]$Profile = @()
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $repoRoot

if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    Write-Host 'Created .env from .env.example. Review the credentials before using this on a shared machine.' -ForegroundColor Yellow
}

$composeArgs = @()
foreach ($name in $Profile) { $composeArgs += @('--profile', $name) }
$composeArgs += @('up', '-d', '--build')

Write-Host "docker compose $($composeArgs -join ' ')" -ForegroundColor Cyan
& docker compose @composeArgs
if ($LASTEXITCODE -ne 0) { throw "docker compose failed with exit code $LASTEXITCODE" }

Write-Host ''
Write-Host 'Stack is starting. Once healthy:' -ForegroundColor Green
Write-Host '  Bank portal     http://localhost:3000'
Write-Host '  API             http://localhost:8080/api/v1/platform/health'
Write-Host '  Swagger UI      http://localhost:8080/swagger-ui.html'
Write-Host '  MinIO console   http://localhost:9001'
Write-Host ''
Write-Host 'Follow progress with: docker compose ps'

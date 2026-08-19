<#
.SYNOPSIS
    Stops the development stack.
.PARAMETER RemoveData
    Also deletes the PostgreSQL, Redis and MinIO volumes.
#>
[CmdletBinding()]
param([switch]$RemoveData)

$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..'))

if ($RemoveData) {
    Write-Host 'Stopping the stack and deleting all local data volumes.' -ForegroundColor Yellow
    & docker compose --profile gateway --profile monitoring down -v
} else {
    & docker compose --profile gateway --profile monitoring down
}

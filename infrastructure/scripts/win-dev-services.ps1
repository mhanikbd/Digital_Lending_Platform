<#
.SYNOPSIS
    Starts, stops and inspects the native Windows development services.
.DESCRIPTION
    PostgreSQL and Memurai install as Windows services and start with the
    machine. MinIO ships as a plain executable, so this script runs it as a
    background process with the credentials the backend expects by default.
.PARAMETER Start
    Start MinIO if it is not already running.
.PARAMETER Stop
    Stop the MinIO process started by this script.
.PARAMETER Status
    Report reachability of PostgreSQL, Memurai and MinIO.
#>
[CmdletBinding(DefaultParameterSetName = 'Status')]
param(
    [Parameter(ParameterSetName = 'Start')][switch]$Start,
    [Parameter(ParameterSetName = 'Stop')][switch]$Stop,
    [Parameter(ParameterSetName = 'Status')][switch]$Status
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$minioDataPath = Join-Path $repoRoot '.local\minio-data'
$minioLog = Join-Path $repoRoot '.local\minio.log'

# Matches the defaults in application.yml so the backend needs no environment
# variables locally. Local development values only.
$minioUser = 'dlp_minio_local'
$minioPassword = 'dlp_minio_local_secret'

function Test-Port([string]$computer, [int]$port) {
    try {
        $client = [Net.Sockets.TcpClient]::new()
        $connected = $client.ConnectAsync($computer, $port).Wait(1500)
        $client.Close()
        return $connected
    } catch { return $false }
}

function Find-Minio {
    $onPath = Get-Command minio -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $candidates = @(
        "$env:LOCALAPPDATA\Microsoft\WinGet\Links\minio.exe",
        "$env:ProgramFiles\MinIO\minio.exe"
    ) | Where-Object { Test-Path $_ }

    if ($candidates) { return $candidates[0] }
    throw "minio.exe was not found. Install it with: winget install --id MinIO.Server -e"
}

if ($Start) {
    if (Test-Port 'localhost' 9000) {
        Write-Host 'MinIO is already listening on 9000.' -ForegroundColor DarkGray
    } else {
        $minio = Find-Minio
        if (-not (Test-Path $minioDataPath)) { New-Item -ItemType Directory -Force -Path $minioDataPath | Out-Null }

        $env:MINIO_ROOT_USER = $minioUser
        $env:MINIO_ROOT_PASSWORD = $minioPassword
        Start-Process -FilePath $minio `
            -ArgumentList @('server', $minioDataPath, '--console-address', ':9001') `
            -RedirectStandardOutput $minioLog `
            -RedirectStandardError "$minioLog.err" `
            -WindowStyle Hidden
        Write-Host "Started MinIO. API on 9000, console on 9001, log at $minioLog" -ForegroundColor Green
    }
}

if ($Stop) {
    $processes = Get-Process -Name 'minio' -ErrorAction SilentlyContinue
    if ($processes) {
        $processes | Stop-Process -Force -Confirm:$false
        Write-Host 'Stopped MinIO.' -ForegroundColor Green
    } else {
        Write-Host 'MinIO is not running.' -ForegroundColor DarkGray
    }
}

if ($Status -or $PSCmdlet.ParameterSetName -eq 'Status') {
    Write-Host ''
    Write-Host 'Local development services' -ForegroundColor Cyan

    $checks = @(
        @{ Name = 'PostgreSQL'; Port = 5432; Hint = 'Service "postgresql-x64-17"' },
        @{ Name = 'Memurai (Redis)'; Port = 6379; Hint = 'Service "Memurai"' },
        @{ Name = 'MinIO API'; Port = 9000; Hint = 'Run this script with -Start' },
        @{ Name = 'MinIO console'; Port = 9001; Hint = 'Run this script with -Start' }
    )

    foreach ($check in $checks) {
        $up = Test-Port 'localhost' $check.Port
        $label = if ($up) { 'UP  ' } else { 'DOWN' }
        $colour = if ($up) { 'Green' } else { 'Red' }
        Write-Host ("  {0}  {1,-16} port {2,-5}  {3}" -f $label, $check.Name, $check.Port, $(if ($up) { '' } else { $check.Hint })) -ForegroundColor $colour
    }
    Write-Host ''
}

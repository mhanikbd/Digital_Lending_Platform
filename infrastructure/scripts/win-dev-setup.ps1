<#
.SYNOPSIS
    One-time setup of the native Windows development services.
.DESCRIPTION
    Creates the lending database and its owning role in a locally installed
    PostgreSQL, applies the same cluster settings the Docker path applies, and
    prepares the MinIO data directory.

    This is the alternative to `docker compose up` for developers who do not run
    a Linux container engine. The deployment target is still Linux, and CI still
    runs the Docker path, so nothing here changes how the platform ships.

    Prerequisites, installed from an elevated prompt:
        winget install --id PostgreSQL.PostgreSQL.17 -e
        winget install --id Memurai.MemuraiDeveloper -e
        winget install --id MinIO.Server -e
.PARAMETER PostgresPassword
    Password for the PostgreSQL superuser, chosen during its installation.
    Prompted for if omitted. Used only to create the role and database.
.PARAMETER MinioDataPath
    Directory MinIO stores objects in. Defaults to .local/minio-data in the repo,
    which is gitignored.
#>
[CmdletBinding()]
param(
    [SecureString]$PostgresPassword,
    [string]$MinioDataPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

# Local development values. They match the defaults in application.yml, so the
# backend starts with no environment variables set. They are not secrets and
# must never appear in a shared environment.
$dbName = 'digital_lending'
$dbRole = 'dlp_owner'
$dbRolePassword = 'dlp_local_only'

if (-not $MinioDataPath) { $MinioDataPath = Join-Path $repoRoot '.local\minio-data' }

function Find-Psql {
    $onPath = Get-Command psql -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $candidates = Get-ChildItem "$env:ProgramFiles\PostgreSQL" -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName 'bin\psql.exe' } |
        Where-Object { Test-Path $_ }

    if ($candidates) { return $candidates[0] }
    throw "psql was not found. Install PostgreSQL 17, then re-run this script."
}

$psql = Find-Psql
Write-Host "Using $psql" -ForegroundColor Cyan

if (-not $PostgresPassword) {
    $PostgresPassword = Read-Host -Prompt 'PostgreSQL superuser (postgres) password' -AsSecureString
}
$env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($PostgresPassword))

try {
    # ---- role -------------------------------------------------------------
    $roleExists = & $psql -U postgres -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$dbRole'"
    if ($LASTEXITCODE -ne 0) { throw "Could not connect to PostgreSQL. Check the service is running and the password is correct." }

    if ($roleExists -eq '1') {
        Write-Host "Role $dbRole already exists." -ForegroundColor DarkGray
    } else {
        & $psql -U postgres -d postgres -c "CREATE ROLE $dbRole LOGIN PASSWORD '$dbRolePassword'"
        if ($LASTEXITCODE -ne 0) { throw "Failed to create role $dbRole" }
        Write-Host "Created role $dbRole." -ForegroundColor Green
    }

    # ---- database ---------------------------------------------------------
    $dbExists = & $psql -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$dbName'"
    if ($dbExists -eq '1') {
        Write-Host "Database $dbName already exists." -ForegroundColor DarkGray
    } else {
        # Encoding and collation match POSTGRES_INITDB_ARGS in docker-compose.yml.
        # Ordering must not differ between a developer machine and production.
        & $psql -U postgres -d postgres -c `
            "CREATE DATABASE $dbName OWNER $dbRole ENCODING 'UTF8' LC_COLLATE 'C' LC_CTYPE 'C' TEMPLATE template0"
        if ($LASTEXITCODE -ne 0) { throw "Failed to create database $dbName" }
        Write-Host "Created database $dbName." -ForegroundColor Green
    }

    # ---- cluster settings, the same file Docker runs ----------------------
    $initSql = Join-Path $repoRoot 'infrastructure\postgres\init\01-initialise-database.sql'
    & $psql -U postgres -d $dbName -v ON_ERROR_STOP=1 -f $initSql
    if ($LASTEXITCODE -ne 0) { throw "Failed to apply $initSql" }
    Write-Host "Applied cluster settings from 01-initialise-database.sql." -ForegroundColor Green
}
finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}

# ---- MinIO storage --------------------------------------------------------
if (-not (Test-Path $MinioDataPath)) {
    New-Item -ItemType Directory -Force -Path $MinioDataPath | Out-Null
    Write-Host "Created MinIO data directory $MinioDataPath." -ForegroundColor Green
} else {
    Write-Host "MinIO data directory already present." -ForegroundColor DarkGray
}

Write-Host ''
Write-Host 'Setup complete. Next:' -ForegroundColor Green
Write-Host '  1. Start MinIO      .\infrastructure\scripts\win-dev-services.ps1 -Start'
Write-Host '  2. Check everything .\infrastructure\scripts\win-dev-services.ps1 -Status'
Write-Host '  3. Run the backend  mvn -f backend/digital-lending-api/pom.xml spring-boot:run'
Write-Host '  4. Run the portal   npm --prefix web/bank-portal run dev'

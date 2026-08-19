<#
.SYNOPSIS
    Rebuilds the environment from scratch.
.DESCRIPTION
    Deletes every container and volume, then starts again. Flyway rebuilds the
    schema from the first migration, which is the check that the migration set
    still works on a clean database.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$scripts = $PSScriptRoot

& (Join-Path $scripts 'dev-down.ps1') -RemoveData
& (Join-Path $scripts 'dev-up.ps1')

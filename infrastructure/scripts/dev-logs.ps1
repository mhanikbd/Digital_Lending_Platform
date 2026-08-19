<#
.SYNOPSIS
    Tails logs for one service, or for the whole stack when none is given.
#>
[CmdletBinding()]
param([string]$Service)

$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..'))

if ($Service) { & docker compose logs -f --tail 200 $Service }
else { & docker compose logs -f --tail 200 }

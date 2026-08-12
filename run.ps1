# Root shortcut launcher for AGAP Master Runner
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
& (Join-Path $PSScriptRoot "scripts\run.ps1") @args

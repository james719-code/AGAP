# AGAP Tailscale Windows Configuration Helper Script
# Configures Tailscale Serve or Tailscale Funnel for AGAP on Windows.

param (
    [int]$BackendPort = 8088,
    [switch]$Funnel,
    [switch]$Reset
)

$ErrorActionPreference = "Stop"

Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host " AGAP Emergency Platform - Tailscale Windows Setup" -ForegroundColor Cyan
Write-Host " Target Backend Port: $BackendPort" -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan

# Verify tailscale CLI is installed and available
$tailscalePath = Get-Command tailscale -ErrorAction SilentlyContinue
if (-not $tailscalePath) {
    Write-Host "[X] ERROR: Tailscale CLI 'tailscale.exe' was not found on PATH." -ForegroundColor Red
    Write-Host "    Please install Tailscale for Windows from https://tailscale.com/download" -ForegroundColor Yellow
    exit 1
}

if ($Reset) {
    Write-Host "[*] Resetting active Tailscale serve/funnel configuration..." -ForegroundColor Yellow
    & tailscale serve reset
    & tailscale funnel reset
    Write-Host "[OK] Tailscale configuration reset successfully." -ForegroundColor Green
    exit 0
}

if ($Funnel) {
    Write-Host "[*] Enabling Tailscale Funnel (Public HTTPS Access) for port $BackendPort..." -ForegroundColor Yellow
    & tailscale funnel --bg $BackendPort
    Write-Host "[OK] Tailscale Funnel enabled on HTTPS -> http://127.0.0.1:$BackendPort" -ForegroundColor Green
} else {
    Write-Host "[*] Enabling Tailscale Serve (Private Tailnet Access) for port $BackendPort..." -ForegroundColor Yellow
    & tailscale serve --bg $BackendPort
    Write-Host "[OK] Tailscale Serve enabled for Tailnet -> http://127.0.0.1:$BackendPort" -ForegroundColor Green
}

Write-Host "`nActive Tailscale Status:" -ForegroundColor Cyan
& tailscale serve status

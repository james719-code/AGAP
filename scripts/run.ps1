# AGAP Full Stack Master Runner (PowerShell for Windows)
# Launches Go Backend API (8088), React Frontend (5173), and Tailscale Public Funnel in one step.

param (
    [int]$BackendPort = 8088,
    [int]$FrontendPort = 5173,
    [switch]$SkipTailscale,
    [switch]$Production
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$rootDir = Split-Path -Parent $scriptDir

Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host " AGAP Emergency Platform - Master One-Command Launcher" -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan

# 1. Launch Backend Service
if ($Production) {
    $exePath = Join-Path $rootDir "build\agap-server.exe"
    if (-not (Test-Path $exePath)) {
        Write-Host "[*] Production binary not found. Compiling via deploy.ps1..." -ForegroundColor Yellow
        & (Join-Path $rootDir "deploy\deploy.ps1") -SkipFirewall
    }
    Write-Host "[1/3] Launching Production Backend Binary (agap-server.exe)..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle='AGAP Backend (Port $BackendPort)'; Set-Location '$rootDir\build'; .\agap-server.exe"
} else {
    Write-Host "[1/3] Launching Go Backend API (Port $BackendPort)..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle='AGAP Backend (Port $BackendPort)'; Set-Location '$rootDir\backend'; go run ./cmd/server/main.go"
}

# 2. Launch Frontend Development Server
Write-Host "[2/3] Launching React GIS Dashboard (Port $FrontendPort)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle='AGAP Frontend (Port $FrontendPort)'; Set-Location '$rootDir\frontend'; npm run dev"

# 3. Enable Tailscale Public HTTPS Funnel
if (-not $SkipTailscale) {
    $tailscalePath = Get-Command tailscale -ErrorAction SilentlyContinue
    if ($tailscalePath) {
        Write-Host "[3/3] Enabling Tailscale Public HTTPS Funnel for Port $BackendPort..." -ForegroundColor Magenta
        try {
            & tailscale funnel --bg $BackendPort | Out-Null
            Write-Host "[OK] Tailscale Funnel enabled!" -ForegroundColor Green
        } catch {
            Write-Host "[!] Note: Could not auto-toggle Tailscale Funnel: $_" -ForegroundColor Yellow
        }
    } else {
        Write-Host "[!] Tailscale CLI not installed. Skipping public funnel setup." -ForegroundColor Yellow
    }
}

Write-Host "`n=======================================================" -ForegroundColor Green
Write-Host " AGAP All-in-One Services Initialized!" -ForegroundColor Green
Write-Host " -> Backend API:     http://localhost:$BackendPort" -ForegroundColor White
Write-Host " -> WebSocket Feed:  ws://localhost:$BackendPort/ws" -ForegroundColor White
Write-Host " -> GIS Dashboard:   http://localhost:$FrontendPort" -ForegroundColor White

if (-not $SkipTailscale) {
    $status = & tailscale funnel status 2>$null
    if ($status) {
        Write-Host " -> Public Funnel:   https://setret.tail8010bd.ts.net/info" -ForegroundColor Cyan
    }
}
Write-Host "=======================================================" -ForegroundColor Green

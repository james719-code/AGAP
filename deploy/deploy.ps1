# AGAP Local On-Device Deployment Script (PowerShell for Windows)
# Builds Go Backend binary & React Frontend dashboard into the local build/ directory.

param (
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$SkipFirewall
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$projectDir = Split-Path -Parent $scriptDir
$buildDir = Join-Path $projectDir "build"

Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host " AGAP Emergency Platform - Local Windows Deployment" -ForegroundColor Cyan
Write-Host " Target Directory: $buildDir" -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan

# 1. Create Local Build Directories
$dirsToCreate = @(
    $buildDir,
    (Join-Path $buildDir "static"),
    (Join-Path $buildDir "data"),
    (Join-Path $buildDir "logs")
)

foreach ($dir in $dirsToCreate) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        Write-Host "[+] Created directory: $dir" -ForegroundColor Gray
    }
}

# 2. Build Go Backend Executable
if (-not $FrontendOnly) {
    Write-Host "`n[1/2] Building Go Backend Engine (agap-server.exe)..." -ForegroundColor Yellow
    $backendDir = Join-Path $projectDir "backend"
    $exePath = Join-Path $buildDir "agap-server.exe"

    Push-Location $backendDir
    try {
        $env:CGO_ENABLED = "0"
        go build -ldflags="-s -w" -o $exePath ./cmd/server
        if (Test-Path $exePath) {
            $size = (Get-Item $exePath).Length / 1MB
            Write-Host "[OK] Backend binary successfully built -> $exePath ($('{0:N2}' -f $size) MB)" -ForegroundColor Green
        }
    } finally {
        Pop-Location
    }

    # Copy config.env if not present in build
    $configEnvSrc = Join-Path $scriptDir "config.env"
    $configEnvDst = Join-Path $buildDir "config.env"
    if ((Test-Path $configEnvSrc) -and (-not (Test-Path $configEnvDst))) {
        Copy-Item $configEnvSrc $configEnvDst
        Write-Host "[+] Staged local config.env -> $configEnvDst" -ForegroundColor Gray
    }
}

# 3. Build React Frontend Dashboard
if (-not $BackendOnly) {
    Write-Host "`n[2/2] Building React GIS Frontend Dashboard..." -ForegroundColor Yellow
    $frontendDir = Join-Path $projectDir "frontend"
    $distDir = Join-Path $frontendDir "dist"
    $staticDir = Join-Path $buildDir "static"

    Push-Location $frontendDir
    try {
        if (-not (Test-Path "node_modules")) {
            Write-Host "[*] Installing npm dependencies..." -ForegroundColor Gray
            npm install --silent
        }
        npm run build
        
        if (Test-Path $distDir) {
            if (Test-Path $staticDir) {
                Remove-Item -Recurse -Force $staticDir
            }
            Copy-Item -Recurse -Force $distDir $staticDir
            Write-Host "[OK] Frontend static bundle staged -> $staticDir" -ForegroundColor Green
        }
    } finally {
        Pop-Location
    }
}

# 4. Optional Firewall Configuration
if (-not $SkipFirewall) {
    $firewallScript = Join-Path $scriptDir "setup-firewall.ps1"
    if (Test-Path $firewallScript) {
        Write-Host "`n[*] Configuring Windows Defender Firewall rules..." -ForegroundColor Yellow
        & $firewallScript
    }
}

Write-Host "`n=======================================================" -ForegroundColor Green
Write-Host " AGAP Local Device Deployment Complete!" -ForegroundColor Green
Write-Host " Binary: $buildDir\agap-server.exe" -ForegroundColor White
Write-Host " Static: $buildDir\static\" -ForegroundColor White
Write-Host "=======================================================" -ForegroundColor Green

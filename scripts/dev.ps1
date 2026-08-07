# AGAP Concurrent Development Runner (PowerShell for Windows)
# Starts both the Go Backend API and the React Vite Frontend concurrently.

Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host " AGAP Emergency Platform - Concurrent Dev Server Launch" -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan

$rootDir = Resolve-Path "$PSScriptRoot\.."

Write-Host "[1/2] Launching Go Backend API (Port 8088)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle='AGAP Backend (Port 8088)'; Set-Location '$rootDir\backend'; go run ./cmd/server/main.go"

Write-Host "[2/2] Launching React GIS Dashboard (Port 5173)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle='AGAP Frontend (Port 5173)'; Set-Location '$rootDir\frontend'; npm run dev"

Write-Host ""
Write-Host "AGAP Services Initialized!" -ForegroundColor Green
Write-Host " -> Backend API:   http://localhost:8088" -ForegroundColor White
Write-Host " -> WebSocket:     ws://localhost:8088/ws" -ForegroundColor White
Write-Host " -> GIS Dashboard: http://localhost:5173" -ForegroundColor White

Write-Host ""

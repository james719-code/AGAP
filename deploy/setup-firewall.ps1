# AGAP Windows Defender Firewall Setup Script
# Opens inbound ports for AGAP Backend API (8080, 8088, 8081) and React GIS Dashboard (5173).
# Run this script in PowerShell as Administrator.

param (
    [string]$RuleNamePrefix = "AGAP Emergency Platform",
    [int[]]$Ports = @(8080, 8088, 8081, 5173)
)

$ErrorActionPreference = "Stop"

Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host " AGAP Emergency Platform - Windows Firewall Configuration" -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan

# Check Administrator Privileges
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "[!] WARNING: This script requires Administrator privileges to modify Firewall rules." -ForegroundColor Yellow
    Write-Host "    Please re-run PowerShell as Administrator if rule creation fails.`n" -ForegroundColor Yellow
}

foreach ($port in $Ports) {
    $ruleName = "$RuleNamePrefix - Port $port (TCP-In)"
    $existingRule = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
    
    if ($existingRule) {
        Write-Host "[OK] Firewall rule '$ruleName' already exists." -ForegroundColor Green
    } else {
        try {
            New-NetFirewallRule -DisplayName $ruleName `
                                -Direction Inbound `
                                -LocalPort $port `
                                -Protocol TCP `
                                -Action Allow `
                                -Description "Inbound traffic for AGAP Emergency Platform services on port $port" `
                                -ErrorAction Stop | Out-Null
            Write-Host "[+] Created Firewall rule for Port $port (TCP Inbound)" -ForegroundColor Green
        } catch {
            Write-Host "[X] Failed to create firewall rule for port ${port} (Run as Administrator required)" -ForegroundColor Red
        }
    }
}

Write-Host "`nWindows Firewall Configuration Complete!`n" -ForegroundColor Green

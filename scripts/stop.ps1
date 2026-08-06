# AGAP Process Termination Utility (PowerShell for Windows)
# Finds and stops running AGAP Backend (Port 8080) and Frontend (Port 5173) dev servers.

Write-Host "=======================================================" -ForegroundColor Yellow
Write-Host " AGAP Emergency Platform - Stopping Dev Servers..." -ForegroundColor Yellow
Write-Host "=======================================================" -ForegroundColor Yellow

# Ports used by AGAP stack
$ports = @(8088, 5173)


foreach ($port in $ports) {
    Write-Host "Checking port $port..." -ForegroundColor Gray
    $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($connections) {
        foreach ($conn in $connections) {
            $pidToKill = $conn.OwningProcess
            if ($pidToKill -and $pidToKill -ne 0) {
                try {
                    $proc = Get-Process -Id $pidToKill -ErrorAction SilentlyContinue
                    if ($proc) {
                        Write-Host " -> Stopping process '$($proc.ProcessName)' (PID: $pidToKill) on port $port" -ForegroundColor Red
                        Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
                    }
                } catch {
                    # Ignore process already stopped
                }
            }
        }
    } else {
        Write-Host " -> Port $port is free." -ForegroundColor Green
    }
}

Write-Host "`nAGAP Dev Servers stopped successfully.`n" -ForegroundColor Green

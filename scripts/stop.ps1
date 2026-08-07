# AGAP Process Termination Utility (PowerShell for Windows)
# Finds and stops running AGAP Backend (Port 8088) and Frontend (Port 5173) dev servers,
# closing their host CMD/PowerShell windows.

Write-Host "=======================================================" -ForegroundColor Yellow
Write-Host " AGAP Emergency Platform - Stopping Dev Servers..." -ForegroundColor Yellow
Write-Host "=======================================================" -ForegroundColor Yellow

# Ports used by AGAP stack
$ports = @(8088, 5173)

# 1. Stop processes listening on ports and their host PowerShell/CMD processes
foreach ($port in $ports) {
    Write-Host "Checking port $port..." -ForegroundColor Gray
    $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($connections) {
        foreach ($conn in $connections) {
            $pidToKill = $conn.OwningProcess
            if ($pidToKill -and $pidToKill -ne 0) {
                # Find host window processes (powershell, pwsh, cmd, conhost) in the process tree
                $pidsToStop = [System.Collections.Generic.List[int]]::new()
                $pidsToStop.Add($pidToKill)

                $currPid = $pidToKill
                while ($currPid -and $currPid -gt 0) {
                    $procInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $currPid" -ErrorAction SilentlyContinue
                    if ($procInfo -and $procInfo.ParentProcessId -and $procInfo.ParentProcessId -ne $currPid -and $procInfo.ParentProcessId -ne $PID) {
                        $parentId = [int]$procInfo.ParentProcessId
                        $parentProc = Get-Process -Id $parentId -ErrorAction SilentlyContinue
                        if ($parentProc) {
                            $pName = $parentProc.ProcessName.ToLower()
                            if ($pName -in @('powershell', 'pwsh', 'cmd', 'conhost')) {
                                if (-not $pidsToStop.Contains($parentId)) {
                                    $pidsToStop.Add($parentId)
                                }
                            }
                        }
                        $currPid = $parentId
                    } else {
                        break
                    }
                }

                foreach ($id in $pidsToStop) {
                    try {
                        $p = Get-Process -Id $id -ErrorAction SilentlyContinue
                        if ($p) {
                            Write-Host " -> Stopping process '$($p.ProcessName)' (PID: $id) on port $port" -ForegroundColor Red
                            Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
                        }
                    } catch {}
                }
            }
        }
    } else {
        Write-Host " -> Port $port is free." -ForegroundColor Green
    }
}

# 2. Close any remaining PowerShell/CMD windows titled with AGAP
$agapWindows = Get-Process | Where-Object { $_.MainWindowTitle -like "*AGAP *" -or $_.MainWindowTitle -like "AGAP *" }
foreach ($win in $agapWindows) {
    if ($win.Id -ne $PID) {
        Write-Host " -> Closing window '$($win.MainWindowTitle)' (PID: $($win.Id))" -ForegroundColor Red
        Stop-Process -Id $win.Id -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "`nAGAP Dev Servers and CMD windows stopped successfully.`n" -ForegroundColor Green

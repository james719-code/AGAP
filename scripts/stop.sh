#!/usr/bin/env bash
# AGAP Process Termination Utility (Bash for Linux/macOS)
# Finds and stops running AGAP Backend (Port 8080) and Frontend (Port 5173) dev servers.

echo "======================================================="
echo " AGAP Emergency Platform - Stopping Dev Servers..."
echo "======================================================="

PORTS=(8080 5173)

for PORT in "${PORTS[@]}"; do
    PID=$(lsof -ti :$PORT 2>/dev/null || fuser $PORT/tcp 2>/dev/null || true)
    if [ -n "$PID" ]; then
        echo " -> Terminating process PID $PID running on port $PORT..."
        kill -9 $PID 2>/dev/null || true
    else
        echo " -> Port $PORT is free."
    fi
done

echo -e "\nAGAP Dev Servers stopped successfully.\n"

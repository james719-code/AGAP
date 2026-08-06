#!/usr/bin/env bash
# AGAP Concurrent Development Runner (Bash for Linux/macOS)
# Starts both the Go Backend API and the React Vite Frontend concurrently.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "======================================================="
echo " AGAP Emergency Platform — Concurrent Dev Server Launch"
echo "======================================================="

cleanup() {
    echo -e "\nShutting down AGAP dev servers..."
    kill 0
}
trap cleanup EXIT INT TERM

echo "[1/2] Launching Go Backend API (Port 8088)..."
(cd "$ROOT_DIR/backend" && go run ./cmd/server/main.go) &

echo "[2/2] Launching React GIS Dashboard (Port 5173)..."
(cd "$ROOT_DIR/frontend" && npm run dev) &

echo -e "\nAGAP Services Initialized!"
echo " -> Backend API:   http://localhost:8088"
echo " -> WebSocket:     ws://localhost:8088/ws"
echo " -> GIS Dashboard: http://localhost:5173\n"


wait

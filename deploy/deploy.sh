#!/usr/bin/env bash
# =============================================================================
# AGAP (Accelerated Geolocation-based Assistance and Protection) — Deploy Script
# =============================================================================
# Builds the Go backend (Linux static binary) + React frontend, then SCPs
# everything to the Debian server and restarts the systemd service.
#
# Usage:
#   ./deploy/deploy.sh                 # full build + deploy
#   ./deploy/deploy.sh --backend-only  # build & deploy backend only
#   ./deploy/deploy.sh --frontend-only # build & deploy frontend only
#   ./deploy/deploy.sh --build-only    # just build, don't deploy
#   ./deploy/deploy.sh --deploy-only   # skip build, just SCP + restart
#   ./deploy/deploy.sh --restart       # just restart remote service
#
# Requires:
#   - Go toolchain (cross-compilation)
#   - Node.js + npm (frontend build)
#   - ssh + scp (Git Bash on Windows, or native Linux/macOS)
# =============================================================================

set -euo pipefail

# ── Resolve script directory ────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_DIR/build"

# ── Load deploy config ──────────────────────────────────────────────────────
CONFIG_FILE="$SCRIPT_DIR/deploy.conf"
if [ -f "$CONFIG_FILE" ]; then
    # shellcheck source=/dev/null
    source "$CONFIG_FILE"
else
    echo "ERROR: deploy/deploy.conf not found."
    echo "  Copy the template and fill in your server details:"
    echo "    cp deploy/deploy.conf.example deploy/deploy.conf"
    exit 1
fi

# ── Defaults ────────────────────────────────────────────────────────────────
DEPLOY_HOST="${DEPLOY_HOST:-}"
DEPLOY_USER="${DEPLOY_USER:-agap}"
DEPLOY_PORT="${DEPLOY_PORT:-22}"
REMOTE_DIR="${REMOTE_DIR:-/opt/agap}"
SSH_KEY="${SSH_KEY:-}"

# Custom path configurations (fallback to standard subdirs inside REMOTE_DIR if not set)
BACKEND_DIR="${BACKEND_DIR:-$REMOTE_DIR}"
FRONTEND_DIR="${FRONTEND_DIR:-$REMOTE_DIR/static}"
DATABASE_DIR="${DATABASE_DIR:-$REMOTE_DIR/data}"
LOG_DIR="${LOG_DIR:-$REMOTE_DIR/logs}"

# Service name
SERVICE_NAME="agap-backend"

# If BUILD_DIR was overridden with a relative path, make it absolute
if [[ "$BUILD_DIR" != /* && "$BUILD_DIR" != [A-Za-z]:* ]]; then
    BUILD_DIR="$PROJECT_DIR/$BUILD_DIR"
fi

SSH_CMD="ssh -p $DEPLOY_PORT"
SCP_CMD="scp -P $DEPLOY_PORT"
if [ -n "$SSH_KEY" ]; then
    SSH_CMD="$SSH_CMD -i $SSH_KEY"
    SCP_CMD="$SCP_CMD -i $SSH_KEY"
fi
SSH_TARGET="${DEPLOY_USER}@${DEPLOY_HOST}"

# ── Colors ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── Parse flags ─────────────────────────────────────────────────────────────
DO_BUILD_BACKEND=true
DO_BUILD_FRONTEND=true
DO_DEPLOY_BACKEND=true
DO_DEPLOY_FRONTEND=true
DO_RESTART=true

case "${1:-}" in
    --backend-only)  DO_BUILD_FRONTEND=false; DO_DEPLOY_FRONTEND=false ;;
    --frontend-only) DO_BUILD_BACKEND=false;  DO_DEPLOY_BACKEND=false; DO_RESTART=false ;;
    --build-only)    DO_DEPLOY_BACKEND=false; DO_DEPLOY_FRONTEND=false; DO_RESTART=false ;;
    --deploy-only)   DO_BUILD_BACKEND=false;  DO_BUILD_FRONTEND=false ;;
    --restart)       DO_BUILD_BACKEND=false;  DO_BUILD_FRONTEND=false; DO_DEPLOY_BACKEND=false; DO_DEPLOY_FRONTEND=false ;;
    --help|-h)
        echo "Usage: deploy.sh [--backend-only|--frontend-only|--build-only|--deploy-only|--restart]"
        echo ""
        echo "  (no flag)        Full build + SCP deploy + restart service"
        echo "  --backend-only   Build & deploy Go backend only"
        echo "  --frontend-only  Build & deploy React frontend only"
        echo "  --build-only     Build backend + frontend locally, skip deploy"
        echo "  --deploy-only    SCP current build/ to server, restart service"
        echo "  --restart        Only restart the remote systemd service"
        echo ""
        echo "Configure: cp deploy/deploy.conf.example deploy/deploy.conf"
        exit 0
        ;;
esac

# ── Detect Local Target ──────────────────────────────────────────────────────
IS_LOCAL=false
if [ "$DEPLOY_HOST" = "127.0.0.1" ] || [ "$DEPLOY_HOST" = "localhost" ]; then
    IS_LOCAL=true
fi

# ── Validate ────────────────────────────────────────────────────────────────
if [ "$DO_DEPLOY_BACKEND" = true ] || [ "$DO_DEPLOY_FRONTEND" = true ] || [ "$DO_RESTART" = true ]; then
    if [ -z "$DEPLOY_HOST" ]; then
        error "DEPLOY_HOST is not set in deploy/deploy.conf"
    fi
    if [ "$IS_LOCAL" = false ]; then
        # Quick connectivity check for remote servers
        if ! $SSH_CMD -o ConnectTimeout=5 -o BatchMode=yes "$SSH_TARGET" "echo ok" &>/dev/null; then
            warn "Cannot connect to $SSH_TARGET (will retry during deploy)"
        fi
    fi
fi

echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  AGAP Engine — Deploy${NC}"
echo -e "${GREEN}  Target: ${DEPLOY_HOST} (Local Device: ${IS_LOCAL})${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo ""

# ╔════════════════════════════════════════════════════════════════════════════╗
# ║  STEP 1: Build Go Backend                                                 ║
# ╚════════════════════════════════════════════════════════════════════════════╝
if [ "$DO_BUILD_BACKEND" = true ]; then
    mkdir -p "$BUILD_DIR"
    cd "$PROJECT_DIR/backend"

    if [ "$IS_LOCAL" = true ]; then
        info "Building Go backend engine for local host..."
        CGO_ENABLED=0 go build -ldflags="-s -w" -o "$BUILD_DIR/server" ./cmd/server
    else
        info "Building Go backend engine (linux/amd64, CGO_ENABLED=0)..."
        CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
            go build -ldflags="-s -w" -o "$BUILD_DIR/server" ./cmd/server
    fi

    success "Backend built  →  build/server"
else
    info "Skipping backend build"
fi

# ╔════════════════════════════════════════════════════════════════════════════╗
# ║  STEP 2: Build React Frontend                                             ║
# ╚════════════════════════════════════════════════════════════════════════════╝
if [ "$DO_BUILD_FRONTEND" = true ]; then
    info "Building React frontend dashboard..."

    cd "$PROJECT_DIR/frontend"

    if [ ! -d "node_modules" ]; then
        info "Installing npm dependencies..."
        npm install --silent
    fi

    npm run build

    # Move dist into build/static
    rm -rf "$BUILD_DIR/static"
    cp -r dist "$BUILD_DIR/static"

    success "Frontend built →  build/static/"
else
    info "Skipping frontend build"
fi

# ╔════════════════════════════════════════════════════════════════════════════╗
# ║  STEP 3: Stage / Deploy Assets                                             ║
# ╚════════════════════════════════════════════════════════════════════════════╝
if [ "$DO_DEPLOY_BACKEND" = true ] || [ "$DO_DEPLOY_FRONTEND" = true ]; then
    if [ "$IS_LOCAL" = true ]; then
        info "Staging build assets locally for device deployment..."
        mkdir -p "$BUILD_DIR/static" "$BUILD_DIR/data" "$BUILD_DIR/logs"
        if [ -f "$SCRIPT_DIR/config.env" ] && [ ! -f "$BUILD_DIR/config.env" ]; then
            cp "$SCRIPT_DIR/config.env" "$BUILD_DIR/config.env"
        fi
        success "Local staging complete in $BUILD_DIR/"
    else
        info "Deploying to $SSH_TARGET..."

        # Ensure remote directory structure exists
        $SSH_CMD "$SSH_TARGET" "mkdir -p $BACKEND_DIR/uploads $FRONTEND_DIR $DATABASE_DIR $LOG_DIR 2>/dev/null || (sudo mkdir -p $BACKEND_DIR/uploads $FRONTEND_DIR $DATABASE_DIR $LOG_DIR && sudo chown -R $DEPLOY_USER $REMOTE_DIR)"

        # Upload server binary
        if [ "$DO_DEPLOY_BACKEND" = true ]; then
            info "  Uploading AGAP backend binary..."
            $SCP_CMD "$BUILD_DIR/server" "$SSH_TARGET:/tmp/agap-server.new"
            $SSH_CMD "$SSH_TARGET" "sudo mv /tmp/agap-server.new $BACKEND_DIR/server && sudo chmod +x $BACKEND_DIR/server"
            success "  AGAP backend binary deployed"

            # Upload deploy config if it doesn't exist on remote
            if ! $SSH_CMD "$SSH_TARGET" "test -f $BACKEND_DIR/config.env" 2>/dev/null; then
                info "  Uploading initial config.env..."
                sed -e "s|DB_FILE_PATH=.*|DB_FILE_PATH=$DATABASE_DIR/agap_gis.db|g" \
                    "$SCRIPT_DIR/config.env" > "$SCRIPT_DIR/config.env.tmp"
                $SCP_CMD "$SCRIPT_DIR/config.env.tmp" "$SSH_TARGET:/tmp/agap-config.env"
                $SSH_CMD "$SSH_TARGET" "sudo mv /tmp/agap-config.env $BACKEND_DIR/config.env"
                rm -f "$SCRIPT_DIR/config.env.tmp"
                success "  config.env uploaded (edit on server: $BACKEND_DIR/config.env)"
            fi
        fi

        # Upload static assets
        if [ "$DO_DEPLOY_FRONTEND" = true ] && [ -d "$BUILD_DIR/static" ]; then
            info "  Uploading frontend static assets..."
            $SSH_CMD "$SSH_TARGET" "mkdir -p /tmp/agap-static-build"
            $SCP_CMD -r "$BUILD_DIR/static/"* "$SSH_TARGET:/tmp/agap-static-build/"
            $SSH_CMD "$SSH_TARGET" "sudo mkdir -p $FRONTEND_DIR && sudo cp -r /tmp/agap-static-build/* $FRONTEND_DIR/ && rm -rf /tmp/agap-static-build"
            success "  Frontend static assets deployed"
        fi

        success "Deploy complete."
    fi
else
    info "Skipping deploy step"
fi

# ╔════════════════════════════════════════════════════════════════════════════╗
# ║  STEP 4: Restart service                                                  ║
# ╚════════════════════════════════════════════════════════════════════════════╝
if [ "$DO_RESTART" = true ]; then
    info "Restarting $SERVICE_NAME service..."

    $SSH_CMD "$SSH_TARGET" "sudo systemctl restart $SERVICE_NAME" || {
        warn "systemctl restart failed — service might not be installed yet."
        warn "Run deploy/setup-server.sh first for initial server setup."
        exit 1
    }

    # Ensure Tailscale Funnel is running if installed
    $SSH_CMD "$SSH_TARGET" "
        if command -v tailscale &>/dev/null && systemctl list-unit-files | grep -q tailscale-funnel.service; then
            sudo systemctl restart tailscale-funnel.service || true
        fi
    "

    # Wait a moment then check status
    sleep 2
    if $SSH_CMD "$SSH_TARGET" "systemctl is-active --quiet $SERVICE_NAME" 2>/dev/null; then
        success "Service $SERVICE_NAME is running."
    else
        warn "Service $SERVICE_NAME may not be running. Check logs:"
        echo ""
        $SSH_CMD "$SSH_TARGET" "sudo journalctl -u $SERVICE_NAME --no-pager -n 20" || true
    fi
else
    info "Skipping service restart"
fi

echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  Done.${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"

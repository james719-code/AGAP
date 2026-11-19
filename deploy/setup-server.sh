#!/usr/bin/env bash
# =============================================================================
# AGAP (Accelerated Geolocation-based Assistance and Protection) — Server Setup
# =============================================================================
# Provisions a fresh Debian server with everything needed to run AGAP.
# Run this ONCE per server from your dev laptop.
#
# Usage:
#   ./deploy/setup-server.sh
#
# What it does:
#   1. Creates system user (or sets up home directory permissions)
#   2. Creates directory structure (BACKEND_DIR, FRONTEND_DIR, DATABASE_DIR, LOG_DIR)
#   3. Installs nginx (if not present) + stages the reverse proxy & WebSockets
#      (enable nginx and public Funnel only when explicitly configured)
#   4. Installs the systemd service (agap-backend.service)
#   5. Uploads initial config.env
#   6. Enables & starts the service
# =============================================================================

set -euo pipefail

# ── Resolve script directory ────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

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
NGINX_ACTIVATE="${NGINX_ACTIVATE:-false}"
TAILSCALE_FUNNEL_ENABLE="${TAILSCALE_FUNNEL_ENABLE:-false}"

# Custom path configurations (fallback to standard subdirs inside REMOTE_DIR if not set)
BACKEND_DIR="${BACKEND_DIR:-$REMOTE_DIR}"
FRONTEND_DIR="${FRONTEND_DIR:-$REMOTE_DIR/static}"
DATABASE_DIR="${DATABASE_DIR:-$REMOTE_DIR/data}"
LOG_DIR="${LOG_DIR:-$REMOTE_DIR/logs}"

# Determine service running user (if deploying inside home directory, run as DEPLOY_USER)
SERVICE_USER="agap"
PROTECT_HOME="yes"
if [[ "$BACKEND_DIR" == /home/* ]]; then
    SERVICE_USER="$DEPLOY_USER"
    PROTECT_HOME="no"
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

echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  AGAP Engine — First-Time Server Setup${NC}"
echo -e "${GREEN}  Target: ${SSH_TARGET}${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo ""

# ── Validate ────────────────────────────────────────────────────────────────
if [ -z "$DEPLOY_HOST" ]; then
    error "DEPLOY_HOST is not set in deploy/deploy.conf"
fi
if [[ "$NGINX_ACTIVATE" != true && "$NGINX_ACTIVATE" != false ]]; then
    error "NGINX_ACTIVATE must be true or false"
fi
if [[ "$TAILSCALE_FUNNEL_ENABLE" != true && "$TAILSCALE_FUNNEL_ENABLE" != false ]]; then
    error "TAILSCALE_FUNNEL_ENABLE must be true or false"
fi
if [[ "$TAILSCALE_FUNNEL_ENABLE" == true && "$NGINX_ACTIVATE" != true ]]; then
    error "Activate the AGAP nginx site before enabling its public Funnel"
fi

info "Checking connectivity..."
if ! $SSH_CMD -o ConnectTimeout=5 -o BatchMode=yes "$SSH_TARGET" "echo ok" &>/dev/null; then
    warn "Cannot connect as $DEPLOY_USER@$DEPLOY_HOST"
    warn "Make sure your SSH key is authorized on the server."
    exit 1
fi
success "Connected to $SSH_TARGET"

# ╔════════════════════════════════════════════════════════════════════════════╗
# ║  STEP 1: Create system user                                               ║
# ╚════════════════════════════════════════════════════════════════════════════╝
echo ""
info "Step 1/6: Setting up service user ($SERVICE_USER)..."

if [ "$SERVICE_USER" = "agap" ]; then
    $SSH_CMD "$SSH_TARGET" "
        if ! id -u agap &>/dev/null; then
            sudo useradd --system --no-create-home --shell /usr/sbin/nologin agap
            echo '  Created system user: agap'
        else
            echo '  User agap already exists'
        fi
    " || error "Failed to create system user"
else
    info "Using existing user '$SERVICE_USER' for systemd service (home directory deploy)"
fi

success "System user ready"

# ╔════════════════════════════════════════════════════════════════════════════╗
# ║  STEP 2: Create directory structure                                       ║
# ╚════════════════════════════════════════════════════════════════════════════╝
echo ""
info "Step 2/6: Creating directory structure..."

$SSH_CMD "$SSH_TARGET" "
    mkdir -p $BACKEND_DIR/uploads $FRONTEND_DIR $DATABASE_DIR $LOG_DIR
    chmod -R 755 $BACKEND_DIR $FRONTEND_DIR $DATABASE_DIR $LOG_DIR
    echo '  Directories ready:'
    echo '    Backend:  $BACKEND_DIR'
    echo '    Frontend: $FRONTEND_DIR'
    echo '    Database: $DATABASE_DIR'
    echo '    Logs:     $LOG_DIR'
" || error "Failed to create directories"

success "Directory structure ready"

# ╔════════════════════════════════════════════════════════════════════════════╗
# ║  STEP 3: Install & configure nginx                                        ║
# ╚════════════════════════════════════════════════════════════════════════════╝
echo ""
info "Step 3/6: Configuring nginx..."

sed -e "s|root /opt/agap/static;|root $FRONTEND_DIR;|g" \
    "$SCRIPT_DIR/nginx.conf" > "$SCRIPT_DIR/nginx.conf.tmp"

$SCP_CMD "$SCRIPT_DIR/nginx.conf.tmp" "$SSH_TARGET:/tmp/agap-nginx.conf"
rm -f "$SCRIPT_DIR/nginx.conf.tmp"

$SSH_CMD "$SSH_TARGET" "
    if ! command -v nginx &>/dev/null; then
        echo '  Installing nginx...'
        sudo apt-get update -qq && sudo apt-get install -y -qq nginx || exit 1
    else
        echo '  nginx already installed'
    fi

    sudo mv /tmp/agap-nginx.conf /etc/nginx/sites-available/agap || exit 1

    if [ '$NGINX_ACTIVATE' = true ]; then
        # Keep existing sites intact. Remove only a symlink created here if validation fails.
        created_link=false
        if [ ! -e /etc/nginx/sites-enabled/agap ] && [ ! -L /etc/nginx/sites-enabled/agap ]; then
            sudo ln -s /etc/nginx/sites-available/agap /etc/nginx/sites-enabled/agap || exit 1
            created_link=true
        fi
        if ! sudo nginx -t; then
            if [ \"\$created_link\" = true ]; then sudo rm /etc/nginx/sites-enabled/agap; fi
            exit 1
        fi
        sudo systemctl reload nginx || exit 1
        echo '  AGAP nginx site enabled and reloaded'
    else
        echo '  AGAP nginx site staged; existing site links and routing left unchanged'
        echo '  Activate it with your preferred reverse proxy configuration when ready.'
    fi
" || error "Failed to configure nginx"

success "nginx ready"

# ╔════════════════════════════════════════════════════════════════════════════╗
# ║  STEP 4: Install systemd service                                          ║
# ╚════════════════════════════════════════════════════════════════════════════╝
echo ""
info "Step 4/6: Installing systemd service (agap-backend)..."

sed -e "s|User=agap|User=$SERVICE_USER|g" \
    -e "s|WorkingDirectory=/opt/agap|WorkingDirectory=$BACKEND_DIR|g" \
    -e "s|EnvironmentFile=/opt/agap/config.env|EnvironmentFile=$BACKEND_DIR/config.env|g" \
    -e "s|ExecStart=/opt/agap/server|ExecStart=$BACKEND_DIR/server|g" \
    -e "s|StandardOutput=append:/opt/agap/logs/agap-backend.log|StandardOutput=append:$LOG_DIR/agap-backend.log|g" \
    -e "s|StandardError=append:/opt/agap/logs/agap-backend.err|StandardError=append:$LOG_DIR/agap-backend.err|g" \
    -e "s|ProtectHome=yes|ProtectHome=$PROTECT_HOME|g" \
    -e "s|ReadWritePaths=/opt/agap/data /opt/agap/uploads /opt/agap/logs|ReadWritePaths=$DATABASE_DIR $BACKEND_DIR/uploads $LOG_DIR|g" \
    -e "s|ReadOnlyPaths=/opt/agap/static|ReadOnlyPaths=$FRONTEND_DIR|g" \
    "$SCRIPT_DIR/agap-backend.service" > "$SCRIPT_DIR/agap-backend.service.tmp"

$SCP_CMD "$SCRIPT_DIR/agap-backend.service.tmp" "$SSH_TARGET:/tmp/agap-backend.service"
rm -f "$SCRIPT_DIR/agap-backend.service.tmp"
if [ "$TAILSCALE_FUNNEL_ENABLE" = true ]; then
    $SCP_CMD "$SCRIPT_DIR/tailscale-funnel.service" "$SSH_TARGET:/tmp/tailscale-funnel.service"
fi

$SSH_CMD "$SSH_TARGET" "
    sudo mv /tmp/agap-backend.service /etc/systemd/system/agap-backend.service || exit 1
    if [ '$TAILSCALE_FUNNEL_ENABLE' = true ]; then
        sudo mv /tmp/tailscale-funnel.service /etc/systemd/system/tailscale-funnel.service || exit 1
    fi
    sudo systemctl daemon-reload || exit 1
    echo '  systemd services installed'
" || error "Failed to install systemd services"

success "systemd services ready"

# ╔════════════════════════════════════════════════════════════════════════════╗
# ║  STEP 5: Upload config.env                                                ║
# ╚════════════════════════════════════════════════════════════════════════════╝
echo ""
info "Step 5/6: Uploading config.env..."

sed -e "s|DB_FILE_PATH=.*|DB_FILE_PATH=$DATABASE_DIR/agap_gis.db|g" \
    "$SCRIPT_DIR/config.env" > "$SCRIPT_DIR/config.env.tmp"
$SCP_CMD "$SCRIPT_DIR/config.env.tmp" "$SSH_TARGET:/tmp/agap-config.env"
rm -f "$SCRIPT_DIR/config.env.tmp"

$SSH_CMD "$SSH_TARGET" "
    if [ -f $BACKEND_DIR/config.env ]; then
        echo '  WARNING: config.env already exists — keeping existing server config.'
    else
        sudo mv /tmp/agap-config.env $BACKEND_DIR/config.env
        sudo chown $SERVICE_USER $BACKEND_DIR/config.env
        sudo chmod 600 $BACKEND_DIR/config.env
        echo '  config.env installed.'
    fi
" || error "Failed to upload config.env"

success "config.env ready"

# ╔════════════════════════════════════════════════════════════════════════════╗
# ║  STEP 6: Start services                                                   ║
# ╚════════════════════════════════════════════════════════════════════════════╝
echo ""
info "Step 6/6: Enabling & starting services..."

$SSH_CMD "$SSH_TARGET" "
    sudo systemctl enable agap-backend
    sudo systemctl start agap-backend || true

    if [ '$TAILSCALE_FUNNEL_ENABLE' = true ] && command -v tailscale &>/dev/null; then
        echo '  Enabling Tailscale Funnel persistent service...'
        sudo systemctl enable tailscale-funnel || true
        sudo systemctl restart tailscale-funnel || true
    fi

    sleep 1
    sudo systemctl status agap-backend --no-pager || true
" || warn "Service may not be running yet (run deploy.sh next)"

echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  AGAP Server setup complete!${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "Next steps:"
echo -e "  1. ${BLUE}Deploy the application:${NC}"
echo -e "     ./deploy/deploy.sh"
echo ""
echo -e "  2. ${BLUE}Verify:${NC}"
echo -e "     Open https://$DEPLOY_HOST/api/v1/health or https://$DEPLOY_HOST"
echo ""

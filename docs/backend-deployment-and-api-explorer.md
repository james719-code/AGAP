# AGAP Backend Deployment & Interactive API Explorer Guide

This document provides a reference for developers building, deploying, and monitoring the AGAP Go Telemetry & Dispatch Backend Engine.

---

## 1. Quick Links

- [Backend Root Directory](file:///backend/)
- [Main Entry Point (`cmd/server/main.go`)](file:///backend/cmd/server/main.go)
- [HTTP Handlers & HTML UI (`internal/handler`)](file:///backend/internal/handler/)
- [Deployment Automations (`deploy/deploy.sh`)](file:///deploy/deploy.sh)
- [Server Setup Script (`deploy/setup-server.sh`)](file:///deploy/setup-server.sh)
- [Systemd Service Unit (`deploy/agap-backend.service`)](file:///deploy/agap-backend.service)
- [Nginx Reverse Proxy Config (`deploy/nginx.conf`)](file:///deploy/nginx.conf)
- [Production Environment Template (`deploy/config.env`)](file:///deploy/config.env)

---

## 2. Interactive Web Explorer & System Endpoints

The Go backend embeds a web-based dashboard accessible directly in any web browser:

### 🌟 Quick Launch Links (Local & Production)
- **Local Interactive Dashboard**: [http://localhost:8080/info](http://localhost:8080/info)
- **Production Tailscale Dashboard**: [https://homeserver.tail8010bd.ts.net/info](https://homeserver.tail8010bd.ts.net/info)
- **Local System Info JSON**: [http://localhost:8080/api/v1/info](http://localhost:8080/api/v1/info)
- **Local Health Probe**: [http://localhost:8080/health](http://localhost:8080/health)
- **Local DB Tables List API**: [http://localhost:8080/api/v1/system/db/tables](http://localhost:8080/api/v1/system/db/tables)

| Feature | Local Link | Production Link | Format | Description |
|---|---|---|---|---|
| **Web Dashboard UI** | [http://localhost:8080/info](http://localhost:8080/info) | [https://homeserver.tail8010bd.ts.net/info](https://homeserver.tail8010bd.ts.net/info) | HTML | Interactive UI for monitoring status, browsing DB tables, and testing APIs |
| **System Info API** | [http://localhost:8080/api/v1/info](http://localhost:8080/api/v1/info) | [https://homeserver.tail8010bd.ts.net/api/v1/info](https://homeserver.tail8010bd.ts.net/api/v1/info) | JSON | Returns version, uptime, rate limits, and table row counts |
| **Liveness Health Probe** | [http://localhost:8080/health](http://localhost:8080/health) | [https://homeserver.tail8010bd.ts.net/health](https://homeserver.tail8010bd.ts.net/health) | JSON | Service readiness & DB ping probe (`{"status":"ok","db":"ok"}`) |
| **DB Tables List** | [http://localhost:8080/api/v1/system/db/tables](http://localhost:8080/api/v1/system/db/tables) | [https://homeserver.tail8010bd.ts.net/api/v1/system/db/tables](https://homeserver.tail8010bd.ts.net/api/v1/system/db/tables) | JSON | Returns list of database tables and row counts |
| **DB Table Data** | [http://localhost:8080/api/v1/system/db/tables/users](http://localhost:8080/api/v1/system/db/tables/users) | [https://homeserver.tail8010bd.ts.net/api/v1/system/db/tables/users](https://homeserver.tail8010bd.ts.net/api/v1/system/db/tables/users) | JSON | Returns up to 100 rows per table with password hashes masked |
| **WebSocket Feed** | `ws://localhost:8080/ws` | `wss://homeserver.tail8010bd.ts.net/ws` | WS | Real-time emergency telemetry stream |

---

## 3. Production Deployment Step-by-Step

### Step 1: Copy Deployment Configuration
Copy the configuration template to [deploy/deploy.conf](file:///deploy/deploy.conf):

```bash
cp deploy/deploy.conf.example deploy/deploy.conf
```

Edit `deploy/deploy.conf` to set your target server details:
```ini
DEPLOY_HOST=your-server-domain-or-ip.com
DEPLOY_USER=agap
DEPLOY_PORT=22
```

### Step 2: Provision Server (First Time Only)
Run the automated provisioner script to install Nginx, set up `/opt/agap` directories, and install the systemd service:

```bash
./deploy/setup-server.sh
```

### Step 3: Deploy Backend Binary
Cross-compile the static Linux executable locally and deploy to your remote server:

```bash
./deploy/deploy.sh --backend-only
```

### Step 4: Enable Tailscale HTTPS (Optional)
To expose Nginx over your private Tailscale network with automatic HTTPS certificates:

```bash
sudo tailscale serve --bg 80
```

Verify your active Tailscale serve configuration:
```bash
sudo tailscale serve status
```

---

## 4. Key Server Maintenance Commands

| Action | Command on Server |
|---|---|
| **View Systemd Status** | `sudo systemctl status agap-backend` |
| **Restart Service** | `sudo systemctl restart agap-backend` |
| **Tail Live Logs** | `sudo journalctl -u agap-backend.service -f` |
| **Reload Nginx Proxy** | `sudo systemctl reload nginx` |
| **Check Tailscale Status** | `sudo tailscale serve status` |

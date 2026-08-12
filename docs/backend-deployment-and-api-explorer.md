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

### 🌟 Quick Launch Links (Local On-Device)
- **Local Interactive Dashboard**: [http://localhost:8088/info](http://localhost:8088/info) (or `http://localhost:8080/info`)
- **Local System Info JSON**: [http://localhost:8088/api/v1/info](http://localhost:8088/api/v1/info)
- **Local Health Probe**: [http://localhost:8088/health](http://localhost:8088/health)
- **Local DB Tables List API**: [http://localhost:8088/api/v1/system/db/tables](http://localhost:8088/api/v1/system/db/tables)

| Feature | Local Dev Link (8088) | Local Engine Link (8080) | Format | Description |
|---|---|---|---|---|
| **Web Dashboard UI** | [http://localhost:8088/info](http://localhost:8088/info) | [http://localhost:8080/info](http://localhost:8080/info) | HTML | Interactive UI for monitoring status, browsing DB tables, and testing APIs |
| **System Info API** | [http://localhost:8088/api/v1/info](http://localhost:8088/api/v1/info) | [http://localhost:8080/api/v1/info](http://localhost:8080/api/v1/info) | JSON | Returns version, uptime, rate limits, and table row counts |
| **Liveness Health Probe** | [http://localhost:8088/health](http://localhost:8088/health) | [http://localhost:8080/health](http://localhost:8080/health) | JSON | Service readiness & DB ping probe (`{"status":"ok","db":"ok"}`) |
| **DB Tables List** | [http://localhost:8088/api/v1/system/db/tables](http://localhost:8088/api/v1/system/db/tables) | [http://localhost:8080/api/v1/system/db/tables](http://localhost:8080/api/v1/system/db/tables) | JSON | Returns list of database tables and row counts |
| **DB Table Data** | [http://localhost:8088/api/v1/system/db/tables/users](http://localhost:8088/api/v1/system/db/tables/users) | [http://localhost:8080/api/v1/system/db/tables/users](http://localhost:8080/api/v1/system/db/tables/users) | JSON | Returns up to 100 rows per table with password hashes masked |
| **WebSocket Feed** | `ws://localhost:8088/ws` | `ws://localhost:8080/ws` | WS | Real-time emergency telemetry stream |

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

### Step 4: Enable Tailscale Funnel (Public HTTPS Access)
To expose Nginx to the public internet with automatic managed TLS/SSL certificates:

1. Enable Tailscale Funnel in background mode on public HTTPS port 443:
```bash
sudo tailscale funnel --bg 443 http://127.0.0.1:80
```

2. (Recommended) Install the persistent systemd service so Tailscale Funnel stays active after server reboots:
```bash
sudo cp deploy/tailscale-funnel.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now tailscale-funnel.service
```

3. If Tailscale Funnel gets stuck or reports a phantom configuration, perform a clean reset:
```bash
sudo tailscale funnel reset
sudo tailscale serve reset
```

4. Verify your active Tailscale Funnel status:
```bash
sudo tailscale funnel status
```

---

## 4. Key Server Maintenance Commands

| Action | Command on Server |
|---|---|
| **View Systemd Status** | `sudo systemctl status agap-backend` |
| **Restart Service** | `sudo systemctl restart agap-backend` |
| **Tail Live Logs** | `sudo journalctl -u agap-backend.service -f` |
| **Reload Nginx Proxy** | `sudo systemctl reload nginx` |
| **Check Tailscale Funnel** | `sudo systemctl status tailscale-serve.service` |
| **View Tailscale Routes** | `sudo tailscale serve status` |

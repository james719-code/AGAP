package handler

import (
	"fmt"
	"net/http"
)

const systemDashboardHTML = `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AGAP Engine — API & Database Explorer</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-dark: #0b0f19;
            --bg-card: #131b2e;
            --bg-card-hover: #1b2640;
            --border-color: #1e2d4a;
            --text-main: #f1f5f9;
            --text-muted: #94a3b8;
            --accent-primary: #38bdf8;
            --accent-glow: rgba(56, 189, 248, 0.15);
            --accent-success: #10b981;
            --accent-warning: #f59e0b;
            --accent-danger: #ef4444;
            --font-main: 'Inter', system-ui, -apple-system, sans-serif;
            --font-mono: 'JetBrains Mono', monospace;
        }

        * { box-sizing: border-box; margin: 0; padding: 0; }

        body {
            font-family: var(--font-main);
            background-color: var(--bg-dark);
            color: var(--text-main);
            line-height: 1.5;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
        }

        header {
            background: linear-gradient(180deg, rgba(19, 27, 46, 0.9) 0%, rgba(11, 15, 25, 0.8) 100%);
            backdrop-filter: blur(12px);
            border-bottom: 1px solid var(--border-color);
            position: sticky;
            top: 0;
            z-index: 100;
            padding: 1rem 2rem;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .logo-group {
            display: flex;
            align-items: center;
            gap: 0.75rem;
        }

        .logo-badge {
            background: linear-gradient(135deg, #0284c7, #2563eb);
            color: #fff;
            font-weight: 700;
            font-size: 0.9rem;
            padding: 0.35rem 0.65rem;
            border-radius: 6px;
            letter-spacing: 0.5px;
            box-shadow: 0 0 15px rgba(37, 99, 235, 0.4);
        }

        .header-title {
            font-size: 1.2rem;
            font-weight: 600;
            color: var(--text-main);
        }

        .header-status {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            font-size: 0.85rem;
            font-weight: 500;
            padding: 0.35rem 0.85rem;
            border-radius: 20px;
            background: rgba(16, 185, 129, 0.1);
            border: 1px solid rgba(16, 185, 129, 0.3);
            color: var(--accent-success);
        }

        .status-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background-color: var(--accent-success);
            box-shadow: 0 0 8px var(--accent-success);
            animation: pulse 2s infinite;
        }

        @keyframes pulse {
            0% { opacity: 0.6; }
            50% { opacity: 1; }
            100% { opacity: 0.6; }
        }

        .nav-tabs {
            display: flex;
            gap: 0.5rem;
            background: rgba(19, 27, 46, 0.5);
            padding: 0.25rem;
            border-radius: 8px;
            border: 1px solid var(--border-color);
        }

        .nav-tab {
            background: transparent;
            border: none;
            color: var(--text-muted);
            padding: 0.5rem 1.25rem;
            font-size: 0.875rem;
            font-weight: 500;
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .nav-tab:hover {
            color: var(--text-main);
            background: rgba(255, 255, 255, 0.05);
        }

        .nav-tab.active {
            color: var(--accent-primary);
            background: var(--bg-card);
            border: 1px solid var(--border-color);
            box-shadow: 0 2px 8px rgba(0,0,0,0.2);
        }

        main {
            flex: 1;
            padding: 2rem;
            max-width: 1400px;
            margin: 0 auto;
            width: 100%;
        }

        .tab-content { display: none; }
        .tab-content.active { display: block; }

        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
            gap: 1.25rem;
            margin-bottom: 2rem;
        }

        .metric-card {
            background: var(--bg-card);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 1.25rem;
            transition: transform 0.2s ease, border-color 0.2s ease;
            position: relative;
            overflow: hidden;
        }

        .metric-card::before {
            content: '';
            position: absolute;
            top: 0; left: 0; width: 100%; height: 3px;
            background: linear-gradient(90deg, var(--accent-primary), transparent);
            opacity: 0.7;
        }

        .metric-card:hover {
            transform: translateY(-2px);
            border-color: var(--accent-primary);
        }

        .metric-label {
            font-size: 0.75rem;
            text-transform: uppercase;
            letter-spacing: 0.8px;
            color: var(--text-muted);
            margin-bottom: 0.5rem;
        }

        .metric-value {
            font-size: 1.5rem;
            font-weight: 700;
            color: var(--text-main);
            font-family: var(--font-mono);
        }

        .metric-sub {
            font-size: 0.8rem;
            color: var(--text-muted);
            margin-top: 0.35rem;
        }

        .panel {
            background: var(--bg-card);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 1.5rem;
            margin-bottom: 2rem;
        }

        .panel-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 1.25rem;
            padding-bottom: 0.75rem;
            border-bottom: 1px solid var(--border-color);
        }

        .panel-title {
            font-size: 1.1rem;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }

        .controls-row {
            display: flex;
            gap: 1rem;
            align-items: center;
            flex-wrap: wrap;
            margin-bottom: 1rem;
        }

        select, input, button {
            font-family: var(--font-main);
            font-size: 0.875rem;
        }

        select, input {
            background: var(--bg-dark);
            border: 1px solid var(--border-color);
            color: var(--text-main);
            padding: 0.6rem 1rem;
            border-radius: 8px;
            outline: none;
            transition: border-color 0.2s;
        }

        select:focus, input:focus {
            border-color: var(--accent-primary);
            box-shadow: 0 0 0 2px var(--accent-glow);
        }

        .btn {
            background: var(--accent-primary);
            color: #0b0f19;
            font-weight: 600;
            border: none;
            padding: 0.6rem 1.2rem;
            border-radius: 8px;
            cursor: pointer;
            transition: all 0.2s ease;
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
        }

        .btn:hover {
            opacity: 0.9;
            transform: translateY(-1px);
        }

        .btn-outline {
            background: transparent;
            border: 1px solid var(--border-color);
            color: var(--text-main);
        }

        .btn-outline:hover {
            background: rgba(255, 255, 255, 0.05);
            border-color: var(--accent-primary);
            color: var(--accent-primary);
        }

        .table-wrapper {
            overflow-x: auto;
            border-radius: 8px;
            border: 1px solid var(--border-color);
        }

        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 0.875rem;
            text-align: left;
        }

        th {
            background: rgba(11, 15, 25, 0.7);
            color: var(--text-muted);
            font-weight: 600;
            padding: 0.85rem 1rem;
            border-bottom: 1px solid var(--border-color);
            white-space: nowrap;
        }

        td {
            padding: 0.85rem 1rem;
            border-bottom: 1px solid var(--border-color);
            color: var(--text-main);
            font-family: var(--font-mono);
            font-size: 0.82rem;
            max-width: 300px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        tr:hover td {
            background: var(--bg-card-hover);
        }

        .tag {
            display: inline-block;
            padding: 0.2rem 0.5rem;
            border-radius: 4px;
            font-size: 0.75rem;
            font-weight: 600;
            font-family: var(--font-main);
        }

        .tag-get { background: rgba(56, 189, 248, 0.15); color: #38bdf8; border: 1px solid rgba(56, 189, 248, 0.3); }
        .tag-post { background: rgba(16, 185, 129, 0.15); color: #10b981; border: 1px solid rgba(16, 185, 129, 0.3); }
        .tag-put { background: rgba(245, 158, 11, 0.15); color: #f59e0b; border: 1px solid rgba(245, 158, 11, 0.3); }
        .tag-delete { background: rgba(239, 68, 68, 0.15); color: #ef4444; border: 1px solid rgba(239, 68, 68, 0.3); }

        .json-view {
            background: var(--bg-dark);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 1rem;
            font-family: var(--font-mono);
            font-size: 0.85rem;
            color: #38bdf8;
            max-height: 400px;
            overflow-y: auto;
            white-space: pre-wrap;
        }

        .spinner {
            display: inline-block;
            width: 18px;
            height: 18px;
            border: 2px solid rgba(255,255,255,0.3);
            border-radius: 50%;
            border-top-color: #fff;
            animation: spin 0.8s linear infinite;
        }

        @keyframes spin {
            to { transform: rotate(360deg); }
        }

        .empty-state {
            padding: 3rem;
            text-align: center;
            color: var(--text-muted);
        }
    </style>
</head>
<body>

    <header>
        <div class="logo-group">
            <div class="logo-badge">AGAP</div>
            <div class="header-title">Backend API & Database Explorer</div>
        </div>

        <nav class="nav-tabs">
            <button class="nav-tab active" onclick="switchTab(event, 'overview')">System Overview</button>
            <button class="nav-tab" onclick="switchTab(event, 'database')">Database Viewer</button>
            <button class="nav-tab" onclick="switchTab(event, 'endpoints')">API Catalog</button>
        </nav>

        <div class="header-status" id="headerStatus">
            <span class="status-dot"></span>
            <span id="statusText">System Operational</span>
        </div>
    </header>

    <main>
        <!-- Tab 1: Overview -->
        <div id="tab-overview" class="tab-content active">
            <div class="metrics-grid">
                <div class="metric-card">
                    <div class="metric-label">Server Status</div>
                    <div class="metric-value" id="mStatus" style="color: var(--accent-success)">ONLINE</div>
                    <div class="metric-sub" id="mEnv">Env: loading...</div>
                </div>

                <div class="metric-card">
                    <div class="metric-label">System Uptime</div>
                    <div class="metric-value" id="mUptime">0s</div>
                    <div class="metric-sub" id="mTime">UTC: --:--:--</div>
                </div>

                <div class="metric-card">
                    <div class="metric-label">Database Status</div>
                    <div class="metric-value" id="mDbStatus" style="color: var(--accent-success)">OK</div>
                    <div class="metric-sub" id="mDbType">Type: sqlite</div>
                </div>

                <div class="metric-card">
                    <div class="metric-label">Rate Limiter</div>
                    <div class="metric-value" id="mRateLimit">20 RPS</div>
                    <div class="metric-sub" id="mBurst">Burst: 40</div>
                </div>
            </div>

            <div class="panel">
                <div class="panel-header">
                    <div class="panel-title">📊 Live Database Table Record Counts</div>
                    <button class="btn btn-outline" onclick="fetchInfo()">🔄 Refresh Stats</button>
                </div>
                <div class="metrics-grid" id="tableStatsGrid">
                    <div class="empty-state"><div class="spinner"></div> Loading table statistics...</div>
                </div>
            </div>

            <div class="panel">
                <div class="panel-header">
                    <div class="panel-title">⚡ System Info JSON Output</div>
                </div>
                <pre class="json-view" id="rawJson">Loading JSON metadata...</pre>
            </div>
        </div>

        <!-- Tab 2: Database Explorer -->
        <div id="tab-database" class="tab-content">
            <div class="panel">
                <div class="panel-header">
                    <div class="panel-title">🗄️ Interactive Database Table Browser</div>
                </div>

                <div class="controls-row">
                    <label for="tableSelect" style="font-weight: 500;">Select Table:</label>
                    <select id="tableSelect" onchange="loadTableData(this.value)" style="min-width: 220px;">
                        <option value="">-- Choose a table --</option>
                    </select>

                    <input type="text" id="searchInput" placeholder="Filter rows..." onkeyup="filterRows()" style="min-width: 250px;">

                    <button class="btn btn-outline" onclick="reloadCurrentTable()">🔄 Reload Table</button>
                    <span id="rowCountBadge" style="font-size: 0.85rem; color: var(--text-muted); margin-left: auto;"></span>
                </div>

                <div class="table-wrapper" id="tableContainer">
                    <div class="empty-state">Select a database table above to view records.</div>
                </div>
            </div>
        </div>

        <!-- Tab 3: API Catalog -->
        <div id="tab-endpoints" class="tab-content">
            <div class="panel">
                <div class="panel-header">
                    <div class="panel-title">🚀 Core API Endpoints Registry</div>
                </div>

                <div class="table-wrapper">
                    <table>
                        <thead>
                            <tr>
                                <th>Method</th>
                                <th>Endpoint Path</th>
                                <th>Access</th>
                                <th>Description</th>
                                <th>Test Endpoint</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td><span class="tag tag-get">GET</span></td>
                                <td><code>/health</code></td>
                                <td>Public</td>
                                <td>Service health & DB liveness probe</td>
                                <td><a href="/health" target="_blank" class="btn btn-outline" style="padding: 0.25rem 0.6rem; font-size: 0.75rem;">Probe GET</a></td>
                            </tr>
                            <tr>
                                <td><span class="tag tag-get">GET</span></td>
                                <td><code>/api/v1/info</code></td>
                                <td>Public</td>
                                <td>Comprehensive API & DB statistics JSON</td>
                                <td><a href="/api/v1/info" target="_blank" class="btn btn-outline" style="padding: 0.25rem 0.6rem; font-size: 0.75rem;">View JSON</a></td>
                            </tr>
                            <tr>
                                <td><span class="tag tag-get">GET</span></td>
                                <td><code>/ws</code></td>
                                <td>Public</td>
                                <td>Real-time WebSocket event feed</td>
                                <td><span style="color: var(--text-muted); font-size: 0.8rem;">WebSocket Upgrade</span></td>
                            </tr>
                            <tr>
                                <td><span class="tag tag-post">POST</span></td>
                                <td><code>/api/v1/auth/login</code></td>
                                <td>Public</td>
                                <td>Authenticate user & issue JWT Bearer Token</td>
                                <td><span style="color: var(--text-muted); font-size: 0.8rem;">Requires Auth Body</span></td>
                            </tr>
                            <tr>
                                <td><span class="tag tag-post">POST</span></td>
                                <td><code>/api/v1/telemetry/uplink</code></td>
                                <td>JWT + HMAC</td>
                                <td>Inbound emergency packet uplink ingestion</td>
                                <td><span style="color: var(--text-muted); font-size: 0.8rem;">Requires X-AGAP-Signature</span></td>
                            </tr>
                            <tr>
                                <td><span class="tag tag-get">GET</span></td>
                                <td><code>/api/v1/incidents</code></td>
                                <td>JWT</td>
                                <td>List emergency incidents with type & status filters</td>
                                <td><a href="/api/v1/incidents" target="_blank" class="btn btn-outline" style="padding: 0.25rem 0.6rem; font-size: 0.75rem;">Fetch API</a></td>
                            </tr>
                            <tr>
                                <td><span class="tag tag-get">GET</span></td>
                                <td><code>/api/v1/agencies</code></td>
                                <td>JWT</td>
                                <td>Dispatch agencies registry</td>
                                <td><a href="/api/v1/agencies" target="_blank" class="btn btn-outline" style="padding: 0.25rem 0.6rem; font-size: 0.75rem;">Fetch API</a></td>
                            </tr>
                            <tr>
                                <td><span class="tag tag-get">GET</span></td>
                                <td><code>/api/v1/responders</code></td>
                                <td>JWT</td>
                                <td>Responder units & live GPS tracking registry</td>
                                <td><a href="/api/v1/responders" target="_blank" class="btn btn-outline" style="padding: 0.25rem 0.6rem; font-size: 0.75rem;">Fetch API</a></td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </main>

    <script>
        var currentRawData = null;
        var activeTableName = '';
        var tableRowsData = [];
        var currentColumns = [];

        function switchTab(evt, tabId) {
            var tabs = document.querySelectorAll('.tab-content');
            for (var i = 0; i < tabs.length; i++) {
                tabs[i].classList.remove('active');
            }
            var btns = document.querySelectorAll('.nav-tab');
            for (var i = 0; i < btns.length; i++) {
                btns[i].classList.remove('active');
            }

            document.getElementById('tab-' + tabId).classList.add('active');
            evt.target.classList.add('active');

            if (tabId === 'database' && document.getElementById('tableSelect').options.length <= 1) {
                loadTableDropdown();
            }
        }

        async function fetchInfo() {
            try {
                var res = await fetch('/api/v1/info');
                if (!res.ok) throw new Error('API status ' + res.status);
                var data = await res.json();
                currentRawData = data;

                document.getElementById('mStatus').innerText = (data.status || 'OK').toUpperCase();
                document.getElementById('mEnv').innerText = 'Env: ' + (data.api && data.api.environment ? data.api.environment : 'development');
                document.getElementById('mUptime').innerText = (data.api && data.api.uptime ? data.api.uptime : '0s');
                document.getElementById('mTime').innerText = 'Server Time: ' + (data.api && data.api.server_time ? data.api.server_time : '');
                document.getElementById('mDbStatus').innerText = (data.database && data.database.status ? data.database.status : 'OK').toUpperCase();
                document.getElementById('mDbType').innerText = 'Type: ' + (data.database && data.database.type ? data.database.type : 'sqlite') + ' (' + (data.database && data.database.file_path ? data.database.file_path : '') + ')';
                document.getElementById('mRateLimit').innerText = (data.api && data.api.rate_limiting && data.api.rate_limiting.rps ? data.api.rate_limiting.rps : 20) + ' RPS';
                document.getElementById('mBurst').innerText = 'Burst: ' + (data.api && data.api.rate_limiting && data.api.rate_limiting.burst ? data.api.rate_limiting.burst : 40);

                document.getElementById('rawJson').innerText = JSON.stringify(data, null, 2);

                var stats = (data.database && data.database.statistics) ? data.database.statistics : {};
                var statsGrid = document.getElementById('tableStatsGrid');
                statsGrid.innerHTML = '';

                Object.keys(stats).forEach(function(table) {
                    var card = document.createElement('div');
                    card.className = 'metric-card';
                    card.innerHTML = '<div class="metric-label">Table: ' + table + '</div>' +
                                     '<div class="metric-value">' + stats[table] + '</div>' +
                                     '<div class="metric-sub">Total Records</div>';
                    statsGrid.appendChild(card);
                });

            } catch (err) {
                document.getElementById('mStatus').innerText = 'DEGRADED';
                document.getElementById('mStatus').style.color = 'var(--accent-danger)';
                document.getElementById('headerStatus').style.borderColor = 'rgba(239,68,68,0.3)';
                document.getElementById('statusText').innerText = 'Status Alert: ' + err.message;
            }
        }

        async function loadTableDropdown() {
            try {
                var res = await fetch('/api/v1/system/db/tables');
                if (!res.ok) throw new Error('Failed to fetch table list');
                var data = await res.json();

                var select = document.getElementById('tableSelect');
                select.innerHTML = '<option value="">-- Select Database Table --</option>';

                (data.tables || []).forEach(function(t) {
                    var opt = document.createElement('option');
                    opt.value = t.name;
                    opt.innerText = t.name + ' (' + t.count + ' rows)';
                    select.appendChild(opt);
                });
            } catch (err) {
                console.error('Error loading tables:', err);
            }
        }

        async function loadTableData(tableName) {
            if (!tableName) return;
            activeTableName = tableName;
            var container = document.getElementById('tableContainer');
            container.innerHTML = '<div class="empty-state"><div class="spinner"></div> Loading records from table ' + tableName + '...</div>';

            try {
                var res = await fetch('/api/v1/system/db/tables/' + encodeURIComponent(tableName));
                if (!res.ok) throw new Error('HTTP ' + res.status);
                var data = await res.json();

                tableRowsData = data.rows || [];
                currentColumns = data.columns || [];
                document.getElementById('rowCountBadge').innerText = 'Total Rows: ' + (data.total !== undefined ? data.total : tableRowsData.length);

                if (tableRowsData.length === 0) {
                    container.innerHTML = '<div class="empty-state">No records found in table ' + tableName + '.</div>';
                    return;
                }

                renderTable(currentColumns, tableRowsData);

            } catch (err) {
                container.innerHTML = '<div class="empty-state" style="color: var(--accent-danger);">Error fetching table data: ' + err.message + '</div>';
            }
        }

        function reloadCurrentTable() {
            if (activeTableName) loadTableData(activeTableName);
        }

        function renderTable(columns, rows) {
            var container = document.getElementById('tableContainer');
            var html = '<table><thead><tr>';
            columns.forEach(function(col) { html += '<th>' + col + '</th>'; });
            html += '</tr></thead><tbody>';

            rows.forEach(function(row) {
                html += '<tr>';
                columns.forEach(function(col) {
                    var val = row[col];
                    if (val === null || val === undefined) val = '<span style="color: var(--text-muted);">NULL</span>';
                    else if (typeof val === 'object') val = JSON.stringify(val);
                    var strVal = String(val).replace(/"/g, '&quot;');
                    html += '<td title="' + strVal + '">' + val + '</td>';
                });
                html += '</tr>';
            });

            html += '</tbody></table>';
            container.innerHTML = html;
        }

        function filterRows() {
            var query = document.getElementById('searchInput').value.toLowerCase();
            var trs = document.querySelectorAll('#tableContainer tbody tr');
            trs.forEach(function(tr) {
                var text = tr.innerText.toLowerCase();
                tr.style.display = text.indexOf(query) !== -1 ? '' : 'none';
            });
        }

        window.addEventListener('DOMContentLoaded', function() {
            fetchInfo();
        });
    </script>
</body>
</html>`

func (h *HealthHandler) DashboardUI(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = fmt.Fprint(w, systemDashboardHTML)
}

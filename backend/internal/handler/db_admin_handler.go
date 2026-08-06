package handler

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"html/template"
	"net/http"
	"strings"
)

type DBAdminHandler struct {
	db *sql.DB
}

func NewDBAdminHandler(db *sql.DB) *DBAdminHandler {
	return &DBAdminHandler{db: db}
}

type ColumnInfo struct {
	Cid       int     `json:"cid"`
	Name      string  `json:"name"`
	Type      string  `json:"type"`
	NotNull   int     `json:"notnull"`
	DfltValue *string `json:"dflt_value"`
	Pk        int     `json:"pk"`
}

type TableSummary struct {
	Name     string       `json:"name"`
	RowCount int          `json:"row_count"`
	Columns  []ColumnInfo `json:"columns"`
}

type APIEndpointDoc struct {
	Method      string `json:"method"`
	Path        string `json:"path"`
	Auth        string `json:"auth"`
	Description string `json:"description"`
}

var backendEndpoints = []APIEndpointDoc{
	{Method: "GET", Path: "/health", Auth: "Public", Description: "System health status and SQLite database connection ping"},
	{Method: "GET", Path: "/ws", Auth: "Public / Token", Description: "Real-time WebSocket connection for live telemetry stream"},
	{Method: "GET", Path: "/admin/db", Auth: "Public", Description: "Live SQLite Database Explorer, Schema Manager & API Documentation Web UI"},
	{Method: "POST", Path: "/admin/db/row/insert", Auth: "Public", Description: "CRUD: Insert new record into specified SQLite table"},
	{Method: "POST", Path: "/admin/db/row/update", Auth: "Public", Description: "CRUD: Update existing record in specified SQLite table by Primary Key"},
	{Method: "POST", Path: "/admin/db/row/delete", Auth: "Public", Description: "CRUD: Delete record from specified SQLite table by Primary Key"},
	{Method: "POST", Path: "/admin/db/schema/create-table", Auth: "Public", Description: "DDL: Create a new custom table in SQLite database"},
	{Method: "POST", Path: "/admin/db/schema/add-column", Auth: "Public", Description: "DDL: Add a new column/attribute to an existing SQLite table"},
	{Method: "POST", Path: "/admin/db/schema/drop-table", Auth: "Public", Description: "DDL: Drop an existing table from SQLite database"},
	{Method: "GET", Path: "/api/v1/admin/db/tables", Auth: "Public", Description: "JSON API returning all database tables, schemas, and row counts"},
	{Method: "POST", Path: "/api/v1/auth/login", Auth: "Public", Description: "Authenticate user credentials and generate JWT token"},
	{Method: "POST", Path: "/api/v1/auth/register", Auth: "Public", Description: "Register new user account (auto-assigns device_id if omitted)"},
	{Method: "PUT", Path: "/api/v1/admin/users/verify", Auth: "Admin Approval", Description: "Approve and verify registered user account (is_verified = 1)"},
	{Method: "POST", Path: "/api/v1/telemetry/uplink", Auth: "JWT + HMAC", Description: "Uplink single or mesh-relayed distress telemetry packet"},
	{Method: "GET", Path: "/api/v1/incidents", Auth: "JWT Required", Description: "List distress incidents filtered by status, region, or type"},
	{Method: "PUT", Path: "/api/v1/incidents/{id}/status", Auth: "JWT Required", Description: "Update incident status (ACTIVE, DISPATCHED, RESOLVED)"},
	{Method: "GET", Path: "/api/v1/agencies", Auth: "JWT Required", Description: "List registered emergency dispatch agencies"},
	{Method: "POST", Path: "/api/v1/agencies", Auth: "JWT Required", Description: "Create new emergency agency registry entry"},
	{Method: "GET", Path: "/api/v1/responders", Auth: "JWT Required", Description: "List responder units and live GPS coordinates"},
	{Method: "POST", Path: "/api/v1/responders", Auth: "JWT Required", Description: "Register new responder unit"},
	{Method: "PUT", Path: "/api/v1/responders/{id}/location", Auth: "JWT Required", Description: "Update responder unit real-time GPS coordinates"},
}

func (h *DBAdminHandler) GetTables(w http.ResponseWriter, r *http.Request) {
	rows, err := h.db.QueryContext(r.Context(), "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")
	if err != nil {
		http.Error(w, `{"error":"failed to query tables"}`, http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var tables []TableSummary
	for rows.Next() {
		var name string
		if err := rows.Scan(&name); err != nil {
			continue
		}

		colRows, err := h.db.QueryContext(r.Context(), fmt.Sprintf("PRAGMA table_info('%s')", name))
		var cols []ColumnInfo
		if err == nil {
			for colRows.Next() {
				var c ColumnInfo
				_ = colRows.Scan(&c.Cid, &c.Name, &c.Type, &c.NotNull, &c.DfltValue, &c.Pk)
				cols = append(cols, c)
			}
			colRows.Close()
		}

		var count int
		_ = h.db.QueryRowContext(r.Context(), fmt.Sprintf("SELECT COUNT(*) FROM '%s'", name)).Scan(&count)

		tables = append(tables, TableSummary{
			Name:     name,
			RowCount: count,
			Columns:  cols,
		})
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"tables":    tables,
		"endpoints": backendEndpoints,
	})
}

// InsertRow handles adding a new row to a table
func (h *DBAdminHandler) InsertRow(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form payload", http.StatusBadRequest)
		return
	}

	tableName := r.FormValue("table_name")
	if tableName == "" {
		http.Error(w, "table_name is required", http.StatusBadRequest)
		return
	}

	var cols []string
	var placeholders []string
	var values []interface{}

	for key, vals := range r.Form {
		if key == "table_name" || len(vals) == 0 || vals[0] == "" {
			continue
		}
		cols = append(cols, key)
		placeholders = append(placeholders, "?")
		values = append(values, vals[0])
	}

	if len(cols) == 0 {
		http.Redirect(w, r, fmt.Sprintf("/admin/db?view=database&table=%s&error=No+data+provided", tableName), http.StatusSeeOther)
		return
	}

	query := fmt.Sprintf("INSERT INTO '%s' (%s) VALUES (%s)", tableName, strings.Join(cols, ", "), strings.Join(placeholders, ", "))
	_, err := h.db.ExecContext(r.Context(), query, values...)
	if err != nil {
		http.Redirect(w, r, fmt.Sprintf("/admin/db?view=database&table=%s&error=%s", tableName, template.URLQueryEscaper(err.Error())), http.StatusSeeOther)
		return
	}

	http.Redirect(w, r, fmt.Sprintf("/admin/db?view=database&table=%s&msg=Record+inserted+successfully", tableName), http.StatusSeeOther)
}

// UpdateRow handles updating an existing row by Primary Key
func (h *DBAdminHandler) UpdateRow(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form payload", http.StatusBadRequest)
		return
	}

	tableName := r.FormValue("table_name")
	pkCol := r.FormValue("pk_col")
	pkVal := r.FormValue("pk_val")

	if tableName == "" || pkCol == "" || pkVal == "" {
		http.Redirect(w, r, "/admin/db?view=database&error=table_name,+pk_col,+and+pk_val+required", http.StatusSeeOther)
		return
	}

	var sets []string
	var values []interface{}

	for key, vals := range r.Form {
		if key == "table_name" || key == "pk_col" || key == "pk_val" || len(vals) == 0 {
			continue
		}
		sets = append(sets, fmt.Sprintf("'%s' = ?", key))
		if vals[0] == "NULL" {
			values = append(values, nil)
		} else {
			values = append(values, vals[0])
		}
	}

	if len(sets) == 0 {
		http.Redirect(w, r, fmt.Sprintf("/admin/db?view=database&table=%s&error=No+fields+to+update", tableName), http.StatusSeeOther)
		return
	}

	values = append(values, pkVal)
	query := fmt.Sprintf("UPDATE '%s' SET %s WHERE '%s' = ?", tableName, strings.Join(sets, ", "), pkCol)

	_, err := h.db.ExecContext(r.Context(), query, values...)
	if err != nil {
		http.Redirect(w, r, fmt.Sprintf("/admin/db?view=database&table=%s&error=%s", tableName, template.URLQueryEscaper(err.Error())), http.StatusSeeOther)
		return
	}

	http.Redirect(w, r, fmt.Sprintf("/admin/db?view=database&table=%s&msg=Record+updated+successfully", tableName), http.StatusSeeOther)
}

// DeleteRow handles deleting a row by Primary Key
func (h *DBAdminHandler) DeleteRow(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form payload", http.StatusBadRequest)
		return
	}

	tableName := r.FormValue("table_name")
	pkCol := r.FormValue("pk_col")
	pkVal := r.FormValue("pk_val")

	if tableName == "" || pkCol == "" || pkVal == "" {
		http.Error(w, "table_name, pk_col, and pk_val are required", http.StatusBadRequest)
		return
	}

	query := fmt.Sprintf("DELETE FROM '%s' WHERE '%s' = ?", tableName, pkCol)
	_, err := h.db.ExecContext(r.Context(), query, pkVal)
	if err != nil {
		http.Redirect(w, r, fmt.Sprintf("/admin/db?view=database&table=%s&error=%s", tableName, template.URLQueryEscaper(err.Error())), http.StatusSeeOther)
		return
	}

	http.Redirect(w, r, fmt.Sprintf("/admin/db?view=database&table=%s&msg=Record+deleted+successfully", tableName), http.StatusSeeOther)
}

// CreateTable handles DDL CREATE TABLE
func (h *DBAdminHandler) CreateTable(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form payload", http.StatusBadRequest)
		return
	}

	tableName := strings.TrimSpace(r.FormValue("table_name"))
	columnDefs := strings.TrimSpace(r.FormValue("column_defs"))

	if tableName == "" || columnDefs == "" {
		http.Redirect(w, r, "/admin/db?view=database&error=Table+name+and+column+definitions+are+required", http.StatusSeeOther)
		return
	}

	query := fmt.Sprintf("CREATE TABLE IF NOT EXISTS '%s' (%s)", tableName, columnDefs)
	_, err := h.db.ExecContext(r.Context(), query)
	if err != nil {
		http.Redirect(w, r, fmt.Sprintf("/admin/db?view=database&error=%s", template.URLQueryEscaper(err.Error())), http.StatusSeeOther)
		return
	}

	http.Redirect(w, r, fmt.Sprintf("/admin/db?view=database&table=%s&msg=Table+'%s'+created+successfully", tableName, tableName), http.StatusSeeOther)
}

// AddColumn handles DDL ALTER TABLE ADD COLUMN
func (h *DBAdminHandler) AddColumn(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form payload", http.StatusBadRequest)
		return
	}

	tableName := strings.TrimSpace(r.FormValue("table_name"))
	colName := strings.TrimSpace(r.FormValue("col_name"))
	colType := strings.TrimSpace(r.FormValue("col_type"))

	if tableName == "" || colName == "" || colType == "" {
		http.Redirect(w, r, fmt.Sprintf("/admin/db?view=database&table=%s&error=Column+name+and+type+required", tableName), http.StatusSeeOther)
		return
	}

	query := fmt.Sprintf("ALTER TABLE '%s' ADD COLUMN '%s' %s", tableName, colName, colType)
	_, err := h.db.ExecContext(r.Context(), query)
	if err != nil {
		http.Redirect(w, r, fmt.Sprintf("/admin/db?view=database&table=%s&error=%s", tableName, template.URLQueryEscaper(err.Error())), http.StatusSeeOther)
		return
	}

	http.Redirect(w, r, fmt.Sprintf("/admin/db?view=database&table=%s&msg=Attribute+'%s'+added+successfully", tableName, colName), http.StatusSeeOther)
}

// DropTable handles DDL DROP TABLE
func (h *DBAdminHandler) DropTable(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form payload", http.StatusBadRequest)
		return
	}

	tableName := strings.TrimSpace(r.FormValue("table_name"))
	if tableName == "" {
		http.Redirect(w, r, "/admin/db?view=database&error=Table+name+required", http.StatusSeeOther)
		return
	}

	query := fmt.Sprintf("DROP TABLE IF EXISTS '%s'", tableName)
	_, err := h.db.ExecContext(r.Context(), query)
	if err != nil {
		http.Redirect(w, r, fmt.Sprintf("/admin/db?view=database&table=%s&error=%s", tableName, template.URLQueryEscaper(err.Error())), http.StatusSeeOther)
		return
	}

	http.Redirect(w, r, "/admin/db?view=database&msg=Table+dropped+successfully", http.StatusSeeOther)
}

func (h *DBAdminHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	selectedTable := r.URL.Query().Get("table")
	viewMode := r.URL.Query().Get("view")
	msg := r.URL.Query().Get("msg")
	errMsg := r.URL.Query().Get("error")

	if viewMode == "" {
		viewMode = "database"
	}

	// Get all tables
	tableRows, err := h.db.QueryContext(r.Context(), "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")
	if err != nil {
		http.Error(w, "Failed to query SQLite database", http.StatusInternalServerError)
		return
	}
	defer tableRows.Close()

	var tableNames []string
	for tableRows.Next() {
		var name string
		if err := tableRows.Scan(&name); err == nil {
			tableNames = append(tableNames, name)
		}
	}

	if selectedTable == "" && len(tableNames) > 0 {
		selectedTable = tableNames[0]
	}

	var columns []ColumnInfo
	var dataRows [][]string
	var headers []string
	var pkColName string

	if selectedTable != "" {
		// Get columns
		colRows, err := h.db.QueryContext(r.Context(), fmt.Sprintf("PRAGMA table_info('%s')", selectedTable))
		if err == nil {
			for colRows.Next() {
				var c ColumnInfo
				_ = colRows.Scan(&c.Cid, &c.Name, &c.Type, &c.NotNull, &c.DfltValue, &c.Pk)
				columns = append(columns, c)
				headers = append(headers, c.Name)
				if c.Pk == 1 && pkColName == "" {
					pkColName = c.Name
				}
			}
			colRows.Close()
		}
		if pkColName == "" && len(columns) > 0 {
			pkColName = columns[0].Name
		}

		// Get rows
		dataQ, err := h.db.QueryContext(r.Context(), fmt.Sprintf("SELECT * FROM '%s' LIMIT 100", selectedTable))
		if err == nil {
			cols, _ := dataQ.Columns()
			for dataQ.Next() {
				values := make([]interface{}, len(cols))
				valuePtrs := make([]interface{}, len(cols))
				for i := range values {
					valuePtrs[i] = &values[i]
				}
				if err := dataQ.Scan(valuePtrs...); err == nil {
					rowStr := make([]string, len(cols))
					for i, val := range values {
						if val == nil {
							rowStr[i] = "NULL"
						} else if b, ok := val.([]byte); ok {
							rowStr[i] = string(b)
						} else {
							rowStr[i] = fmt.Sprintf("%v", val)
						}
					}
					dataRows = append(dataRows, rowStr)
				}
			}
			dataQ.Close()
		}
	}

	// JSON response requested?
	if strings.Contains(r.Header.Get("Accept"), "application/json") || r.URL.Query().Get("format") == "json" {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"table":     selectedTable,
			"columns":   columns,
			"rows":      dataRows,
			"endpoints": backendEndpoints,
		})
		return
	}

	// Serve HTML Web UI Dashboard
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_ = dbDashboardTemplate.Execute(w, map[string]interface{}{
		"TableNames":    tableNames,
		"SelectedTable": selectedTable,
		"ViewMode":      viewMode,
		"Columns":       columns,
		"Headers":       headers,
		"Rows":          dataRows,
		"Endpoints":     backendEndpoints,
		"PkColName":     pkColName,
		"Message":       msg,
		"Error":         errMsg,
	})
}

var dbDashboardTemplate = template.Must(template.New("db_dashboard").Parse(`<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AGAP GIS Database & API Explorer</title>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;800;900&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg: #0F172A;
            --surface: rgba(30, 41, 59, 0.7);
            --border: rgba(255, 255, 255, 0.1);
            --primary: #EF4444;
            --primary-hover: #DC2626;
            --text-main: #F8FAFC;
            --text-sub: #94A3B8;
            --accent-blue: #38BDF8;
            --green: #22C55E;
            --yellow: #EAB308;
            --purple: #A855F7;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: 'Inter', system-ui, sans-serif;
            background-color: var(--bg);
            color: var(--text-main);
            min-height: 100vh;
            padding: 24px;
        }
        header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 20px;
            padding-bottom: 16px;
            border-bottom: 1px solid var(--border);
        }
        .branding {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .shield-icon {
            width: 36px;
            height: 36px;
            background: linear-gradient(135deg, #EF4444, #B91C1C);
            border-radius: 8px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 900;
            font-size: 18px;
            color: white;
            box-shadow: 0 4px 14px rgba(239, 68, 68, 0.4);
        }
        h1 { font-size: 20px; font-weight: 900; letter-spacing: 1px; }
        .sub-tag { font-size: 12px; color: var(--text-sub); }
        .alert-box {
            padding: 12px 16px;
            border-radius: 10px;
            margin-bottom: 16px;
            font-size: 13px;
            font-weight: 600;
        }
        .alert-success { background: rgba(34, 197, 94, 0.15); color: var(--green); border: 1px solid var(--green); }
        .alert-error { background: rgba(239, 68, 68, 0.15); color: var(--primary); border: 1px solid var(--primary); }
        .view-nav {
            display: flex;
            gap: 12px;
            margin-bottom: 20px;
        }
        .view-btn {
            padding: 10px 18px;
            border-radius: 12px;
            background: var(--surface);
            border: 1px solid var(--border);
            color: var(--text-sub);
            font-weight: 700;
            font-size: 13px;
            text-decoration: none;
            transition: all 0.2s ease;
        }
        .view-btn.active {
            background: rgba(239, 68, 68, 0.2);
            border-color: var(--primary);
            color: #FFF;
        }
        .container {
            display: grid;
            grid-template-columns: 280px 1fr;
            gap: 20px;
        }
        .sidebar {
            background: var(--surface);
            border: 1px solid var(--border);
            border-radius: 16px;
            padding: 16px;
            backdrop-filter: blur(12px);
        }
        .sidebar h2 {
            font-size: 11px;
            font-weight: 800;
            text-transform: uppercase;
            letter-spacing: 1px;
            color: var(--text-sub);
            margin-bottom: 12px;
        }
        .table-btn {
            display: flex;
            align-items: center;
            justify-content: space-between;
            width: 100%;
            padding: 10px 14px;
            margin-bottom: 6px;
            background: transparent;
            border: 1px solid transparent;
            border-radius: 10px;
            color: var(--text-sub);
            font-weight: 600;
            font-size: 13px;
            text-decoration: none;
            transition: all 0.2s ease;
        }
        .table-btn:hover {
            background: rgba(255, 255, 255, 0.05);
            color: var(--text-main);
        }
        .table-btn.active {
            background: rgba(239, 68, 68, 0.15);
            border-color: rgba(239, 68, 68, 0.4);
            color: #FCA5A5;
            font-weight: 800;
        }
        .main-content {
            background: var(--surface);
            border: 1px solid var(--border);
            border-radius: 16px;
            padding: 20px;
            backdrop-filter: blur(12px);
        }
        .section-title {
            font-size: 16px;
            font-weight: 800;
            margin-bottom: 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
        }
        .badge {
            font-size: 11px;
            background: rgba(56, 189, 248, 0.15);
            color: var(--accent-blue);
            padding: 4px 10px;
            border-radius: 20px;
            border: 1px solid rgba(56, 189, 248, 0.3);
        }
        .btn-action {
            padding: 6px 12px;
            border-radius: 8px;
            font-size: 11px;
            font-weight: 800;
            border: none;
            cursor: pointer;
            text-decoration: none;
            transition: all 0.2s;
        }
        .btn-green { background: var(--green); color: black; }
        .btn-green:hover { background: #16A34A; }
        .btn-yellow { background: var(--yellow); color: black; }
        .btn-yellow:hover { background: #CA8A04; }
        .btn-red { background: var(--primary); color: white; }
        .btn-red:hover { background: var(--primary-hover); }
        .btn-blue { background: var(--accent-blue); color: black; }
        .method-badge {
            font-size: 10px;
            font-weight: 900;
            padding: 3px 8px;
            border-radius: 6px;
            letter-spacing: 0.5px;
        }
        .method-GET { background: rgba(34, 197, 94, 0.2); color: var(--green); border: 1px solid var(--green); }
        .method-POST { background: rgba(56, 189, 248, 0.2); color: var(--accent-blue); border: 1px solid var(--accent-blue); }
        .method-PUT { background: rgba(234, 179, 8, 0.2); color: var(--yellow); border: 1px solid var(--yellow); }
        .method-DELETE { background: rgba(239, 68, 68, 0.2); color: var(--primary); border: 1px solid var(--primary); }
        .schema-box {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            margin-bottom: 20px;
            padding: 12px;
            background: rgba(15, 23, 42, 0.6);
            border-radius: 12px;
            border: 1px solid var(--border);
            align-items: center;
        }
        .col-chip {
            font-size: 11px;
            padding: 4px 8px;
            background: rgba(255, 255, 255, 0.05);
            border: 1px solid var(--border);
            border-radius: 6px;
            color: var(--text-main);
        }
        .col-chip .type { color: var(--accent-blue); font-weight: bold; }
        .col-chip .pk { color: #F59E0B; font-weight: bold; }
        .table-wrapper {
            overflow-x: auto;
            border-radius: 12px;
            border: 1px solid var(--border);
        }
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 12px;
            text-align: left;
        }
        th {
            background: rgba(15, 23, 42, 0.8);
            color: var(--text-sub);
            font-weight: 800;
            padding: 10px 14px;
            border-bottom: 1px solid var(--border);
            white-space: nowrap;
        }
        td {
            padding: 10px 14px;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
            color: var(--text-main);
            white-space: nowrap;
            max-width: 300px;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        tr:hover td {
            background: rgba(255, 255, 255, 0.03);
        }
        .null-val { color: var(--text-sub); font-style: italic; opacity: 0.6; }
        .card-form {
            background: rgba(15, 23, 42, 0.6);
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 16px;
            margin-bottom: 20px;
        }
        .form-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
            gap: 12px;
            margin-bottom: 12px;
        }
        .form-group {
            display: flex;
            flex-direction: column;
            gap: 4px;
        }
        .form-group label {
            font-size: 11px;
            font-weight: 700;
            color: var(--text-sub);
        }
        .form-input, .form-select {
            padding: 8px 10px;
            background: rgba(30, 41, 59, 0.8);
            border: 1px solid var(--border);
            border-radius: 8px;
            color: var(--text-main);
            font-size: 12px;
            outline: none;
        }
        .form-input:focus {
            border-color: var(--accent-blue);
        }
        .endpoint-card {
            background: rgba(15, 23, 42, 0.6);
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 14px;
            margin-bottom: 12px;
        }
        .endpoint-header {
            display: flex;
            align-items: center;
            gap: 12px;
            margin-bottom: 6px;
        }
        .endpoint-path {
            font-family: monospace;
            font-size: 14px;
            font-weight: 700;
            color: var(--text-main);
        }
        .endpoint-desc {
            font-size: 12px;
            color: var(--text-sub);
        }
    </style>
</head>
<body>
    <header>
        <div class="branding">
            <div class="shield-icon">A</div>
            <div>
                <h1>AGAP GIS DATABASE & API EXPLORER</h1>
                <div class="sub-tag">SQLite Live Inspector, Full CRUD (Insert, Update, Delete) & Schema Management</div>
            </div>
        </div>
        <div>
            <span class="badge">SQLite WAL Mode Active</span>
        </div>
    </header>

    {{if .Message}}
        <div class="alert-box alert-success">✅ {{.Message}}</div>
    {{end}}
    {{if .Error}}
        <div class="alert-box alert-error">❌ {{.Error}}</div>
    {{end}}

    <div class="view-nav">
        <a href="?view=database&table={{.SelectedTable}}" class="view-btn {{if eq .ViewMode "database"}}active{{end}}">
            📊 Database Tables & CRUD
        </a>
        <a href="?view=endpoints" class="view-btn {{if eq .ViewMode "endpoints"}}active{{end}}">
            🔌 REST & WebSocket API Endpoints ({{len .Endpoints}})
        </a>
    </div>

    {{if eq .ViewMode "endpoints"}}
        <div class="main-content" style="grid-column: span 2;">
            <div class="section-title">
                <span>Registered Backend API Endpoints</span>
                <span class="badge">{{len .Endpoints}} Endpoints Active</span>
            </div>

            {{range .Endpoints}}
                <div class="endpoint-card">
                    <div class="endpoint-header">
                        <span class="method-badge method-{{.Method}}">{{.Method}}</span>
                        <span class="endpoint-path">{{.Path}}</span>
                        <span class="badge">{{.Auth}}</span>
                    </div>
                    <div class="endpoint-desc">{{.Description}}</div>
                </div>
            {{end}}
        </div>
    {{else}}
        <div class="container">
            <div class="sidebar">
                <h2>Database Tables</h2>
                {{range .TableNames}}
                    <a href="?view=database&table={{.}}" class="table-btn {{if eq . $.SelectedTable}}active{{end}}">
                        <span>📊 {{.}}</span>
                    </a>
                {{end}}

                <hr style="border:0; border-top: 1px solid var(--border); margin: 16px 0;">

                <h2>Create New Table</h2>
                <form action="/admin/db/schema/create-table" method="POST" style="display:flex; flex-direction:column; gap:8px;">
                    <input type="text" name="table_name" placeholder="table_name" class="form-input" required>
                    <textarea name="column_defs" placeholder="id INTEGER PRIMARY KEY, name TEXT NOT NULL" class="form-input" style="height:60px; font-family:monospace;" required></textarea>
                    <button type="submit" class="btn-action btn-blue">➕ Create Table</button>
                </form>
            </div>

            <div class="main-content">
                <div class="section-title">
                    <span>Table: <strong>{{.SelectedTable}}</strong></span>
                    <div style="display:flex; gap:8px;">
                        <button onclick="document.getElementById('insertForm').style.display = document.getElementById('insertForm').style.display === 'none' ? 'block' : 'none';" class="btn-action btn-green">➕ Insert New Row</button>
                        <form action="/admin/db/schema/drop-table" method="POST" onsubmit="return confirm('Drop table {{.SelectedTable}}?');" style="display:inline;">
                            <input type="hidden" name="table_name" value="{{.SelectedTable}}">
                            <button type="submit" class="btn-action btn-red">🗑️ Drop Table</button>
                        </form>
                    </div>
                </div>

                <!-- Add New Column / Attribute Form -->
                <div class="schema-box">
                    <span style="font-size:11px; font-weight:800; color:var(--text-sub);">SCHEMA ATTRIBUTES:</span>
                    {{range .Columns}}
                        <span class="col-chip">
                            {{.Name}} <span class="type">: {{.Type}}</span>
                            {{if eq .Pk 1}}<span class="pk">(PK)</span>{{end}}
                        </span>
                    {{end}}

                    <!-- Inline Add Attribute Form -->
                    <form action="/admin/db/schema/add-column" method="POST" style="display:inline-flex; gap:6px; margin-left:auto;">
                        <input type="hidden" name="table_name" value="{{.SelectedTable}}">
                        <input type="text" name="col_name" placeholder="new_attr" class="form-input" style="padding:2px 6px; width:90px;" required>
                        <select name="col_type" class="form-select" style="padding:2px 6px;">
                            <option value="TEXT">TEXT</option>
                            <option value="INTEGER">INTEGER</option>
                            <option value="REAL">REAL</option>
                            <option value="BLOB">BLOB</option>
                        </select>
                        <button type="submit" class="btn-action btn-blue" style="padding:3px 8px;">+ Add Attr</button>
                    </form>
                </div>

                <!-- Insert Row Form Modal/Card -->
                <div id="insertForm" class="card-form" style="display:none;">
                    <div style="font-size:13px; font-weight:800; margin-bottom:12px;">➕ Insert Record into {{.SelectedTable}}</div>
                    <form action="/admin/db/row/insert" method="POST">
                        <input type="hidden" name="table_name" value="{{.SelectedTable}}">
                        <div class="form-grid">
                            {{range .Columns}}
                                <div class="form-group">
                                    <label>{{.Name}} ({{.Type}})</label>
                                    <input type="text" name="{{.Name}}" placeholder="{{if eq .Pk 1}}Auto-generated if empty{{else}}{{.Type}}{{end}}" class="form-input">
                                </div>
                            {{end}}
                        </div>
                        <button type="submit" class="btn-action btn-green" style="padding:8px 16px;">Save Record</button>
                    </form>
                </div>

                <!-- Edit Row Form Modal/Card -->
                <div id="editFormBox" class="card-form" style="display:none; border-color: var(--yellow);">
                    <div style="font-size:13px; font-weight:800; margin-bottom:12px; color: var(--yellow);">✏️ Edit Record in {{.SelectedTable}}</div>
                    <form action="/admin/db/row/update" method="POST">
                        <input type="hidden" name="table_name" value="{{.SelectedTable}}">
                        <input type="hidden" name="pk_col" value="{{.PkColName}}">
                        <input type="hidden" name="pk_val" id="editPkVal">
                        <div class="form-grid" id="editFormGrid"></div>
                        <div style="display:flex; gap:8px;">
                            <button type="submit" class="btn-action btn-yellow" style="padding:8px 16px;">Update Record</button>
                            <button type="button" onclick="document.getElementById('editFormBox').style.display='none';" class="btn-action btn-red" style="padding:8px 16px;">Cancel</button>
                        </div>
                    </form>
                </div>

                <div class="table-wrapper">
                    <table>
                        <thead>
                            <tr>
                                <th style="width:90px;">ACTIONS</th>
                                {{range .Headers}}
                                    <th>{{.}}</th>
                                {{end}}
                            </tr>
                        </thead>
                        <tbody>
                            {{$headers := .Headers}}
                            {{$selectedTable := .SelectedTable}}
                            {{$pkColName := .PkColName}}
                            {{range $rowIdx, $row := .Rows}}
                                <tr>
                                    <td>
                                        <div style="display:flex; gap:4px;">
                                            <button onclick='openEditRow({{$row}}, {{$headers}}, "{{$selectedTable}}", "{{$pkColName}}")' class="btn-action btn-yellow" style="padding:2px 6px;">✏️</button>
                                            <form action="/admin/db/row/delete" method="POST" onsubmit="return confirm('Delete this record?');" style="display:inline;">
                                                <input type="hidden" name="table_name" value="{{$selectedTable}}">
                                                <input type="hidden" name="pk_col" value="{{$pkColName}}">
                                                <input type="hidden" name="pk_val" value="{{index $row 0}}">
                                                <button type="submit" class="btn-action btn-red" style="padding:2px 6px;">✕</button>
                                            </form>
                                        </div>
                                    </td>
                                    {{range .}}
                                        <td>{{if eq . "NULL"}}<span class="null-val">NULL</span>{{else}}{{.}}{{end}}</td>
                                    {{end}}
                                </tr>
                            {{else}}
                                <tr>
                                    <td colspan="100%" style="text-align:center; padding: 24px; color: var(--text-sub);">
                                        No records found in table <strong>{{.SelectedTable}}</strong>
                                    </td>
                                </tr>
                            {{end}}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    {{end}}

    <script>
        function openEditRow(rowVals, headers, tableName, pkCol) {
            const editBox = document.getElementById('editFormBox');
            const editGrid = document.getElementById('editFormGrid');
            const pkValInput = document.getElementById('editPkVal');
            
            editGrid.innerHTML = '';
            pkValInput.value = rowVals[0] || '';

            headers.forEach(function(h, i) {
                const val = (rowVals[i] === 'NULL') ? '' : rowVals[i];
                const isPk = (h === pkCol);
                const div = document.createElement('div');
                div.className = 'form-group';
                div.innerHTML = '<label>' + h + (isPk ? ' (PK)' : '') + '</label>' +
                    '<input type="text" name="' + h + '" value="' + val + '" ' + (isPk ? 'readonly style="opacity:0.6;"' : '') + ' class="form-input">';
                editGrid.appendChild(div);
            });

            editBox.style.display = 'block';
            editBox.scrollIntoView({ behavior: 'smooth' });
        }
    </script>

</body>
</html>
`))

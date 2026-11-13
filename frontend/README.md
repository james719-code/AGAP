# AGAP Frontend — GIS Command Center Dispatcher Dashboard

The AGAP frontend is a **GIS Command Center & Emergency Dispatch Dashboard** built with **React 19**, **TypeScript**, **Vite**, **Tailwind CSS**, and **shadcn/ui**. It supports the Go API and WebSocket for local or self-hosted setups. For a managed API, follow the [Cloudflare, Supabase, and Firebase deployment guide](../functions/README.md).

---

## Prerequisites & Tooling Requirements

Before installing and running the frontend dashboard, ensure you have the following installed:

* **Node.js**: `v20.x` or higher — [Download Node.js](https://nodejs.org/)
* **npm**: `v10.x+` (comes bundled with Node.js) or `pnpm` / `yarn`
* **Modern Web Browser**: Google Chrome, Mozilla Firefox, or Microsoft Edge (with WebSocket & ES2022 support)

---

## Quick Start & Installation

### Step 1: Navigate to Frontend Directory
```bash
cd frontend
```

### Step 2: Environment Configuration
Copy `.env.example` to create your local `.env` configuration file:

```bash
# On Linux / macOS / Git Bash
cp .env.example .env

# On Windows PowerShell
Copy-Item .env.example .env
```

### Step 3: Install Package Dependencies
Install all Node.js dependencies:

```bash
npm install
```

---

## Running the Dashboard

### Development Mode (Hot Reloading)
Start the Vite development server:

```bash
npm run dev
```

*The application will start at `http://localhost:5173`. Open this URL in your web browser.*

### Production Build & Preview
To create an optimized production build:

```bash
npm run build
```

To preview the compiled production build locally:

```bash
npm run preview
```

### Linting & Type Checking
Run ESLint to check for code quality and style compliance:

```bash
npm run lint
```

---

## Environment Variables Reference

| Variable | Default Value | Description |
|---|---|---|
| `VITE_APP_TITLE` | `AGAP Command Center Dashboard` | Page title displayed in browser tab |
| `VITE_API_PROVIDER` | `go` | Backend runtime. Choose `go`, `cloudflare`, `supabase`, or `firebase` |
| `VITE_API_BASE_URL` | `/agap/api/v1` | REST API URL including `/api/v1` |
| `VITE_WS_URL` | `/agap/ws` | WebSocket URL for the Go runtime |
| `VITE_SUPABASE_PUBLISHABLE_KEY` | — | Optional public Supabase key for requests to the Edge Function |
| `VITE_MAP_TILES_URL` | `https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png` | OpenStreetMap tile server template URL |
| `VITE_MAP_ATTRIBUTION` | `&copy; OpenStreetMap contributors` | Map tile layer copyright attribution string |

---

## Repository Structure

```
frontend/
├── components.json               # shadcn/ui component configuration
├── eslint.config.js              # ESLint 9 flat configuration
├── index.html                    # Single Page Application entry HTML
├── package.json                  # Dependencies & script commands
├── postcss.config.js             # PostCSS configuration for Tailwind
├── public/                       # Static public assets (icons, logos)
├── src/                          # Application source code
│   ├── components/               # React UI components & view modules
│   │   └── ui/                   # shadcn/ui primitive components
│   ├── hooks/                    # Custom React hooks (WebSocket, Telemetry hooks)
│   ├── lib/                      # Helper utilities (cn class merger, formatters)
│   ├── maps/                     # GIS Leaflet + OpenStreetMap interactive map integration
│   ├── services/                 # REST API client & HTTP fetchers
│   └── types/                    # TypeScript interfaces & domain type definitions
├── tailwind.config.js            # Tailwind CSS styling configuration
├── tsconfig.json                 # TypeScript compiler configuration
├── vite.config.ts                # Vite build & alias configuration
└── README.md                     # Frontend developer guide
```

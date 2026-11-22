# Synthetic Command Center visual preview

When the normal Vite development server is running, open `http://localhost:5173/agap/__visual-preview/`. The preview bundle is generated in memory and served only on the local development machine. Alternatively, from `frontend`, run `npm run dev:visual` and open `http://localhost:4173/`.

This is a separate, loopback-only development entry for inspecting the real UI components with fictional sample data. It is not the AGAP application and does not authenticate an administrator, issue a token, create a session, connect to the real API or WebSocket, use real preferences, or write emergency records. All mutation methods are blocked. It cannot be built as a production application through its configuration.

The ordinary application (`npm run dev`) and its production build retain their existing authentication and authorization behavior.

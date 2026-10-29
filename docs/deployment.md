# Deployment: Neon + Render + Vercel (free tiers)

None of these steps run automatically. Each is a manual action in a provider dashboard and needs your account. Free tiers have no uptime guarantee.

## 1. PostgreSQL on Neon (or any PostgreSQL host)

1. Create a project and database (e.g. `devarena`).
2. Copy the connection details. The JDBC URL looks like this:
   `jdbc:postgresql://<host>/<db>?sslmode=require`
3. Note the user and password. They become Render environment variables and are never committed.

Flyway creates the schema on first boot. Keep `DB_POOL_MAX` low (4), because free databases limit connections.

## 2. Backend on Render (Docker)

1. Push the repo to GitHub (your choice; nothing here publishes it).
2. Render → **New → Blueprint**, pick the repo; it reads `render.yaml`. Alternatively, choose **New → Web Service → Docker** with root directory `backend`.
3. Set the environment variables:

| Variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | `jdbc:postgresql://<host>/<db>?sslmode=require` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | from Neon |
| `DEVARENA_CORS_ALLOWED_ORIGINS` | `https://<your-app>.vercel.app` (comma-separate extra origins) |

4. Deploy. The health check path is `/actuator/health`. The image uses a non-root user and the JVM is tuned for 512 MB. Render injects `PORT`, which the app binds to.

## 3. Frontend on Vercel

1. In `frontend/vercel.json`, replace `YOUR-RENDER-SERVICE` with your Render hostname. That rewrite makes `/api/*` same-origin, which keeps the session cookie first-party.
2. Vercel → **New Project**, root directory `frontend`, framework preset **Vite**.
3. Set the environment variables (public values only):

| Variable | Value |
| --- | --- |
| `VITE_WS_BASE_URL` | `wss://<your-render-service>.onrender.com` |
| `VITE_API_BASE_URL` | leave **empty** (use the rewrite) |

4. Deploy, then open `https://<your-app>.vercel.app`.

## 4. Smoke test

- `https://<render>.onrender.com/actuator/health` returns `{"status":"UP"}`.
- Register on the Vercel URL. In devtools you should see a `SESSION` cookie (HttpOnly, Secure) on the Vercel domain.
- Create a battle in one browser and join from another. Live updates should arrive over `wss://`.

## Free-tier behaviour (documented, not hidden)

- **Render free instances sleep** after about 15 minutes without traffic. The next request wakes it, which takes up to a minute. The SPA shows "server is starting up", retries API calls with bounded backoff, and the socket reconnects on its own. There is deliberately **no keep-alive pinging**.
- **A restart drops live sockets.** Clients reconnect and resubscribe automatically. In-progress battles resume from the database, though a round may close as soon as the server returns if its deadline passed meanwhile. Sessions survive restarts because they're stored in PostgreSQL.
- **One instance only.** Don't scale the Render service horizontally; see the single-instance notes in [architecture.md](architecture.md).

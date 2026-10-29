# Environment variables

## Backend

| Variable | Default (local) | Notes |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | (none) | `prod` on Render: secure cookies, required DB variables |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/devarena` | add `?sslmode=require` for hosted databases |
| `DATABASE_USERNAME` | `devarena` | |
| `DATABASE_PASSWORD` | empty | **secret**; never commit it |
| `DB_POOL_MAX` | `5` (prod `4`) | Hikari maximum pool size |
| `DEVARENA_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | comma-separated; also the WebSocket Origin allowlist |
| `DEVARENA_SECURE_COOKIES` | `false` | forced `true` in `prod` |
| `PORT` | `8080` | Render sets this |

Fixed settings, changed in `application.yml` if needed:
- `devarena.login-max-attempts` (5)
- `devarena.login-lock-minutes` (15)
- `devarena.ws-ticket-seconds` (30)
- `spring.session.timeout` (14 days)
- `devarena.gamification.levels`, an optional list of `{level, title, minXp}` that replaces the default levels

## Frontend (Vite)

Every `VITE_*` value is compiled into public JavaScript. **Never put secrets in them.**

| Variable | Default | Notes |
| --- | --- | --- |
| `VITE_API_BASE_URL` | empty (same origin) | leave empty with the Vercel rewrite |
| `VITE_WS_BASE_URL` | derived from `location` | production: `wss://<render-service>.onrender.com` |

## Dev launcher system properties (`DevServer`)

| Property | Default |
| --- | --- |
| `devarena.dataDir` | `../.data/pg` |
| `devarena.pgPort` | `5544` |

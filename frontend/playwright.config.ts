import { defineConfig } from '@playwright/test'

/**
 * End-to-end tests drive the real app: Vite dev server + a running backend (see README:
 * "Run everything locally"). Uses the locally installed Chrome by default (no browser download);
 * set PW_CHANNEL= (empty) to use Playwright's bundled Chromium in CI.
 */
const channel = process.env.PW_CHANNEL ?? 'chrome'

export default defineConfig({
  testDir: './e2e',
  timeout: 180_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    ...(channel ? { channel } : {}),
    trace: 'retain-on-failure',
    viewport: { width: 1440, height: 900 },
  },
  webServer: process.env.E2E_BASE_URL ? undefined : {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 120_000,
  },
})

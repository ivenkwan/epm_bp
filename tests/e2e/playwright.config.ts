import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: 'accessibility',
  timeout: 60_000,
  retries: 0,
  use: {
    baseURL: process.env.EPM_WEB_URL ?? 'http://localhost:5173',
    // Local environment: playwright cannot install browsers on this Ubuntu; reuse the
    // system-cached chromium build (channel-less executable path override).
    launchOptions: {
      executablePath: process.env.EPM_CHROMIUM_PATH ?? `${process.env.HOME ?? ''}/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome`,
    },
  },
  reporter: [['list']],
});

import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'line',
  use: {
    baseURL: 'http://127.0.0.1:5202',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    reducedMotion: 'reduce',
    colorScheme: 'light',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    ...devices['Desktop Chrome'],
  },
  projects: [
    {
      name: 'visual-chromium',
      testMatch: /standardization-responsive\.spec\.ts/u,
    },
    {
      name: 'chrome-behavior',
      testIgnore: /standardization-responsive\.spec\.ts/u,
      use: { channel: 'chrome' },
    },
  ],
  webServer: {
    command: 'npm run preview -- --host 127.0.0.1 --port 5202 --strictPort',
    url: 'http://127.0.0.1:5202',
    reuseExistingServer: false,
    timeout: 45_000,
  },
});

/*
 * Copyright 2026 LIFS Tools Consortium.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests of the packaged Goslin webapp. Not part of `./mvnw verify`.
 *
 *   ./mvnw -B package -DskipTests
 *   cd e2e && npm ci && npx playwright install chromium
 *   npm test                 # starts the jar (scripts/start-app.sh) on GOSLIN_E2E_PORT (default 8083)
 *   npm run screenshots      # only the manual's screenshots (src/main/resources/static/img)
 *
 * GOSLIN_E2E_PORT picks another port, when 8083 is taken (an already running server is never reused).
 * E2E_BASE_URL tests a running instance instead (it must end with a slash); then no server is started.
 * The tests never send tracking data: tests/helpers.ts answers every Matomo request in the browser,
 * and start-app.sh points the server-side tracker at a closed port.
 */
const port = process.env['GOSLIN_E2E_PORT'] ?? '8083';
const baseURL = process.env['E2E_BASE_URL'] ?? `http://localhost:${port}/goslin/`;
const screenshots = !!process.env['SCREENSHOTS'];

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  workers: process.env['CI'] ? 2 : undefined,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 60_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: screenshots
    ? // `npm run screenshots`: only screenshots.spec.ts
      [{ name: 'screenshots', use: { ...devices['Desktop Chrome'] }, testMatch: /screenshots\.spec\.ts/ }]
    : [
        {
          name: 'chromium',
          use: { ...devices['Desktop Chrome'] },
          testIgnore: /screenshots\.spec\.ts/,
          grepInvert: /@timing/,
        },
        // Timing tests (tag @timing) run alone, after all other tests, so that no parallel test loads the
        // server or the machine, and without retries, so that a slow run is not hidden by a fast retry.
        // When a test of the chromium project fails, they are skipped.
        {
          name: 'timing',
          use: { ...devices['Desktop Chrome'] },
          testMatch: /harmonize\.spec\.ts/,
          grep: /@timing/,
          dependencies: ['chromium'],
          retries: 0,
        },
      ],
  webServer: process.env['E2E_BASE_URL']
    ? undefined
    : {
        command: './scripts/start-app.sh',
        url: `http://localhost:${port}/goslin/actuator/health`,
        reuseExistingServer: false,
        timeout: 120_000,
        stdout: 'pipe',
        stderr: 'pipe',
      },
});

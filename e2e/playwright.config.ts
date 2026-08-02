import { defineConfig, devices } from '@playwright/test'

/**
 * Test E2E przepływu (M5.3/D30) — przeciw SPAKOWANEMU JAROWI, nie serwerowi dev:
 * sprawdzamy między innymi to, że front rzeczywiście wychodzi z jara pod /static
 * i że hash w adresie nie potrzebuje fallbacku SPA.
 *
 * Zakres to jeden przepływ, nie siatka przypadków: od E2E chcemy sygnału
 * „całość się rozpięła", a szczegóły pokrywają testy jednostkowe i integracyjne.
 */
const PORT = Number(process.env.E2E_APP_PORT ?? 8080)
const STUB_PORT = Number(process.env.E2E_STUB_PORT ?? 8089)
const DATASOURCE_URL =
  process.env.E2E_DATASOURCE_URL ?? 'jdbc:postgresql://localhost:5432/musicview'

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  reporter: process.env.CI ? [['github'], ['list']] : [['list']],
  use: {
    baseURL: `http://127.0.0.1:${PORT}`,
    trace: 'retain-on-failure',
  },
  // Jedna przeglądarka wystarczy: aplikacja jest narzędziem jednego DJ-a (D2),
  // a od E2E chcemy sygnału „całość działa", nie macierzy zgodności.
  // E2E_CHROMIUM_PATH pozwala wskazać Chromium już obecne w systemie (obrazy CI
  // i kontenery deweloperskie często je mają) zamiast pobierać własne.
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        launchOptions: { executablePath: process.env.E2E_CHROMIUM_PATH || undefined },
      },
    },
  ],
  webServer: [
    {
      command: `node stub-server.mjs`,
      port: STUB_PORT,
      reuseExistingServer: !process.env.CI,
      stdout: 'pipe',
    },
    {
      command: `java -jar ../target/music-view-0.1.0-SNAPSHOT.jar`,
      url: `http://127.0.0.1:${PORT}/actuator/health`,
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
      stdout: 'pipe',
      env: {
        SERVER_PORT: String(PORT),
        SPRING_DATASOURCE_URL: DATASOURCE_URL,
        SPRING_DATASOURCE_USERNAME: process.env.E2E_DB_USER ?? 'musicview',
        SPRING_DATASOURCE_PASSWORD: process.env.E2E_DB_PASSWORD ?? 'musicview',
        // wszystkie źródła zewnętrzne na stub — bez sieci i bez kluczy
        LLM_PROVIDER: 'openai',
        LLM_BASE_URL: `http://127.0.0.1:${STUB_PORT}`,
        LLM_API_KEY: 'e2e-key',
        LLM_MODEL: 'e2e-model',
        LLM_COST_INPUT_PER_1M: '1.00',
        LLM_COST_OUTPUT_PER_1M: '5.00',
        CLIENTS_DEEZER_BASEURL: `http://127.0.0.1:${STUB_PORT}`,
        CLIENTS_MUSICBRAINZ_BASEURL: `http://127.0.0.1:${STUB_PORT}`,
        CLIENTS_SPOTIFY_BASEURL: `http://127.0.0.1:${STUB_PORT}`,
        CLIENTS_SPOTIFY_AUTHURL: `http://127.0.0.1:${STUB_PORT}`,
        MB_USER_AGENT: 'music-view-e2e (test@example.com)',
        MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: 'health,info',
      },
    },
  ],
})

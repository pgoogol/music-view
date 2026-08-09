import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

/**
 * Pełny przepływ DJ-a (DoD Etapu 3 rozszerzone o Etap 4) na dwóch osobnych
 * aplikacjach — zbudowany front i backend za proxy /api:
 * import → przegląd → biblioteka i utwór → wzbogacenie AI → set → generator.
 *
 * Eksport na Spotify świadomie zostaje poza tym testem: wymagałby albo
 * przeprowadzenia OAuth przez ekran zgody Spotify, albo wpisania tokenów wprost
 * do bazy. Ta ścieżka ma własny test integracyjny na WireMocku
 * (PlaylistExportIntegrationTest) — dublowanie jej tutaj kupiłoby ryzyko
 * fałszywych alarmów bez nowego sygnału.
 */

const CSV = `Track Name,Artist Name(s),Album Name,Track URI
Vivir Mi Vida,Marc Anthony,3.0,spotify:track:e2eVivir00000000000000
La Vida Es Un Carnaval,Celia Cruz,Mi Vida Es Cantar,spotify:track:e2eCarna00000000000000
Bailando,Enrique Iglesias,Sex and Love,spotify:track:e2eBaila00000000000000
Danza Kuduro,Don Omar,Meet the Orphans,spotify:track:e2eDanza00000000000000
Propuesta Indecente,Romeo Santos,Formula Vol 2,spotify:track:e2ePropu00000000000000
`

/** Sprzątanie po poprzednim przebiegu — test ma być powtarzalny na tej samej bazie. */
async function resetLibrary(api: APIRequestContext) {
  const playlists = await (await api.get('/api/playlists')).json()
  for (const playlist of playlists) {
    await api.delete(`/api/playlists/${playlist.id}`)
  }
  const page = await (await api.get('/api/library/tracks?size=100')).json()
  for (const entry of page.content ?? []) {
    await api.delete(`/api/library/tracks/${entry.spotifyId}`)
  }
}

async function openTab(page: Page, name: string) {
  await page.getByRole('button', { name, exact: true }).click()
}

/** „zamknij" ma też host toastów — celujemy w szufladę, nie w powiadomienie. */
async function closeDrawer(page: Page) {
  await page.getByTestId('track-details').getByRole('button', { name: 'zamknij' }).click()
  await expect(page.getByTestId('track-details')).toBeHidden()
}

test.describe('przepływ DJ-a', () => {

  test.beforeEach(async ({ request }) => {
    await resetLibrary(request)
  })

  test('import → przegląd → utwór → wzbogacenie → set → generator', async ({ page, request }) => {

    // --- import: plik CSV wchodzi do katalogu i biblioteki (tryb A, D6)
    const imported = await request.post('/api/ingest/file', {
      multipart: {
        file: { name: 'biblioteka.csv', mimeType: 'text/csv', buffer: Buffer.from(CSV) },
      },
    })
    expect(imported.ok()).toBeTruthy()
    expect((await imported.json()).imported).toBe(5)

    // --- przegląd: front dochodzi do API po względnym /api, liczby liczy baza
    await page.goto('/#/overview')
    await expect(page.getByTestId('overview')).toBeVisible()
    await expect(page.getByTestId('overview-headline')).toContainText('5')
    await expect(page.getByTestId('bpm-sources')).toBeVisible()

    // --- biblioteka: wyszukiwarka i stan widoku w hashu (D22)
    await openTab(page, 'Biblioteka')
    await page.getByTestId('search-input').fill('vivir')
    await expect(page.getByTestId('result-summary')).toContainText('1 utworów')
    await expect(page).toHaveURL(/q=vivir/)

    // --- utwór: dane prywatne DJ-a zapisują się z wersją (D29)
    await page.getByText('Vivir Mi Vida').first().click()
    await expect(page.getByTestId('track-details')).toBeVisible()
    await page.getByTestId('dj-notes').fill('pewniak na parkiet')
    await page.getByTestId('dj-save').click()
    await expect(page.getByText('Zapisano dane DJ-a')).toBeVisible()

    // --- wzbogacanie: szacunek PRZED startem, potem realny job na stubie LLM
    await closeDrawer(page)
    await openTab(page, 'Wzbogacanie')
    await expect(page.getByTestId('enrich-estimate')).toContainText('utworów w zleceniu')
    await expect(page.getByTestId('enrich-estimate')).toContainText('$')

    await page.getByLabel('metadane (Spotify)').uncheck()
    await page.getByLabel('audio (BPM, tonacja)').uncheck()
    await page.getByTestId('enrich-start').click()
    await expect(page.getByText(/zakończony — wzbogacono/)).toBeVisible({ timeout: 45_000 })

    // opis od LLM-a wylądował w katalogu
    await openTab(page, 'Biblioteka')
    await page.getByTestId('search-input').fill('vivir')
    await page.getByText('Vivir Mi Vida').first().click()
    await expect(page.getByTestId('track-details')).toContainText('salsa dura')
    // tekst z LRCLIB wraz z tłumaczeniem i interpretacją (D32) — ta sama droga co opis
    await expect(page.getByTestId('lyrics-interpretation')).toContainText(
      'Testowa interpretacja tekstu utworu.',
    )
    await expect(page.getByTestId('lyrics-translation')).toContainText('Pierwszy wers po polsku')
    await closeDrawer(page)

    // --- set: nowy set i utwory z zaznaczenia
    await page.getByTestId('search-input').fill('')
    await expect(page.getByTestId('result-summary')).toContainText('5 utworów')
    await page.getByLabel('zaznacz stronę').check()
    await openTab(page, 'Sety')
    await page.getByTestId('playlist-name').fill('E2E — piątek')
    await page.getByTestId('playlist-create').click()
    await page.getByTestId('playlist-add-selected').click()
    await expect(page.getByTestId('set-stats')).toBeVisible()
    await expect(page.getByTestId('set-list').getByRole('listitem')).toHaveCount(5)

    // kolejność wg faz wieczoru (D9) przechodzi przez PUT z wersją agregatu (D29)
    await page.getByTestId('playlist-arrange').click()
    await expect(page.getByText('Ułożono set wg faz wieczoru (D9)')).toBeVisible()

    // --- generator: propozycja, która niczego nie zapisuje (D26)
    const playlistsBefore = (await (await request.get('/api/playlists')).json()).length
    await page.getByTestId('propose-set').click()
    await expect(page.getByTestId('set-proposal')).toBeVisible()
    expect((await (await request.get('/api/playlists')).json()).length).toBe(playlistsBefore)

    await page.getByTestId('materialize-set').click()
    await expect
      .poll(async () => (await (await request.get('/api/playlists')).json()).length)
      .toBe(playlistsBefore + 1)
  })
})

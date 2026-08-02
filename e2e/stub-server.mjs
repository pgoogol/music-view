// Stub zewnętrznych źródeł na czas testu E2E (M5.3/D30). Test przepływu nie może
// zależeć od dostępności Spotify ani od klucza LLM — inaczej czerwone CI przestaje
// cokolwiek znaczyć. To nie jest WireMock (ten obsługuje testy integracyjne
// backendu), tylko kilkadziesiąt linii, żeby nie stawiać drugiego procesu JVM.

import { createServer } from 'node:http'

const PORT = Number(process.env.E2E_STUB_PORT ?? 8089)

/** Odpowiedź LLM-a musi być tablicą JSON — jeden obiekt na utwór (prompt v1). */
function analysisFor(spotifyIds) {
  return spotifyIds.map((spotifyId) => ({
    spotify_id: spotifyId,
    style: 'salsa dura',
    genre_family: 'latin',
    lyrics_theme: 'Afirmacja życia i tańca.',
    description_pl: 'Mocna salsa na szczyt wieczoru — pewny parkiet.',
    energy: 'high',
    confidence: 'medium',
    bpm_estimate: null,
  }))
}

/**
 * Wyciąga id utworów z promptu, żeby stub odpowiadał na to, o co go pytano.
 * Czytamy treść wiadomości wprost — JSON.stringify całego ciała uciekłby
 * cudzysłowy i wzorzec przestałby pasować.
 */
function idsFromPrompt(body) {
  const text = (body.messages ?? []).map((message) => message.content ?? '').join('\n')
  return [...new Set([...text.matchAll(/"spotify_id"\s*:\s*"([A-Za-z0-9_-]+)"/g)].map((m) => m[1]))]
}

const server = createServer((request, response) => {
  let raw = ''
  request.on('data', (chunk) => (raw += chunk))
  request.on('end', () => {
    const url = request.url ?? ''
    const send = (status, body) => {
      response.writeHead(status, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify(body))
    }

    if (url.startsWith('/v1/chat/completions')) {
      const body = raw ? JSON.parse(raw) : {}
      const ids = idsFromPrompt(body)
      return send(200, {
        choices: [{ message: { role: 'assistant', content: JSON.stringify(analysisFor(ids)) } }],
        usage: { prompt_tokens: 140 * ids.length, completion_tokens: 120 * ids.length },
      })
    }

    // Deezer i MusicBrainz: „nie znam tego nagrania" — kaskada BPM (D6) ma
    // spokojnie zejść do kolejnego źródła zamiast się wywrócić
    if (url.startsWith('/track/isrc:') || url.startsWith('/search')) {
      return send(404, { error: { code: 404, message: 'not found' } })
    }
    if (url.startsWith('/ws/2/')) {
      return send(200, { recordings: [] })
    }
    return send(404, { error: 'brak stubu dla ' + url })
  })
})

server.listen(PORT, () => console.log(`[e2e-stub] nasłuchuje na :${PORT}`))

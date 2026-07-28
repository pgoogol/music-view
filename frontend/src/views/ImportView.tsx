// Zakładka „Import" (M3.1) — trzy tryby zasilania biblioteki obok siebie:
// plik CSV (tryb A), playlista po linku (B/D) i własne playlisty konta (C).

import ImportPanel from '../components/ImportPanel'
import SpotifyPanel from '../components/SpotifyPanel'

interface Props {
  onImported: () => void
}

export default function ImportView({ onImported }: Props) {

  return (
    <div className="panels">
      <ImportPanel onImported={onImported} />
      <SpotifyPanel onImported={onImported} />
    </div>
  )
}

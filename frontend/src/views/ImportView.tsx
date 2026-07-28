// Zakładka „Import" (M3.1, przycięta w M3.2) — dwa tryby zasilania biblioteki:
// własne playlisty konta (tryb C, z raportem w modalu) i playlista po linku
// (tryby B/D). Import z pliku CSV zniknął z UI — endpoint został w API.

import ImportPanel from '../components/ImportPanel'
import SpotifyPanel from '../components/SpotifyPanel'

interface Props {
  onImported: () => void
}

export default function ImportView({ onImported }: Props) {

  return (
    <div className="panels">
      <SpotifyPanel onImported={onImported} />
      <ImportPanel onImported={onImported} />
    </div>
  )
}

// Custom tagi DJ-a jako chipsy (M3.1) — zamiast pola tekstowego „po przecinku".

import { useState } from 'react'

interface Props {
  tags: readonly string[]
  onChange: (tags: string[]) => void
}

/** Przecinek pozwala wkleić kilka tagów naraz; puste i duplikaty odpadają. */
function splitTags(input: string): string[] {
  return input
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean)
}

export default function TagChips({ tags, onChange }: Props) {

  const [draft, setDraft] = useState('')

  const add = () => {
    const added = splitTags(draft).filter((tag) => !tags.includes(tag))
    if (added.length > 0) onChange([...tags, ...added])
    setDraft('')
  }

  return (
    <div className="tag-chips" data-testid="tag-chips">
      <div className="chips">
        {tags.map((tag) => (
          <span key={tag} className="chip">
            {tag}
            <button
              type="button"
              className="link chip-remove"
              aria-label={`usuń tag ${tag}`}
              onClick={() => onChange(tags.filter((current) => current !== tag))}
            >
              ×
            </button>
          </span>
        ))}
        {tags.length === 0 && <span className="muted">brak tagów</span>}
      </div>
      <div className="row">
        <input
          value={draft}
          placeholder="nowy tag"
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault()
              add()
            }
          }}
          data-testid="tag-input"
        />
        <button type="button" onClick={add} disabled={draft.trim() === ''}>
          Dodaj tag
        </button>
      </div>
    </div>
  )
}

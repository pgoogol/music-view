// Macierz tempo × energia (M5.4). Dwa rozkłady osobno mówią „mam dużo szybkich"
// i „mam dużo energetycznych"; dopiero skrzyżowanie mówi, czy szybkie
// i energetyczne to te same utwory, czy dwa różne kawałki biblioteki.

import type { CSSProperties } from 'react'
import type { MatrixCellResponse } from '../../api'
import { energyLabel, tempoLabel } from '../../format'
import { matrixGrid } from '../../overviewInsights'

interface Props {
  cells: readonly MatrixCellResponse[]
  testId?: string
}

const TEMPO_ROWS = ['SLOW', 'MEDIUM', 'FAST', 'VERY_FAST', 'BEZ TEMPA'] as const
const ENERGY_COLUMNS = ['LOW', 'MEDIUM', 'HIGH', 'BEZ ENERGII'] as const

const ROW_LABELS: Record<string, string> = { 'BEZ TEMPA': 'bez tempa' }
const COLUMN_LABELS: Record<string, string> = { 'BEZ ENERGII': 'bez danych' }

export default function HeatMatrix({ cells, testId }: Props) {

  const grid = matrixGrid(cells, TEMPO_ROWS, ENERGY_COLUMNS)
  const max = grid.reduce(
    (highest, row) => row.reduce((rowMax, count) => Math.max(rowMax, count), highest),
    0,
  )

  return (
    <div className="heat" data-testid={testId}>
      <table className="heat-table">
        <caption className="muted">
          Wiersz — klasa tempa, kolumna — energia; jasność komórki to liczba utworów.
        </caption>
        <thead>
          <tr>
            <th scope="col">tempo \ energia</th>
            {ENERGY_COLUMNS.map((column) => (
              <th key={column} scope="col">
                {COLUMN_LABELS[column] ?? energyLabel(column.toLowerCase())}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {TEMPO_ROWS.map((row, rowIndex) => (
            <tr key={row}>
              <th scope="row">{ROW_LABELS[row] ?? tempoLabel(row)}</th>
              {ENERGY_COLUMNS.map((column, columnIndex) => {
                const count = grid[rowIndex][columnIndex]
                return (
                  <td
                    key={column}
                    className={count === 0 ? 'heat-cell empty' : 'heat-cell'}
                    style={{
                      // zero zostaje puste; reszta rośnie od ledwie widocznego tła
                      '--heat': max === 0 ? 0 : count / max,
                      animationDelay: `${(rowIndex * ENERGY_COLUMNS.length + columnIndex) * 25}ms`,
                    } as CSSProperties}
                    title={`${tempoLabel(row)} + energia ${
                      COLUMN_LABELS[column] ?? energyLabel(column.toLowerCase())
                    }: ${count} utworów`}
                  >
                    {count === 0 ? '' : count}
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

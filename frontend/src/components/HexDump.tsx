// The raw bytes of the frame, like the bottom pane of Wireshark: offset | 16 bytes in hex | the same bytes as ASCII.
// The bytes of the selected layer are highlighted.

import { useEffect, useRef } from 'react'

const BYTES_PER_ROW = 16

type HexDumpProps = {
  /** the whole frame as lower-case hex, two characters per byte (PacketDetail.hex) */
  hex: string
  /** the byte range to highlight, or null for none */
  highlight: { offset: number; length: number } | null
}

export function HexDump({ hex, highlight }: HexDumpProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const bytes = hexToBytes(hex)

  const highlightStart = highlight?.offset ?? -1
  const highlightEnd = highlight === null ? -1 : highlight.offset + highlight.length

  // when a layer is selected, scroll so its first row is visible
  useEffect(() => {
    const firstHighlightedRow = containerRef.current?.querySelector('[data-first-highlighted-row]')
    firstHighlightedRow?.scrollIntoView({ block: 'nearest' })
  }, [highlightStart])

  const rows = []
  for (let rowStart = 0; rowStart < bytes.length; rowStart += BYTES_PER_ROW) {
    const rowBytes = bytes.slice(rowStart, rowStart + BYTES_PER_ROW)
    const rowEnd = rowStart + rowBytes.length
    const containsHighlightStart = highlightStart >= rowStart && highlightStart < rowEnd

    rows.push(
      <div key={rowStart} className="flex gap-4 whitespace-pre" data-first-highlighted-row={containsHighlightStart || undefined}>
        <span className="text-neutral-400">{rowStart.toString(16).padStart(4, '0')}</span>

        <span>
          {rowBytes.map((byteValue, indexInRow) => {
            const byteOffset = rowStart + indexInRow
            const isHighlighted = byteOffset >= highlightStart && byteOffset < highlightEnd
            // an extra space after the 8th byte, like Wireshark, to make counting easier
            const separator = indexInRow === 7 ? '  ' : ' '
            return (
              <span key={byteOffset}>
                <span className={isHighlighted ? 'bg-amber-200' : ''}>{byteValue.toString(16).padStart(2, '0')}</span>
                {indexInRow < rowBytes.length - 1 ? separator : ''}
              </span>
            )
          })}
        </span>

        <span>
          {rowBytes.map((byteValue, indexInRow) => {
            const byteOffset = rowStart + indexInRow
            const isHighlighted = byteOffset >= highlightStart && byteOffset < highlightEnd
            return (
              <span key={byteOffset} className={isHighlighted ? 'bg-amber-200' : ''}>
                {toPrintableCharacter(byteValue)}
              </span>
            )
          })}
        </span>
      </div>,
    )
  }

  return (
    <div ref={containerRef} className="h-80 overflow-auto rounded-md border p-2 font-mono text-xs leading-5">
      {rows}
    </div>
  )
}

/** "0a1f" -> [10, 31] */
function hexToBytes(hex: string): number[] {
  const bytes: number[] = []
  for (let position = 0; position < hex.length; position += 2) {
    bytes.push(parseInt(hex.substring(position, position + 2), 16))
  }
  return bytes
}

/** Printable ASCII (space to ~) as itself, everything else as a dot. */
function toPrintableCharacter(byteValue: number): string {
  return byteValue >= 0x20 && byteValue <= 0x7e ? String.fromCharCode(byteValue) : '.'
}

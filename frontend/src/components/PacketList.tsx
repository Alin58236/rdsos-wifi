// The packet list, like the top pane of Wireshark: one row per packet, newest at the bottom.
// Clicking a row selects it; App then shows its layers and hex dump below.

import { useEffect, useRef } from 'react'
import type { PacketSummary } from '@/types'

type PacketListProps = {
  packets: PacketSummary[]
  selectedPacketId: number | null
  onSelectPacket: (packetId: number) => void
}

// how close to the bottom (in pixels) still counts as "at the bottom"
const FOLLOW_NEWEST_MARGIN_PX = 40

export function PacketList({ packets, selectedPacketId, onSelectPacket }: PacketListProps) {
  const scrollBoxRef = useRef<HTMLDivElement>(null)

  // Like Wireshark: keep showing the newest packets while the user is at the bottom,
  // but stop jumping once they scroll up to look at an older one.
  const followNewestRef = useRef(true)

  function rememberIfAtBottom() {
    const scrollBox = scrollBoxRef.current
    if (scrollBox === null) {
      return
    }
    const distanceToBottom = scrollBox.scrollHeight - scrollBox.scrollTop - scrollBox.clientHeight
    followNewestRef.current = distanceToBottom < FOLLOW_NEWEST_MARGIN_PX
  }

  useEffect(() => {
    const scrollBox = scrollBoxRef.current
    if (scrollBox !== null && followNewestRef.current) {
      scrollBox.scrollTop = scrollBox.scrollHeight
    }
  }, [packets])

  if (packets.length === 0) {
    return <p className="py-8 text-center text-sm text-neutral-500">No packets yet. Start a capture or replay a file.</p>
  }

  return (
    <div ref={scrollBoxRef} onScroll={rememberIfAtBottom} className="h-80 overflow-auto rounded-md border">
      <table className="w-full text-sm">
        <thead className="sticky top-0 bg-neutral-100 text-left text-xs text-neutral-600">
          <tr>
            <th className="px-2 py-1.5 text-right font-medium">No.</th>
            <th className="px-2 py-1.5 font-medium">Time</th>
            <th className="px-2 py-1.5 font-medium">Source</th>
            <th className="px-2 py-1.5 font-medium">Destination</th>
            <th className="px-2 py-1.5 font-medium">Protocol</th>
            <th className="px-2 py-1.5 text-right font-medium">Length</th>
            <th className="px-2 py-1.5 font-medium">Info</th>
          </tr>
        </thead>
        <tbody>
          {packets.map((packet) => {
            const isSelected = packet.id === selectedPacketId
            const rowColor = isSelected ? 'bg-blue-600 text-white' : `${protocolRowColor(packet.protocol)} hover:bg-neutral-200`

            return (
              <tr
                key={packet.id}
                onClick={() => onSelectPacket(packet.id)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') onSelectPacket(packet.id)
                }}
                tabIndex={0}
                aria-selected={isSelected}
                className={`cursor-pointer ${rowColor}`}
              >
                <td className="px-2 py-1 text-right tabular-nums">{packet.id}</td>
                <td className="px-2 py-1 font-mono text-xs whitespace-nowrap">{formatTime(packet.timestampMicros)}</td>
                <td className="px-2 py-1 font-mono text-xs whitespace-nowrap">{packet.source ?? '–'}</td>
                <td className="px-2 py-1 font-mono text-xs whitespace-nowrap">{packet.destination ?? '–'}</td>
                <td className="px-2 py-1 whitespace-nowrap">{packet.protocol}</td>
                <td className="px-2 py-1 text-right tabular-nums">{packet.length}</td>
                <td className="max-w-md truncate px-2 py-1" title={packet.info}>{packet.info}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

/** A light background per protocol, so the kinds of traffic stand out like in Wireshark. */
function protocolRowColor(protocol: string): string {
  if (protocol === 'HTTP') return 'bg-green-50'
  if (protocol === 'TCP') return 'bg-violet-50'
  if (protocol.startsWith('Encrypted')) return 'bg-neutral-50 text-neutral-500'
  if (protocol.startsWith('802.11')) return 'bg-sky-50'
  return ''
}

/** Capture time as HH:MM:SS.microseconds, in the browser's time zone. */
function formatTime(timestampMicros: number): string {
  const date = new Date(Math.floor(timestampMicros / 1000))
  const clock = date.toLocaleTimeString('en-GB', { hour12: false })
  const microseconds = String(timestampMicros % 1_000_000).padStart(6, '0')
  return `${clock}.${microseconds}`
}

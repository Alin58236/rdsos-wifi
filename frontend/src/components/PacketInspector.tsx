// The details of the selected packet, like the middle and bottom panes of Wireshark:
// on the left the layer tree (Radiotap, 802.11, ... IPv4, TCP, HTTP), on the right the hex dump.
// Clicking a layer highlights its bytes in the hex dump.

import { useEffect, useState } from 'react'
import { getPacketDetail } from '@/api'
import { HexDump } from '@/components/HexDump'
import { findMockPacketDetail, USE_MOCK_DATA } from '@/mockData'
import type { PacketDetail } from '@/types'

type PacketInspectorProps = {
  /** null when no row is selected */
  packetId: number | null
}

export function PacketInspector({ packetId }: PacketInspectorProps) {
  const [detail, setDetail] = useState<PacketDetail | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [selectedLayerIndex, setSelectedLayerIndex] = useState<number | null>(null)

  // The list only has the short summary; the layers and the bytes are fetched when a row is clicked.
  useEffect(() => {
    if (packetId === null) {
      return
    }

    // if the user clicks another row before this answer arrives, the old answer is ignored
    let answerIsStillWanted = true

    async function loadDetail(id: number) {
      try {
        const loadedDetail = USE_MOCK_DATA ? findMockPacketDetail(id) : await getPacketDetail(id)
        if (answerIsStillWanted) {
          setDetail(loadedDetail)
          setErrorMessage(null)
          setSelectedLayerIndex(null)
        }
      } catch (error) {
        if (answerIsStillWanted) {
          setDetail(null)
          setErrorMessage(error instanceof Error ? error.message : String(error))
        }
      }
    }

    loadDetail(packetId)

    return () => {
      answerIsStillWanted = false
    }
  }, [packetId])

  if (packetId === null) {
    return <p className="py-8 text-center text-sm text-neutral-500">Click a packet to see its layers and bytes.</p>
  }

  if (errorMessage !== null) {
    return <p className="py-8 text-center text-sm text-red-600">{errorMessage}</p>
  }

  // the first answer has not arrived yet
  if (detail === null || detail.id !== packetId) {
    return <p className="py-8 text-center text-sm text-neutral-500">Loading packet {packetId}…</p>
  }

  const selectedLayer = selectedLayerIndex === null ? null : detail.layers[selectedLayerIndex]

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <div className="h-80 overflow-auto rounded-md border p-2 text-sm">
        <p className="mb-2 text-xs text-neutral-500">
          Packet {detail.id} · {detail.length} bytes · click a layer to highlight its bytes
        </p>

        {detail.layers.map((layer, layerIndex) => {
          const isSelected = layerIndex === selectedLayerIndex

          return (
            // a native <details>: open by default, the summary line folds it
            <details key={layerIndex} open className="mb-1">
              <summary
                onClick={() => setSelectedLayerIndex(layerIndex)}
                className={`cursor-pointer rounded px-1 py-0.5 font-medium ${isSelected ? 'bg-amber-200' : 'hover:bg-neutral-100'}`}
              >
                {layer.name}
                <span className="ml-2 text-xs font-normal text-neutral-500">
                  bytes {layer.offset}–{layer.offset + layer.length - 1} ({layer.length})
                </span>
              </summary>

              <dl className="ml-5 grid grid-cols-[max-content_1fr] gap-x-4 gap-y-0.5 py-1">
                {layer.fields.map((field) => (
                  <div key={field.name} className="contents">
                    <dt className="text-neutral-500">{field.name}</dt>
                    {/* pre-wrap keeps the line breaks of an HTTP payload */}
                    <dd className="font-mono text-xs break-all whitespace-pre-wrap">{field.value}</dd>
                  </div>
                ))}
              </dl>
            </details>
          )
        })}
      </div>

      <HexDump
        hex={detail.hex}
        highlight={selectedLayer === null ? null : { offset: selectedLayer.offset, length: selectedLayer.length }}
      />
    </div>
  )
}

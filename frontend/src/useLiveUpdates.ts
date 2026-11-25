// Connects to the backend WebSocket (/ws/updates) and keeps the latest message in React state.
// The backend sends one message every 200 ms; if the connection drops, we try again every 2 seconds.
// Each message carries only the packets that are new since the previous one, so they are collected here.

import { useCallback, useEffect, useState } from 'react'
import { buildMockUpdate, SAMPLE_PACKETS, USE_MOCK_DATA } from './mockData'
import type { PacketSummary, UpdateMessage } from './types'

const RECONNECT_DELAY_MS = 2000
const MOCK_UPDATE_INTERVAL_MS = 1000
// the newest rows of the packet list; older ones are dropped so the page stays fast (docs/API.md §2)
const MAX_PACKETS_KEPT = 1000

export type LiveUpdates = {
  /** null until the first message arrives */
  latestUpdate: UpdateMessage | null
  connected: boolean
  /** the last MAX_PACKETS_KEPT packets, oldest first */
  packets: PacketSummary[]
  clearPackets: () => void
}

export function useLiveUpdates(): LiveUpdates {
  const [latestUpdate, setLatestUpdate] = useState<UpdateMessage | null>(USE_MOCK_DATA ? buildMockUpdate() : null)
  const [connected, setConnected] = useState(USE_MOCK_DATA)
  const [packets, setPackets] = useState<PacketSummary[]>(USE_MOCK_DATA ? SAMPLE_PACKETS : [])

  const clearPackets = useCallback(() => setPackets([]), [])

  useEffect(() => {
    if (USE_MOCK_DATA) {
      const mockTimer = window.setInterval(() => setLatestUpdate(buildMockUpdate()), MOCK_UPDATE_INTERVAL_MS)
      return () => window.clearInterval(mockTimer)
    }

    let socket: WebSocket | null = null
    let reconnectTimer: number | undefined
    let stopped = false

    function connect() {
      const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
      socket = new WebSocket(`${protocol}://${window.location.host}/ws/updates`)

      socket.onopen = () => setConnected(true)

      socket.onmessage = (event) => {
        const message: UpdateMessage = JSON.parse(event.data)
        setLatestUpdate(message)

        if (message.packets.length > 0) {
          setPackets((previousPackets) => [...previousPackets, ...message.packets].slice(-MAX_PACKETS_KEPT))
        }
      }

      socket.onclose = () => {
        setConnected(false)
        if (!stopped) {
          reconnectTimer = window.setTimeout(connect, RECONNECT_DELAY_MS)
        }
      }
    }

    connect()

    // runs when the page component is removed: close the socket and stop reconnecting
    return () => {
      stopped = true
      window.clearTimeout(reconnectTimer)
      socket?.close()
    }
  }, [])

  return { latestUpdate: latestUpdate, connected: connected, packets: packets, clearPackets: clearPackets }
}

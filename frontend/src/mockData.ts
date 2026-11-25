// Sample data for working on the UI without the backend. Open http://localhost:5173/?mock to use it.

import type { AccessPoint, PacketDetail, PacketSummary, UpdateMessage } from './types'
// real answers of GET /api/packets/{id}, saved while replaying the fixtures in backend/src/test/resources/fixtures
import MOCK_PACKET_DETAILS from './mockPacketDetails.json'

/** ?mock in the URL: fake data, no backend needed */
export const USE_MOCK_DATA = new URLSearchParams(window.location.search).has('mock')

const SAMPLE_ACCESS_POINTS: AccessPoint[] = [
  { bssid: '50:d4:f7:03:12:f3', ssid: 'HomeNetwork', channel: 10, frequencyMHz: 2457, signalDBm: -68, security: 'WPA/WPA2-PSK', vendor: 'TP-LINK TECHNOLOGIES CO.,LTD.', lastSeenMillis: 0 },
  { bssid: '50:d4:f7:11:22:33', ssid: 'PoseidonZone', channel: 6, frequencyMHz: 2437, signalDBm: -45, security: 'WPA2-PSK', vendor: 'TP-LINK TECHNOLOGIES CO.,LTD.', lastSeenMillis: 0 },
  { bssid: 'ae:80:66:e8:2f:e3', ssid: 'iPhone', channel: 1, frequencyMHz: 2412, signalDBm: -58, security: 'WPA2/WPA3', vendor: 'Local/Unknown', lastSeenMillis: 0 },
  { bssid: '00:1a:11:aa:bb:cc', ssid: '', channel: 11, frequencyMHz: 2462, signalDBm: -82, security: 'Open', vendor: 'Google, Inc.', lastSeenMillis: 0 },
  { bssid: '00:11:22:33:44:55', ssid: 'Neighbour-5G', channel: 3, frequencyMHz: 2422, signalDBm: -77, security: 'WPA3-SAE', vendor: null, lastSeenMillis: 0 },
]

/** A message like the backend's, with the signal moving a little so the UI updates visibly. */
export function buildMockUpdate(): UpdateMessage {
  const nowMillis = Date.now()
  const accessPoints = SAMPLE_ACCESS_POINTS.map((accessPoint) => {
    const randomChange = Math.round(Math.random() * 6 - 3)
    return {
      ...accessPoint,
      signalDBm: accessPoint.signalDBm === null ? null : accessPoint.signalDBm + randomChange,
      lastSeenMillis: nowMillis,
    }
  })

  return {
    status: {
      running: true,
      source: 'FILE',
      interface: 'mock data',
      mode: 'MONITOR',
      currentChannel: (Math.floor(nowMillis / 250) % 13) + 1,
      pinnedChannel: null,
      frames: 0,
      dropped: 0,
      parseErrors: 0,
    },
    aps: accessPoints,
    packets: [],
  }
}

/** The rows that go with mockPacketDetails.json: a beacon, an encrypted data frame, a TCP SYN and an HTTP GET. */
export const SAMPLE_PACKETS: PacketSummary[] = [
  { id: 1, timestampMicros: 1795357761364086, length: 378, protocol: '802.11 Beacon', source: '50:d4:f7:03:12:f3', destination: 'ff:ff:ff:ff:ff:ff', info: 'SSID=HomeNetwork CH=10' },
  { id: 2, timestampMicros: 1795357768361120, length: 155, protocol: 'Encrypted (WPA2)', source: 'ae:80:66:e8:2f:e3', destination: '50:d4:f7:03:12:f4', info: 'Protected data' },
  { id: 3, timestampMicros: 1795545548833436, length: 74, protocol: 'TCP', source: '192.168.0.165', destination: '34.223.124.45', info: '39194 → 80 [SYN]' },
  { id: 4, timestampMicros: 1795545549061146, length: 141, protocol: 'HTTP', source: '192.168.0.165', destination: '34.223.124.45', info: 'GET / HTTP/1.1' },
]

/** Stands in for GET /api/packets/{id} when there is no backend. */
export function findMockPacketDetail(packetId: number): PacketDetail {
  const details: Record<string, PacketDetail> = MOCK_PACKET_DETAILS
  const detail = details[String(packetId)]
  if (detail === undefined) {
    throw new Error(`Packet ${packetId} is no longer in the buffer`)
  }
  return detail
}

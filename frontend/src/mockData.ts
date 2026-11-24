// Sample data for working on the UI without the backend. Open http://localhost:5173/?mock to use it.

import type { AccessPoint, UpdateMessage } from './types'

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
      linkType: 127,
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

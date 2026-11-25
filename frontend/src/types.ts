// The data the backend sends, exactly as described in docs/API.md §3.

export type InterfaceMode = 'MANAGED' | 'MONITOR' | 'OTHER'

/** One wireless interface, from GET /api/interfaces. */
export type WifiInterface = {
  name: string
  mode: InterfaceMode
}

/** One detected network (one row of the AP table, one curve in the graph). */
export type AccessPoint = {
  bssid: string
  /** "" for a hidden network */
  ssid: string
  channel: number
  frequencyMHz: number | null
  signalDBm: number | null
  security: string
  /** null when the manufacturer is not in the OUI registry */
  vendor: string | null
  lastSeenMillis: number
}

/** The state of the capture, sent with every WebSocket message. */
export type CaptureStatus = {
  running: boolean
  source: 'LIVE' | 'FILE' | null
  interface: string | null
  mode: InterfaceMode | null
  currentChannel: number | null
  pinnedChannel: number | null
  frames: number
  dropped: number
  parseErrors: number
}

/** One row of the packet list. */
export type PacketSummary = {
  id: number
  timestampMicros: number
  length: number
  protocol: string
  source: string | null
  destination: string | null
  info: string
}

/** One line in a layer, already formatted by the backend, e.g. "Time to live" = "64". */
export type PacketField = {
  name: string
  value: string
}

/** One header in the layer tree. offset and length are bytes in the hex dump, so it can be highlighted. */
export type PacketLayer = {
  name: string
  offset: number
  length: number
  fields: PacketField[]
}

/** The full decode of one packet, from GET /api/packets/{id}. */
export type PacketDetail = {
  id: number
  timestampMicros: number
  length: number
  /** in wire order: Radiotap first in monitor mode, Ethernet first in managed mode */
  layers: PacketLayer[]
  /** the whole frame as lower-case hex, two characters per byte */
  hex: string
}

/** One WebSocket message (the backend sends one every 200 ms). */
export type UpdateMessage = {
  status: CaptureStatus
  aps: AccessPoint[]
  packets: PacketSummary[]
}

# Backend ↔ frontend contract

Agreed 2026-11-23. The frontend (Thursday) is built against this document; change it here first if the backend has to deviate.

The backend listens on `127.0.0.1:8080` only. The Vite dev server (`http://localhost:5173`) proxies `/api` and `/ws` to it; CORS and WebSocket origins allow only that origin.

## 1. REST (control)

All bodies are JSON.

| Method | Path | Request body | Response | Purpose |
|---|---|---|---|---|
| `GET` | `/api/interfaces` | – | `[{"name":"wlp4s0","mode":"MANAGED"}]` | Wireless interfaces. `mode`: `MANAGED`, `MONITOR`, `OTHER`. |
| `POST` | `/api/interfaces/{name}/mode` | `{"mode":"MONITOR"}` | `204` | Switch mode (driver reload, nmcli, iw). Refused while a capture is running. |
| `POST` | `/api/capture/start` | `{"source":"LIVE","interface":"wlp4s0"}` or `{"source":"FILE","file":"beacons-wpa2.pcap"}` | `204` | Start capturing. In monitor mode the channel hopper starts too. |
| `POST` | `/api/capture/stop` | – | `204` | Stop the capture and the hopper. |
| `GET` | `/api/capture/status` | – | `Status` (§3) | Current state. |
| `PUT` | `/api/channel` | `{"channel":10}` | `204` | Pin a channel for sniffing; the hopper pauses. Monitor mode only. |
| `DELETE` | `/api/channel` | – | `204` | Unpin; the hopper resumes. |
| `GET` | `/api/replay-files` | – | `["beacons-wpa2.pcap","wpa2-data.pcap"]` | `.pcap` files available for replay. |
| `GET` | `/api/packets/{id}` | – | `PacketDetail` (§3) | Full decode of one packet, when a row is clicked. `404` if it has left the buffer. |

### Errors

Every error response has the body `{"error":"<message>"}`.

| Status | When |
|---|---|
| `400` | Invalid input: unknown interface, channel outside 1–13, unknown replay file, malformed body. |
| `404` | Packet id no longer in the buffer. |
| `409` | Wrong state: start while running, mode switch while running, pin a channel in managed mode. |
| `500` | A system command (`ip`, `iw`, `nmcli`, `modprobe`) or libpcap failed. |

### Validation rules

- `interface` and `{name}` must be in the list from `GET /api/interfaces` (checked against `/sys/class/net`).
- `channel` must be in `wifi.hop-channels` (1–13).
- `file` must be a bare name from `GET /api/replay-files`. Never a path: the backend runs as root, so a free path would let a request read any file on disk.

## 2. WebSocket (live data)

Endpoint: `/ws/updates`. Plain text WebSocket, no STOMP. Server → client only; the client sends nothing.

The server sends **one message every 200 ms** while at least one client is connected, whether or not a capture is running:

```json
{
  "status": { ... Status ... },
  "aps": [ ... AccessPoint ... ],
  "packets": [ ... PacketSummary ... ]
}
```

- `aps`: the **full** current snapshot of the AP registry, sorted by `signalDBm` descending. The client replaces its list on every message.
- `packets`: **only the packets decoded since the previous message**, oldest first, at most 500. The client appends them and keeps the last 1,000 rows. Packets beyond 500 per tick are not sent and are counted in `status.dropped`.

## 3. Types

### Status

```json
{
  "running": true,
  "source": "LIVE",
  "interface": "wlp4s0",
  "mode": "MONITOR",
  "linkType": 127,
  "currentChannel": 6,
  "pinnedChannel": null,
  "frames": 18342,
  "dropped": 0,
  "parseErrors": 3
}
```

| Field | Type | Notes |
|---|---|---|
| `running` | boolean | |
| `source` | `"LIVE"` \| `"FILE"` \| null | null when stopped |
| `interface` | string \| null | for `FILE`, the file name |
| `mode` | `"MANAGED"` \| `"MONITOR"` \| `"OTHER"` \| null | |
| `linkType` | int \| null | `127` = Radiotap (monitor), `1` = Ethernet (managed) |
| `currentChannel` | int \| null | channel the hopper is on; null in managed mode |
| `pinnedChannel` | int \| null | |
| `frames` | long | frames read since start |
| `dropped` | long | frames lost (full queue) plus packets not sent over WebSocket |
| `parseErrors` | long | frames rejected by a parser |

### AccessPoint

```json
{
  "bssid": "50:d4:f7:03:12:f3",
  "ssid": "ArtemisZone",
  "channel": 10,
  "frequencyMHz": 2457,
  "signalDBm": -68,
  "security": "WPA/WPA2-PSK",
  "vendor": "TP-Link",
  "lastSeenMillis": 1795357761364
}
```

| Field | Type | Notes |
|---|---|---|
| `bssid` | string | lower-case, colon-separated |
| `ssid` | string | `""` for a hidden network; the UI shows `<hidden>` |
| `channel` | int | from IE 3, falling back to IE 61, then the Radiotap frequency |
| `frequencyMHz` | int \| null | derived from `channel`, not from Radiotap (see `CLAUDE.md`, neighbouring-channel leakage) |
| `signalDBm` | int \| null | Radiotap combined antenna signal of the latest beacon |
| `security` | string | display text: `Open`, `WEP`, `WPA-PSK`, `WPA2-PSK`, `WPA/WPA2-PSK`, `WPA3-SAE`, `WPA2/WPA3`, `WPA2-Enterprise`, … |
| `vendor` | string \| null | from the bundled OUI file; `Locally administered` for random MACs |
| `lastSeenMillis` | long | backend wall-clock time (`System.currentTimeMillis()`) when the latest beacon was processed, so replayed files do not expire at once. APs unseen for 30 s are removed. |

The UI colours the signal icon from `signalDBm`: green ≥ −60, yellow −60 to −75, red < −75.

### PacketSummary (one row of the packet list)

```json
{
  "id": 1042,
  "timestampMicros": 1795357761364086,
  "length": 378,
  "protocol": "802.11 Beacon",
  "source": "50:d4:f7:03:12:f3",
  "destination": "ff:ff:ff:ff:ff:ff",
  "info": "SSID=ArtemisZone CH=10"
}
```

| Field | Type | Notes |
|---|---|---|
| `id` | long | increasing, unique per backend run |
| `timestampMicros` | long | capture time from libpcap or the file |
| `length` | int | original frame length |
| `protocol` | string | the highest layer decoded: `802.11 Beacon`, `802.11 ACK`, `802.11 QoS Data`, `Encrypted (WPA2)`, `IPv4`, `TCP`, `HTTP`, `Ethernet` |
| `source`, `destination` | string \| null | IP addresses when an IP layer was decoded, otherwise MAC addresses |
| `info` | string | short free text, e.g. `SSID=… CH=…`, `443 → 51234 [ACK]`, `GET /index.html` |

### PacketDetail (`GET /api/packets/{id}`)

```json
{
  "id": 1042,
  "timestampMicros": 1795357761364086,
  "length": 378,
  "layers": [
    {
      "name": "Radiotap",
      "offset": 0,
      "length": 38,
      "fields": [
        {"name": "Frequency", "value": "2457 MHz"},
        {"name": "Signal", "value": "-90 dBm"},
        {"name": "FCS at end", "value": "yes"}
      ]
    },
    {
      "name": "802.11 Beacon",
      "offset": 38,
      "length": 24,
      "fields": [
        {"name": "Type / subtype", "value": "Management (0) / Beacon (8)"},
        {"name": "Destination", "value": "ff:ff:ff:ff:ff:ff"},
        {"name": "Source", "value": "50:d4:f7:03:12:f3"},
        {"name": "BSSID", "value": "50:d4:f7:03:12:f3"}
      ]
    }
  ],
  "hex": "000026002f4000a0..."
}
```

- `layers` are in wire order. `offset` and `length` are bytes within `hex`, so the hex dump can highlight the selected layer.
- Fields carry no offsets; per-field highlighting is the first feature to cut.
- `hex` is the whole captured frame as lower-case hex, no separators.
- In managed mode there is no Radiotap or 802.11 layer; the first layer is `Ethernet (managed mode)`.
- A layer that cannot be parsed further ends the list. Protected data frames end with a layer named `Encrypted (WPA2)` with no fields.

## 4. Who builds what

**Option B** (agreed): the parsers return typed `@Value` headers (`RadioTapHeader`, `Dot11Header`, `Ipv4Header`, `TcpHeader`, …) and know nothing about the UI. A `PacketViewMapper` in `api/` turns them into `PacketSummary` and `PacketDetail`. The mapper formats only; it never reads bytes, apart from hex-encoding the raw frame.

What `api/` needs from the capture side (exact shape decided when `CaptureService` is written):

- start a capture with a `CaptureSource` (live interface or replay file) and stop it;
- `isRunning()` and the `frames`, `dropped` and `parseErrors` counters;
- `ApRegistry.snapshot()` returning `List<AccessPoint>`;
- a packet store holding the last 5,000 decoded packets (raw frame plus typed headers) with increasing ids: get by id, and get everything after a given id.

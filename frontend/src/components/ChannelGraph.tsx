// The channel graph, drawn by hand in SVG (no chart library), like the one in Android WiFi Analyzer.
// Each access point is a hill centred on its channel, about 2 channels wide on each side (a 20 MHz
// signal covers channels ch-2 … ch+2), and as tall as its signal strength. Overlapping hills show
// which channels are crowded.

import type { AccessPoint } from '@/types'

// Size of the drawing, in SVG units; the SVG scales to the width of the card.
const GRAPH_WIDTH = 900
const GRAPH_HEIGHT = 320
const MARGIN_LEFT = 50
const MARGIN_RIGHT = 20
const MARGIN_TOP = 20
const MARGIN_BOTTOM = 40

// The x axis goes from channel -1 to 15, so the hills of channels 1 and 13 fit on screen.
const FIRST_AXIS_CHANNEL = -1
const LAST_AXIS_CHANNEL = 15
const LABELLED_CHANNELS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13]

// The y axis goes from -100 dBm (bottom, no signal) to -30 dBm (top, very strong).
const WEAKEST_DBM = -120
const STRONGEST_DBM = -35
const DBM_GRID_LINES = [-110, -100, -70, -60, -50, -40]

// How far a hill reaches on each side of its channel.
const HILL_HALF_WIDTH_IN_CHANNELS = 2

const HILL_COLORS = ['#2563eb', '#16a34a', '#dc2626', '#9333ea', '#ea580c', '#0891b2', '#ca8a04', '#db2777']

/** Channel number → x position in the drawing. */
function channelToX(channel: number): number {
  const plotWidth = GRAPH_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
  const fraction = (channel - FIRST_AXIS_CHANNEL) / (LAST_AXIS_CHANNEL - FIRST_AXIS_CHANNEL)
  return MARGIN_LEFT + fraction * plotWidth
}

/** Signal in dBm → y position in the drawing (SVG y grows downwards, so strong signals get small y). */
function dbmToY(signalDBm: number): number {
  const plotHeight = GRAPH_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM
  const clampedDBm = Math.min(STRONGEST_DBM, Math.max(WEAKEST_DBM, signalDBm))
  const fraction = (clampedDBm - WEAKEST_DBM) / (STRONGEST_DBM - WEAKEST_DBM)
  return GRAPH_HEIGHT - MARGIN_BOTTOM - fraction * plotHeight
}

/**
 * The SVG path of one hill: a curve from (channel - 2, bottom) up to (channel, signal) and back down
 * to (channel + 2, bottom). It is a cubic Bézier curve with both control points at the same height.
 * Such a curve only rises 3/4 of the way to its control points, so the control points are placed
 * higher (divided by 0.75) to make the top of the hill land exactly on the signal value.
 */
function hillPath(channel: number, signalDBm: number): string {
  const bottomY = dbmToY(WEAKEST_DBM)
  const peakY = dbmToY(signalDBm)
  const controlPointY = bottomY + (peakY - bottomY) / 0.75

  const startX = channelToX(channel - HILL_HALF_WIDTH_IN_CHANNELS)
  const endX = channelToX(channel + HILL_HALF_WIDTH_IN_CHANNELS)
  const leftControlX = channelToX(channel - 1)
  const rightControlX = channelToX(channel + 1)

  return `M ${startX} ${bottomY} C ${leftControlX} ${controlPointY}, ${rightControlX} ${controlPointY}, ${endX} ${bottomY} Z`
}

type ChannelGraphProps = {
  accessPoints: AccessPoint[]
}

export function ChannelGraph({ accessPoints }: ChannelGraphProps) {
  // only 2.4 GHz networks (channels 1–14) fit on this axis
  const accessPointsToDraw = accessPoints.filter(
    (accessPoint) => accessPoint.signalDBm !== null && accessPoint.channel >= 1 && accessPoint.channel <= 14,
  )

  return (
    <svg
      viewBox={`0 0 ${GRAPH_WIDTH} ${GRAPH_HEIGHT}`}
      className="h-auto w-full"
      role="img"
      aria-label="Signal strength of each network across the 13 Wi-Fi channels"
    >
      {/* horizontal grid lines with dBm labels */}
      {DBM_GRID_LINES.map((gridDBm) => (
        <g key={gridDBm}>
          <line
            x1={MARGIN_LEFT}
            x2={GRAPH_WIDTH - MARGIN_RIGHT}
            y1={dbmToY(gridDBm)}
            y2={dbmToY(gridDBm)}
            stroke="#e5e5e5"
          />
          <text x={MARGIN_LEFT - 8} y={dbmToY(gridDBm) + 4} textAnchor="end" fontSize="12" fill="#737373">
            {gridDBm}
          </text>
        </g>
      ))}

      {/* x axis with the channel numbers */}
      <line
        x1={MARGIN_LEFT}
        x2={GRAPH_WIDTH - MARGIN_RIGHT}
        y1={dbmToY(WEAKEST_DBM)}
        y2={dbmToY(WEAKEST_DBM)}
        stroke="#a3a3a3"
      />
      {LABELLED_CHANNELS.map((channel) => (
        <text
          key={channel}
          x={channelToX(channel)}
          y={GRAPH_HEIGHT - MARGIN_BOTTOM + 20}
          textAnchor="middle"
          fontSize="13"
          fill="#404040"
        >
          {channel}
        </text>
      ))}
      <text x={MARGIN_LEFT - 8} y={MARGIN_TOP - 6} textAnchor="end" fontSize="11" fill="#737373">
        dBm
      </text>

      {/* one hill per access point, with its name above the top */}
      {accessPointsToDraw.map((accessPoint, index) => {
        const signalDBm = accessPoint.signalDBm as number
        const color = HILL_COLORS[index % HILL_COLORS.length]
        const label = accessPoint.ssid === '' ? '<hidden>' : accessPoint.ssid

        return (
          <g key={accessPoint.bssid}>
            <path d={hillPath(accessPoint.channel, signalDBm)} fill={color} fillOpacity={0.15} stroke={color} strokeWidth={2} />
            <text
              x={channelToX(accessPoint.channel)}
              y={dbmToY(signalDBm) - 6}
              textAnchor="middle"
              fontSize="12"
              fontWeight="600"
              fill={color}
            >
              {label}
            </text>
          </g>
        )
      })}
    </svg>
  )
}

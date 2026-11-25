// Signal strength → colour, the same thresholds as Android WiFi Analyzer:
// green ≥ -60 dBm, yellow -60 … -75 dBm, red < -75 dBm.

export type SignalLevel = 'good' | 'fair' | 'weak' | 'unknown'

export function signalLevel(signalDBm: number | null): SignalLevel {
  if (signalDBm === null) {
    return 'unknown'
  }
  if (signalDBm >= -60) {
    return 'good'
  }
  if (signalDBm >= -85) {
    return 'fair'
  }
  return 'weak'
}

/** Tailwind text colour for the signal icon. */
export const SIGNAL_TEXT_COLOR: Record<SignalLevel, string> = {
  good: 'text-green-600',
  fair: 'text-yellow-500',
  weak: 'text-red-600',
  unknown: 'text-neutral-400',
}

// The list of detected networks: one row per access point, with the 7 fields the project requires
// (SSID, MAC address, manufacturer, signal, channel, frequency, security) and a signal icon coloured by strength.

import { Wifi } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { SIGNAL_TEXT_COLOR, signalLevel } from '@/signal'
import type { AccessPoint } from '@/types'

type AccessPointTableProps = {
  accessPoints: AccessPoint[]
}

export function AccessPointTable({ accessPoints }: AccessPointTableProps) {
  if (accessPoints.length === 0) {
    return <p className="py-8 text-center text-sm text-neutral-500">No networks detected yet.</p>
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-10" aria-label="Signal level" />
          <TableHead>SSID</TableHead>
          <TableHead>BSSID (MAC)</TableHead>
          <TableHead>Manufacturer</TableHead>
          <TableHead className="text-right">Signal</TableHead>
          <TableHead className="text-right">Channel</TableHead>
          <TableHead className="text-right">Frequency</TableHead>
          <TableHead>Security</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {accessPoints.map((accessPoint) => {
          const iconColor = SIGNAL_TEXT_COLOR[signalLevel(accessPoint.signalDBm)]
          const ssidText = accessPoint.ssid === '' ? '<hidden>' : accessPoint.ssid
          const vendorText = accessPoint.vendor ?? 'Unknown'
          const signalText = accessPoint.signalDBm === null ? '–' : `${accessPoint.signalDBm} dBm`
          const frequencyText = accessPoint.frequencyMHz === null ? '–' : `${accessPoint.frequencyMHz} MHz`

          return (
            <TableRow key={accessPoint.bssid}>
              <TableCell>
                <Wifi className={`size-5 ${iconColor}`} aria-label={`Signal ${signalText}`} />
              </TableCell>
              <TableCell className="font-medium">{ssidText}</TableCell>
              <TableCell className="font-mono text-xs">{accessPoint.bssid}</TableCell>
              <TableCell className="max-w-56 truncate" title={vendorText}>{vendorText}</TableCell>
              <TableCell className="text-right tabular-nums">{signalText}</TableCell>
              <TableCell className="text-right tabular-nums">{accessPoint.channel}</TableCell>
              <TableCell className="text-right tabular-nums">{frequencyText}</TableCell>
              <TableCell>
                <Badge variant={accessPoint.security === 'Open' ? 'destructive' : 'secondary'}>
                  {accessPoint.security}
                </Badge>
              </TableCell>
            </TableRow>
          )
        })}
      </TableBody>
    </Table>
  )
}

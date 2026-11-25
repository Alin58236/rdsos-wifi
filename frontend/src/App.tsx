// The whole page: a header with the connection state, the controls, the channel graph, the AP table
// and the packet sniffer (packet list + layers + hex dump).
// All live data comes from one place, the WebSocket hook (useLiveUpdates).

import { useState } from 'react'
import { AccessPointTable } from '@/components/AccessPointTable'
import { ChannelGraph } from '@/components/ChannelGraph'
import { ControlPanel } from '@/components/ControlPanel'
import { PacketInspector } from '@/components/PacketInspector'
import { PacketList } from '@/components/PacketList'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useLiveUpdates } from '@/useLiveUpdates'

function App() {
  const { latestUpdate, connected, packets, clearPackets } = useLiveUpdates()
  const [selectedPacketId, setSelectedPacketId] = useState<number | null>(null)

  const status = latestUpdate?.status ?? null
  const accessPoints = latestUpdate?.aps ?? []

  const captureText = status?.running ? `Capturing (${status.source}, ${status.interface})` : 'Capture stopped'
  const channelText = status?.currentChannel == null ? '' : ` · channel ${status.currentChannel}`
  const counterText = status === null ? '' : `${status.frames} frames · ${status.parseErrors} parse errors`

  function clearPacketList() {
    clearPackets()
    setSelectedPacketId(null)
  }

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-6 p-6">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold">Wi-Fi Analyzer</h1>
        <div className="flex items-center gap-3 text-sm text-neutral-600">
          <span>
            {captureText}
            {channelText}
          </span>
          <Badge variant={connected ? 'secondary' : 'destructive'}>
            {connected ? 'Backend connected' : 'Backend disconnected'}
          </Badge>
        </div>
      </header>

      <Card>
        <CardContent>
          <ControlPanel status={status} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Channels (2.4 GHz)</CardTitle>
        </CardHeader>
        <CardContent>
          <ChannelGraph accessPoints={accessPoints} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Networks ({accessPoints.length})</CardTitle>
        </CardHeader>
        <CardContent>
          <AccessPointTable accessPoints={accessPoints} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2">
          <CardTitle>Packets ({packets.length})</CardTitle>
          <div className="flex items-center gap-3 text-sm text-neutral-600">
            <span>{counterText}</span>
            <Button variant="outline" size="sm" onClick={clearPacketList} disabled={packets.length === 0}>
              Clear
            </Button>
          </div>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <PacketList packets={packets} selectedPacketId={selectedPacketId} onSelectPacket={setSelectedPacketId} />
          <PacketInspector packetId={selectedPacketId} />
        </CardContent>
      </Card>
    </div>
  )
}

export default App

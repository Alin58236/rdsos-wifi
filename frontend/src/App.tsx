// The whole page: a header with the connection state, the controls, the channel graph and the AP table.
// All live data comes from one place, the WebSocket hook (useLiveUpdates).

import { AccessPointTable } from '@/components/AccessPointTable'
import { ChannelGraph } from '@/components/ChannelGraph'
import { ControlPanel } from '@/components/ControlPanel'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useLiveUpdates } from '@/useLiveUpdates'

function App() {
  const { latestUpdate, connected } = useLiveUpdates()

  const status = latestUpdate?.status ?? null
  const accessPoints = latestUpdate?.aps ?? []

  const captureText = status?.running ? `Capturing (${status.source}, ${status.interface})` : 'Capture stopped'
  const channelText = status?.currentChannel == null ? '' : ` · channel ${status.currentChannel}`

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
    </div>
  )
}

export default App

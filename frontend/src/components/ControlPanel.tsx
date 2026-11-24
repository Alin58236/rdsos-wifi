// The controls: pick the interface, switch it between managed and monitor mode, start or stop the
// capture (live or from a .pcap file), and pin one channel for sniffing.
// Every button calls one function from api.ts; a failed call shows the backend's error message.

import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import * as api from '@/api'
import type { CaptureStatus, InterfaceMode, WifiInterface } from '@/types'

const PINNABLE_CHANNELS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13]

const HOPPING_OPTION = 'hop'

type ControlPanelProps = {
  status: CaptureStatus | null
}

export function ControlPanel({ status }: ControlPanelProps) {
  const [interfaces, setInterfaces] = useState<WifiInterface[]>([])
  const [selectedInterfaceName, setSelectedInterfaceName] = useState<string>('')
  const [replayFiles, setReplayFiles] = useState<string[]>([])
  const [selectedReplayFile, setSelectedReplayFile] = useState<string>('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [requestInProgress, setRequestInProgress] = useState(false)

  const selectedInterface = interfaces.find((wifiInterface) => wifiInterface.name === selectedInterfaceName)
  const captureRunning = status?.running ?? false
  const pinnedChannelOption = status?.pinnedChannel == null ? HOPPING_OPTION : String(status.pinnedChannel)

  async function loadInterfaces() {
    const interfaceList = await api.getInterfaces()
    setInterfaces(interfaceList)
    if (interfaceList.length > 0 && !interfaceList.some((wifiInterface) => wifiInterface.name === selectedInterfaceName)) {
      setSelectedInterfaceName(interfaceList[0].name)
    }
  }

  async function runRequest(request: () => Promise<void>) {
    setRequestInProgress(true)
    setErrorMessage(null)
    try {
      await request()
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : String(error))
    } finally {
      setRequestInProgress(false)
    }
  }

  // load the interfaces and the replay files once, when the page opens
  useEffect(() => {
    async function loadInitialData() {
      try {
        const interfaceList = await api.getInterfaces()
        setInterfaces(interfaceList)
        if (interfaceList.length > 0) {
          setSelectedInterfaceName(interfaceList[0].name)
        }
        const fileList = await api.getReplayFiles()
        setReplayFiles(fileList)
        if (fileList.length > 0) {
          setSelectedReplayFile(fileList[0])
        }
      } catch (error) {
        setErrorMessage(error instanceof Error ? error.message : String(error))
      }
    }
    loadInitialData()
  }, [])

  function switchMode(mode: InterfaceMode) {
    runRequest(async () => {
      await api.switchInterfaceMode(selectedInterfaceName, mode)
      await loadInterfaces()
    })
  }

  function changePinnedChannel(option: string) {
    runRequest(async () => {
      if (option === HOPPING_OPTION) {
        await api.unpinChannel()
      } else {
        await api.pinChannel(Number(option))
      }
    })
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end gap-6">
        {/* interface and mode */}
        <div className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-neutral-500">Interface</span>
          <div className="flex items-center gap-2">
            <Select value={selectedInterfaceName} onValueChange={setSelectedInterfaceName}>
              <SelectTrigger className="w-40" aria-label="Interface">
                <SelectValue placeholder="No interface" />
              </SelectTrigger>
              <SelectContent>
                {interfaces.map((wifiInterface) => (
                  <SelectItem key={wifiInterface.name} value={wifiInterface.name}>
                    {wifiInterface.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <span className="text-sm text-neutral-600">{selectedInterface ? selectedInterface.mode : '–'}</span>
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-neutral-500">Mode</span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              disabled={requestInProgress || captureRunning || !selectedInterface || selectedInterface.mode === 'MONITOR'}
              onClick={() => switchMode('MONITOR')}
            >
              Monitor mode
            </Button>
            <Button
              variant="outline"
              disabled={requestInProgress || captureRunning || !selectedInterface || selectedInterface.mode === 'MANAGED'}
              onClick={() => switchMode('MANAGED')}
            >
              Managed mode
            </Button>
          </div>
        </div>

        {/* capture: live or from a file */}
        <div className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-neutral-500">Capture</span>
          <div className="flex gap-2">
            <Button
              disabled={requestInProgress || captureRunning || !selectedInterface}
              onClick={() => runRequest(() => api.startLiveCapture(selectedInterfaceName))}
            >
              Start live
            </Button>
            <Button
              variant="destructive"
              disabled={requestInProgress || !captureRunning}
              onClick={() => runRequest(() => api.stopCapture())}
            >
              Stop
            </Button>
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-neutral-500">Replay a file</span>
          <div className="flex gap-2">
            <Select value={selectedReplayFile} onValueChange={setSelectedReplayFile}>
              <SelectTrigger className="w-48" aria-label="Replay file">
                <SelectValue placeholder="No files" />
              </SelectTrigger>
              <SelectContent>
                {replayFiles.map((fileName) => (
                  <SelectItem key={fileName} value={fileName}>
                    {fileName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button
              variant="secondary"
              disabled={requestInProgress || captureRunning || selectedReplayFile === ''}
              onClick={() => runRequest(() => api.startFileReplay(selectedReplayFile))}
            >
              Replay
            </Button>
          </div>
        </div>

        {/* channel: hop through 1-13 or stay on one */}
        <div className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-neutral-500">Channel</span>
          <Select value={pinnedChannelOption} onValueChange={changePinnedChannel} disabled={requestInProgress}>
            <SelectTrigger className="w-36" aria-label="Channel">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={HOPPING_OPTION}>Hop 1–13</SelectItem>
              {PINNABLE_CHANNELS.map((channel) => (
                <SelectItem key={channel} value={String(channel)}>
                  Channel {channel}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {errorMessage !== null && (
        <p role="alert" className="text-sm text-red-600">
          {errorMessage}
        </p>
      )}
    </div>
  )
}

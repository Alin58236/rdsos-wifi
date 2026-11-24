

import type { InterfaceMode, WifiInterface } from './types'

async function sendRequest(method: string, url: string, body?: unknown): Promise<Response> {
  const response = await fetch(url, {
    method: method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (!response.ok) {

    const backendNotRunning = response.status >= 502 && response.status <= 504

    let errorMessage = backendNotRunning
      ? 'The backend is not running (start it on 127.0.0.1:8080).'
      : `${method} ${url} failed with status ${response.status}`

    try {
    
      const errorBody = await response.json()
    
      if (typeof errorBody.error === 'string') {
        errorMessage = errorBody.error
    
      }
    
    } catch {
    
      // the body was not JSON; keep the generic message
    
    }
    throw new Error(errorMessage)
  }

  return response
}

export async function getInterfaces(): Promise<WifiInterface[]> {
  const response = await sendRequest('GET', '/api/interfaces')
  return response.json()
}

export async function switchInterfaceMode(interfaceName: string, mode: InterfaceMode): Promise<void> {
  await sendRequest('POST', `/api/interfaces/${encodeURIComponent(interfaceName)}/mode`, { mode: mode })
}

export async function getReplayFiles(): Promise<string[]> {
  const response = await sendRequest('GET', '/api/replay-files')
  return response.json()
}

export async function startLiveCapture(interfaceName: string): Promise<void> {
  await sendRequest('POST', '/api/capture/start', { source: 'LIVE', interface: interfaceName })
}

export async function startFileReplay(fileName: string): Promise<void> {
  await sendRequest('POST', '/api/capture/start', { source: 'FILE', file: fileName })
}

export async function stopCapture(): Promise<void> {
  await sendRequest('POST', '/api/capture/stop')
}

export async function pinChannel(channel: number): Promise<void> {
  await sendRequest('PUT', '/api/channel', { channel: channel })
}

export async function unpinChannel(): Promise<void> {
  await sendRequest('DELETE', '/api/channel')
}

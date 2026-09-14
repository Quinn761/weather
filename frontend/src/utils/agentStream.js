// The server may emit `end` before `done`; only `done` carries persisted session metadata.
export async function readAgentStream(body, onDelta) {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  const parse = async (frame) => {
    const data = frame.split(/\r?\n/).filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trimStart()).join('\n')
    if (!data) return null
    let event
    try { event = JSON.parse(data) } catch { throw new Error('回答数据格式异常，请重试') }
    if (event.type === 'error') throw new Error(event.message || 'Agent 执行失败，请重试')
    if (event.type === 'delta' && event.text) await onDelta?.(event.text)
    return event.type === 'done' && event.run ? event.run : null
  }
  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer += done ? decoder.decode() : decoder.decode(value, { stream: true })
      const frames = buffer.split(/\r?\n\r?\n/)
      buffer = frames.pop() || ''
      for (const frame of frames) {
        const result = await parse(frame)
        if (result) return result
      }
      if (done) {
        if (buffer.trim()) {
          const result = await parse(buffer)
          if (result) return result
        }
        throw new Error('连接已中断，回答可能不完整。请重试或重新打开会话查看已保存的结果。')
      }
    }
  } finally {
    try { await reader.cancel() } catch { /* Connection may already be closed. */ }
    reader.releaseLock()
  }
}

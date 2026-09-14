import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readAgentStream } from './agentStream.js'

function stream(text, split = 7) {
  const bytes = new TextEncoder().encode(text)
  return new ReadableStream({ start(controller) {
    for (let i = 0; i < bytes.length; i += split) controller.enqueue(bytes.slice(i, i + split))
    controller.close()
  } })
}
test('keeps session metadata after end, with split UTF-8 and CRLF frames', async () => {
  let text = ''
  const result = await readAgentStream(stream(
    ': heartbeat\r\n\r\ndata: {"type":"delta","text":"天气晴朗"}\r\n\r\n' +
    'data: {"type":"end"}\r\n\r\ndata: {"type":"done","run":{"sessionId":42,"reply":"天气晴朗"}}\r\n\r\n', 1),
    (chunk) => { text += chunk })
  assert.equal(text, '天气晴朗')
  assert.equal(result.sessionId, 42)
})
test('accepts a final done event without a trailing blank line', async () => {
  assert.equal((await readAgentStream(stream('data: {"type":"done","run":{"sessionId":9}}'))).sessionId, 9)
})
test('does not silently accept a truncated answer', async () => {
  await assert.rejects(readAgentStream(stream('data: {"type":"delta","text":"partial"}\n\n')), /连接已中断/)
})
test('surfaces service errors and rejects malformed JSON', async () => {
  await assert.rejects(readAgentStream(stream('data: {"type":"error","message":"工具不可用"}\n\n')), /工具不可用/)
  await assert.rejects(readAgentStream(stream('data: {bad}\n\n')), /格式异常/)
})

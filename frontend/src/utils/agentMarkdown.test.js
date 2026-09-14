import { test } from 'node:test'
import assert from 'node:assert/strict'
import { renderAgentMarkdown } from './agentMarkdown.js'
test('renders Markdown and escapes HTML and unsafe links', () => {
  const result = renderAgentMarkdown('## 天气\n\n**晴天**\n\n<script>alert(1)</script>\n\n[x](javascript:alert(1))')
  assert.match(result, /<h2>天气<\/h2>/)
  assert.match(result, /<strong>晴天<\/strong>/)
  assert.doesNotMatch(result, /<script|href="javascript:/)
})
test('external links are isolated and images are not fetched', () => {
  const result = renderAgentMarkdown('[资料](https://example.com)\n\n![卫星图](https://example.com/tracker)')
  assert.match(result, /noopener noreferrer/)
  assert.doesNotMatch(result, /<img/)
})

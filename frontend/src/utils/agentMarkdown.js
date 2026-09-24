import MarkdownIt from 'markdown-it'
const markdown = new MarkdownIt({ html: false, breaks: true, linkify: false })
markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
  tokens[index].attrSet('target', '_blank')
  tokens[index].attrSet('rel', 'noopener noreferrer')
  return self.renderToken(tokens, index, options)
}
markdown.renderer.rules.image = (tokens, index) => markdown.utils.escapeHtml(tokens[index].content || '[图片]')

// A streamed model response can omit the newline between a heading and a table
// header (for example, "##未来7天预报|日期|..."). Markdown would otherwise render
// the whole line as text instead of a heading followed by a table.
function normalizeAgentMarkdown(text) {
  return String(text || '')
    .replace(/\r\n?/g, '\n')
    .replace(/^(#{1,6})\s*([^\n|]*?)\s*(\|(?=[^\n]*\|))/gm, (_, hashes, title, table) => `${hashes} ${title.trim()}\n\n${table}`)
}

export function renderAgentMarkdown(text) { return markdown.render(normalizeAgentMarkdown(text)) }

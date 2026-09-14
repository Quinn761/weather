import MarkdownIt from 'markdown-it'
const markdown = new MarkdownIt({ html: false, breaks: true, linkify: false })
markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
  tokens[index].attrSet('target', '_blank')
  tokens[index].attrSet('rel', 'noopener noreferrer')
  return self.renderToken(tokens, index, options)
}
markdown.renderer.rules.image = (tokens, index) => markdown.utils.escapeHtml(tokens[index].content || '[图片]')
export function renderAgentMarkdown(text) { return markdown.render(text || '') }

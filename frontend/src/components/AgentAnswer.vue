<script setup>
import { computed } from 'vue'
import { renderAgentMarkdown } from '@/utils/agentMarkdown'
const props = defineProps({ content: { type: String, default: '' } })
const rendered = computed(() => renderAgentMarkdown(props.content))
</script>
<template>
  <!-- Only the HTML-disabled Markdown renderer generates this HTML. -->
  <div class="agent-answer" v-html="rendered" />
</template>
<style scoped>
.agent-answer { font-size: 14px; line-height: 1.85; overflow-wrap: anywhere; }
.agent-answer :deep(> :first-child) { margin-top: 0; }
.agent-answer :deep(> :last-child) { margin-bottom: 0; }
.agent-answer :deep(p) { margin: 0 0 12px; }
.agent-answer :deep(h1), .agent-answer :deep(h2), .agent-answer :deep(h3) { font-size: 17px; line-height: 1.5; margin: 20px 0 8px; }
.agent-answer :deep(h4), .agent-answer :deep(h5), .agent-answer :deep(h6) { font-size: 15px; margin: 16px 0 8px; }
.agent-answer :deep(ul), .agent-answer :deep(ol) { padding-left: 24px; margin: 8px 0 16px; }
.agent-answer :deep(li + li) { margin-top: 6px; }
.agent-answer :deep(a) { color: #087e8b; text-decoration: underline; text-underline-offset: 3px; }
.agent-answer :deep(pre) { white-space: pre; overflow-x: auto; max-width: 100%; padding: 16px; border-radius: 10px; background: #132b3b; color: #dcecf5; font-size: 12px; line-height: 1.7; }
.agent-answer :deep(code) { font-family: Consolas, monospace; background: #edf3f6; border-radius: 4px; padding: 2px 5px; font-size: .9em; }
.agent-answer :deep(pre code) { background: none; padding: 0; color: inherit; }
.agent-answer :deep(blockquote) { margin: 14px 0; border-left: 3px solid #79b7bd; padding: 6px 14px; color: #64748b; background: #f4f9fa; }
.agent-answer :deep(table) { display: block; max-width: 100%; overflow: auto; border-collapse: collapse; margin: 14px 0; font-size: 13px; }
.agent-answer :deep(th), .agent-answer :deep(td) { border: 1px solid #dce6ec; padding: 8px 12px; min-width: 85px; text-align: left; }
.agent-answer :deep(th) { background: #f0f6f8; }
.agent-answer :deep(hr) { border: 0; border-top: 1px solid #e2e8f0; margin: 18px 0; }
</style>

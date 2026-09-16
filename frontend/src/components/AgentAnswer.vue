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
.agent-answer {
  color: #d7e7f8;
  font-size: 14px;
  line-height: 1.85;
  overflow-wrap: anywhere;
}
.agent-answer :deep(> :first-child) { margin-top: 0; }
.agent-answer :deep(> :last-child) { margin-bottom: 0; }
.agent-answer :deep(p) { margin: 0 0 12px; }
.agent-answer :deep(h1),
.agent-answer :deep(h2),
.agent-answer :deep(h3) {
  color: #eef6ff;
  font-size: 17px;
  line-height: 1.5;
  margin: 20px 0 8px;
}
.agent-answer :deep(h4),
.agent-answer :deep(h5),
.agent-answer :deep(h6) {
  color: #e2effc;
  font-size: 15px;
  margin: 16px 0 8px;
}
.agent-answer :deep(ul),
.agent-answer :deep(ol) {
  padding-left: 24px;
  margin: 8px 0 16px;
}
.agent-answer :deep(li + li) { margin-top: 6px; }
.agent-answer :deep(a) {
  color: #6ee7ff;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.agent-answer :deep(pre) {
  white-space: pre;
  overflow-x: auto;
  max-width: 100%;
  padding: 16px;
  border-radius: 10px;
  border: 1px solid #2f4a66;
  background: #0a1624;
  color: #dcecf5;
  font-size: 12px;
  line-height: 1.7;
}
.agent-answer :deep(code) {
  font-family: Consolas, 'SF Mono', monospace;
  color: #b8f3ff;
  background: #173247;
  border: 1px solid #2f5870;
  border-radius: 5px;
  padding: 1px 6px;
  font-size: 0.9em;
}
.agent-answer :deep(pre code) {
  background: none;
  border: 0;
  padding: 0;
  color: inherit;
}
.agent-answer :deep(blockquote) {
  margin: 14px 0;
  border-left: 3px solid #3db8d4;
  padding: 6px 14px;
  color: #a8bfd4;
  background: #122033;
}
.agent-answer :deep(table) {
  display: block;
  max-width: 100%;
  overflow: auto;
  border-collapse: collapse;
  margin: 14px 0;
  font-size: 13px;
  color: #d5e6f7;
}
.agent-answer :deep(th),
.agent-answer :deep(td) {
  border: 1px solid #2d455f;
  padding: 8px 12px;
  min-width: 85px;
  text-align: left;
  vertical-align: top;
}
.agent-answer :deep(th) {
  color: #e8f4ff;
  background: #15263b;
  font-weight: 600;
}
.agent-answer :deep(td) {
  background: #101d2e;
}
.agent-answer :deep(td code) {
  color: #b8f3ff;
  background: #1a3348;
  border-color: #35637a;
}
.agent-answer :deep(tr:nth-child(even) td) {
  background: #122235;
}
.agent-answer :deep(hr) {
  border: 0;
  border-top: 1px solid #2c425a;
  margin: 18px 0;
}
.agent-answer :deep(strong) {
  color: #eef6ff;
}
</style>

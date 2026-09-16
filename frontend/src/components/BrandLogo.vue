<script setup>
defineProps({
  size: { type: Number, default: 40 },
  withWordmark: { type: Boolean, default: false },
  tone: { type: String, default: 'light' },
})
</script>

<template>
  <span class="brand-logo" :class="[`tone-${tone}`, { 'has-word': withWordmark }]">
    <span class="mark" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
      <svg viewBox="0 0 64 64" fill="none">
        <rect width="64" height="64" rx="14" fill="#0c2735" />
        <path d="M14 38.5 32 28.5 50 38.5 32 48.5Z" fill="#1d4d57" />
        <path d="M14 34.5 32 24.5 50 34.5 32 44.5Z" fill="#2a6570" />
        <path d="M18 33.2 32 25.4 46 33.2 32 41Z" fill="#356f78" />
        <path d="M22 31.8 32 26.2 42 31.8 32 37.4Z" fill="#3f7f88" />
        <path d="M10 40C18 28 28 22 32 22c6 0 14 8 22 18" stroke="#7fe8cb" stroke-width="2.2" stroke-linecap="round" opacity=".9" />
        <circle cx="32" cy="30.5" r="4.2" fill="#a8ffe0" class="core" />
        <circle cx="32" cy="30.5" r="8.5" stroke="#7fe8cb" stroke-opacity=".55" class="ring" />
        <circle cx="32" cy="30.5" r="13" stroke="#7fe8cb" stroke-opacity=".28" class="ring delay" />
        <circle cx="32" cy="30.5" r="17.5" stroke="#7fe8cb" stroke-opacity=".14" class="ring delay2" />
      </svg>
    </span>
    <span v-if="withWordmark" class="wordmark">
      <strong>Weather Data Hub</strong>
      <small>空间 · 智能 · 连接</small>
    </span>
  </span>
</template>

<style scoped>
.brand-logo {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.mark {
  flex-shrink: 0;
  display: block;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 10px 24px rgba(4, 24, 34, 0.28);
}

.mark svg {
  display: block;
  width: 100%;
  height: 100%;
}

.core {
  transform-origin: 32px 30.5px;
  animation: core-glow 3.2s ease-in-out infinite;
}

.ring {
  transform-origin: 32px 30.5px;
  animation: ring-pulse 3.2s ease-out infinite;
}

.ring.delay {
  animation-delay: 0.45s;
}

.ring.delay2 {
  animation-delay: 0.9s;
}

@keyframes core-glow {
  0%,
  100% {
    opacity: 0.85;
  }
  50% {
    opacity: 1;
  }
}

@keyframes ring-pulse {
  0% {
    opacity: 0.7;
    transform: scale(0.92);
  }
  70%,
  100% {
    opacity: 0;
    transform: scale(1.08);
  }
}

@media (prefers-reduced-motion: reduce) {
  .core,
  .ring {
    animation: none;
  }
}

.wordmark {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.wordmark strong {
  font-family: Orbitron, Oxanium, sans-serif;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  line-height: 1.1;
}

.wordmark small {
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 10px;
  letter-spacing: 0.32em;
  font-weight: 300;
}

.tone-light .wordmark strong {
  color: #edf6f7;
}

.tone-light .wordmark small {
  color: #8aa8b4;
}

.tone-dark .wordmark strong {
  color: #16352f;
}

.tone-dark .wordmark small {
  color: #6f857c;
}
</style>

<script setup>
import backdrop from '@/assets/workspace/storm-gis-backdrop.png'
</script>

<template>
  <div
    class="workspace-backdrop"
    :style="{ backgroundImage: `linear-gradient(110deg, rgb(4 12 23 / 46%), rgb(4 12 23 / 18%) 58%, rgb(4 12 23 / 30%)), url(${backdrop})` }"
    aria-hidden="true"
  />
</template>

<style scoped>
.workspace-backdrop {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background-color: #081321;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}
</style>

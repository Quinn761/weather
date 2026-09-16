<script setup>
import heroImg from '@/assets/login/hero-3840.png'
</script>

<template>
  <div
    class="map-scene"
    :style="{ backgroundImage: `url(${heroImg})` }"
    aria-hidden="true"
  />
</template>

<style scoped>
.map-scene {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  background-color: #04101c;
  background-size: cover;
  background-position: center center;
  background-repeat: no-repeat;
  z-index: 0;
}
</style>

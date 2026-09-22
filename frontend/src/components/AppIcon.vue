<script setup lang="ts">
import { computed } from 'vue'

/**
 * 全站统一线性图标（24 网格 / 1.8 描边 / currentColor）。
 * 一套图标语言贯穿导航、按钮与空态，避免 emoji 与混搭风格。
 */
const ICONS: Record<string, string[]> = {
  house: ['m3 9.5 9-7 9 7V20a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z', 'M9 22V12h6v10'],
  plan: ['M4 4h7v7H4z', 'M13 4h7v9h-7z', 'M4 13h7v7H4z', 'M13 15h7v5h-7z'],
  gauge: ['M3.5 19a10 10 0 1 1 17 0', 'm12 13 3.5-3.5'],
  compare: ['M3 3v18h18', 'M18 17V9', 'M13 17V5', 'M8 17v-3'],
  ai: [
    'm12 3-1.9 5.8a2 2 0 0 1-1.3 1.3L3 12l5.8 1.9a2 2 0 0 1 1.3 1.3L12 21l1.9-5.8a2 2 0 0 1 1.3-1.3L21 12l-5.8-1.9a2 2 0 0 1-1.3-1.3z',
    'M5 3v3.5', 'M3.5 4.8h3', 'M19 17.5v3.5', 'M17.5 19.3h3',
  ],
  calendar: ['M8 2v4', 'M16 2v4', 'M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z', 'M3 10h18'],
  shield: ['M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z', 'm9 12 2 2 4-4'],
  check: ['M20 6 9 17l-5-5'],
  'check-circle': ['M22 11.08V12a10 10 0 1 1-5.93-9.14', 'm9 11 3 3L22 4'],
  'arrow-right': ['M5 12h14', 'm12 5 7 7-7 7'],
  search: ['M10.5 3a7.5 7.5 0 1 0 0 15 7.5 7.5 0 0 0 0-15z', 'm20.5 20.5-4.9-4.9'],
  clock: ['M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17z', 'M12 7v5l3.5 2'],
  user: ['M12 3.5a4 4 0 1 0 0 8 4 4 0 0 0 0-8z', 'M4.5 21v-1.5a5.5 5.5 0 0 1 5.5-5.5h4a5.5 5.5 0 0 1 5.5 5.5V21'],
  message: ['M7.9 20A9 9 0 1 0 4 16.1L2 22z'],
  lock: ['M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z', 'M7.5 11V7a4.5 4.5 0 0 1 9 0v4'],
  'trending-up': ['M22 7l-8.5 8.5-5-5L2 17', 'M16 7h6v6'],
  sun: ['M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0z', 'M12 2v2', 'M12 20v2', 'm4.9 4.9 1.4 1.4', 'm17.7 17.7 1.4 1.4', 'M2 12h2', 'M20 12h2', 'm6.3 17.7-1.4 1.4', 'm19.1 4.9-1.4 1.4'],
  wind: ['M17.7 7.7a2.5 2.5 0 1 1 1.8 4.3H2', 'M9.6 4.6A2 2 0 1 1 11 8H2', 'M12.6 19.4A2 2 0 1 0 14 16H2'],
  'map-pin': ['M20 10.5c0 6.5-8 11.5-8 11.5s-8-5-8-11.5a8 8 0 0 1 16 0z', 'M12 12.5a3 3 0 1 0 0-6 3 3 0 0 0 0 6z'],
  refresh: ['M3 12a9 9 0 0 1 15.3-6.4L21 8', 'M21 3v5h-5', 'M21 12a9 9 0 0 1-15.3 6.4L3 16', 'M3 21v-5h5'],
  info: ['M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17z', 'M12 8h.01', 'M12 12v4.5'],
  alert: ['m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3z', 'M12 9v4', 'M12 17h.01'],
  link: ['M15 3h6v6', 'M10 14 21 3', 'M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6'],
  copy: ['M10 8h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z', 'M4 16a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2'],
  plus: ['M12 5v14', 'M5 12h14'],
}

const props = withDefaults(defineProps<{ name: string; size?: number | string }>(), { size: 18 })

const paths = computed(() => ICONS[props.name] ?? ICONS.info)
const px = computed(() => (typeof props.size === 'number' ? `${props.size}px` : props.size))
</script>

<template>
  <svg
    class="app-icon"
    :width="px"
    :height="px"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
</template>

<style scoped>
.app-icon {
  display: inline-block;
  vertical-align: -0.25em;
  flex: none;
}
</style>

import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getOverview } from '@/api'

export const useDashboardStore = defineStore('dashboard', () => {
  const overview = ref({
    userCount: 0,
    roleCount: 0,
    menuCount: 0,
  })
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      overview.value = (await getOverview()) || overview.value
    } finally {
      loading.value = false
    }
  }

  return { overview, loading, load }
})

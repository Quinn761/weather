<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Avatar, Menu, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useDashboardStore } from '@/stores/dashboard'

const router = useRouter()
const authStore = useAuthStore()
const dashboard = useDashboardStore()

const cards = [
  { key: 'userCount', label: '系统用户', icon: User, tone: 'cyan', path: '/users', permission: 'user:read' },
  { key: 'roleCount', label: '角色', icon: Avatar, tone: 'blue', path: '/roles', permission: 'role:read' },
  { key: 'menuCount', label: '菜单', icon: Menu, tone: 'green', path: '/menus', permission: 'menu:read' },
]

onMounted(() => {
  dashboard.load()
})

function go(card) {
  if (authStore.hasPermission(card.permission)) {
    router.push(card.path)
  }
}
</script>

<template>
  <div class="page" v-loading="dashboard.loading">
    <section class="hero-panel">
      <div>
        <p class="eyebrow">Weather Data Hub</p>
        <h2>用户、角色与菜单权限中心</h2>
        <p class="hero-desc">
          给用户分配角色，给角色勾选菜单。侧栏和接口权限都来自角色绑定的菜单。
        </p>
      </div>
    </section>

    <section class="stat-grid">
      <article
        v-for="card in cards"
        :key="card.key"
        class="stat-card"
        :class="card.tone"
        @click="go(card)"
      >
        <el-icon class="stat-icon"><component :is="card.icon" /></el-icon>
        <div>
          <p>{{ card.label }}</p>
          <strong>{{ dashboard.overview[card.key] ?? 0 }}</strong>
        </div>
      </article>
    </section>
  </div>
</template>

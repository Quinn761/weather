import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      meta: { title: '登录', public: true },
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/',
      component: AppLayout,
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          meta: { title: '工作台', permission: 'dashboard:view' },
          component: () => import('@/views/DashboardView.vue'),
        },
        {
          path: 'users',
          name: 'users',
          meta: { title: '用户管理', permission: 'user:read' },
          component: () => import('@/views/UserView.vue'),
        },
        {
          path: 'roles',
          name: 'roles',
          meta: { title: '角色管理', permission: 'role:read' },
          component: () => import('@/views/RoleView.vue'),
        },
        {
          path: 'menus',
          name: 'menus',
          meta: { title: '菜单管理', permission: 'menu:read' },
          component: () => import('@/views/MenuView.vue'),
        },
        {
          path: 'gis',
          name: 'gis',
          meta: {
            title: 'GIS 标注',
            subtitle: '用地理视角，连接数据与现实世界',
            permission: 'gis:read',
          },
          component: () => import('@/views/GisView.vue'),
        },
        {
          path: 'cameras',
          name: 'cameras',
          meta: {
            title: '摄像头列表',
            subtitle: '配置 GIS 工作台使用的实时视频设备',
            permission: 'camera:read',
          },
          component: () => import('@/views/CameraListView.vue'),
        },
        {
          path: 'kb',
          name: 'kb',
          meta: { title: '知识库', permission: 'kb:read' },
          component: () => import('@/views/KnowledgeView.vue'),
        },
        {
          path: 'ai',
          name: 'ai',
          meta: { title: 'Agent 工作台', permission: 'ai:chat' },
          component: () => import('@/views/AiView.vue'),
        },
      ],
    },
  ],
})

function firstAllowedPath(authStore) {
  const walk = (nodes) => {
    for (const node of nodes || []) {
      if (node.type === 'MENU' && node.path) {
        return node.path
      }
      const child = walk(node.children)
      if (child) {
        return child
      }
    }
    return '/dashboard'
  }
  return walk(authStore.user?.menus)
}

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (to.meta.public) {
    if (authStore.token && to.path === '/login') {
      return firstAllowedPath(authStore)
    }
    return true
  }
  if (!authStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  try {
    await authStore.ensureFreshUser()
  } catch {
    await authStore.logout()
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const permission = to.meta.permission
  if (permission && !authStore.hasPermission(permission)) {
    const fallback = firstAllowedPath(authStore)
    if (to.path !== fallback) {
      return fallback
    }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title ? `${to.meta.title} · Weather Data Hub` : 'Weather Data Hub'
  document.title = title
})

export default router

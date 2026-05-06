import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      name: 'home',
      component: () => import('../views/HomeView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/home',
      redirect: '/',
    },
    {
      path: '/problems',
      name: 'problems',
      component: () => import('../views/ProblemListView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/problems/:id',
      name: 'problem-detail',
      component: () => import('../views/ProblemDetailView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/algorithm',
      name: 'algorithm',
      component: () => import('../views/ProblemDetailView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/algorithm/conversation/:conversationId',
      name: 'conversation-detail',
      component: () => import('../views/ProblemDetailView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/spans',
      name: 'spans',
      component: () => import('../views/SpanListView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/trace/:traceId',
      name: 'trace-detail',
      component: () => import('../views/TraceDetailView.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

// 路由守卫负责保护需登录页面并处理登录页跳转。
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  const requiresAuth = to.meta.requiresAuth !== false

  if (requiresAuth && !authStore.isAuthenticated) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }

  if (to.name === 'login' && authStore.isAuthenticated) {
    next({ name: 'problems' })
    return
  }

  next()
})

export default router

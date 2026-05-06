import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const DEFAULT_AUTH_REDIRECT = '/questions'

// 统一过滤 redirect 参数，避免外链跳转与登录页循环跳转
const resolveSafeRedirect = (redirect: unknown) => {
  if (typeof redirect !== 'string') {
    return DEFAULT_AUTH_REDIRECT
  }
  const trimmedRedirect = redirect.trim()
  if (!trimmedRedirect.startsWith('/') || trimmedRedirect.startsWith('//') || trimmedRedirect.startsWith('/\\')) {
    return DEFAULT_AUTH_REDIRECT
  }
  if (trimmedRedirect === '/login' || trimmedRedirect.startsWith('/login?')) {
    return DEFAULT_AUTH_REDIRECT
  }
  return trimmedRedirect
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'admin-login',
      component: () => import('@/views/LoginView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      component: () => import('@/views/AdminLayoutView.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/questions',
        },
        {
          path: 'questions',
          name: 'admin-questions',
          component: () => import('@/views/QuestionManagementView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'questions/:questionId/documents/:docId/segments',
          name: 'admin-question-document-segments',
          component: () => import('@/views/QuestionDocumentSegmentsView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'knowledge-segments',
          name: 'admin-knowledge-segments',
          component: () => import('@/views/KnowledgeSegmentHitkTaskView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'users',
          name: 'admin-user-logs',
          component: () => import('@/views/UserManagementView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'user-management',
          name: 'admin-users',
          component: () => import('@/views/UserAccountManagementView.vue'),
          meta: { requiresAuth: true },
        },
      ],
    },
    {
      path: '/dashboard',
      redirect: '/questions',
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  const requiresAuth = to.meta.requiresAuth !== false

  if (requiresAuth && !authStore.isAuthenticated) {
    return { name: 'admin-login', query: { redirect: resolveSafeRedirect(to.fullPath) } }
  }

  if (to.name === 'admin-login' && authStore.isAuthenticated) {
    return resolveSafeRedirect(to.query.redirect)
  }

  return true
})

export default router

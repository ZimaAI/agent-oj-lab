import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const storage = new Map<string, string>()

Object.defineProperty(globalThis, 'localStorage', {
  value: {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => {
      storage.set(key, value)
    },
    removeItem: (key: string) => {
      storage.delete(key)
    },
    clear: () => {
      storage.clear()
    },
  },
  writable: true,
  configurable: true,
})

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')

  return {
    ...actual,
    createWebHistory: () => actual.createMemoryHistory(),
  }
})

import router from '../index'
import { useAuthStore } from '@/stores/auth'

describe('router guard', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    localStorage.clear()
    await router.push('/problems')
    await router.isReady()
  })

  afterEach(async () => {
    const authStore = useAuthStore()
    authStore.accessToken = null
    authStore.refreshToken = null
    authStore.userInfo = null
    authStore.currentUser = null
    storage.clear()
    await router.push('/problems')
  })

  it('redirects unauthenticated users from /spans to /login with redirect query', async () => {
    const authStore = useAuthStore()
    authStore.accessToken = null

    await router.push('/spans')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.fullPath).toBe('/login?redirect=/spans')
    expect(router.currentRoute.value.query.redirect).toBe('/spans')
  })

  it('allows unauthenticated users to access public routes', async () => {
    await router.push('/problems')

    expect(router.currentRoute.value.name).toBe('problems')
  })

  it('routes root path to home view', async () => {
    await router.push('/')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('redirects authenticated users away from login to problems', async () => {
    const authStore = useAuthStore()
    authStore.accessToken = 'access-token'

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('problems')
  })
})

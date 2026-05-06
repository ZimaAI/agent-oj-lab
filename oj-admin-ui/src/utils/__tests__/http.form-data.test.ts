import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

interface MockStorage extends Storage {
  _store: Map<string, string>
}

// Build a controllable localStorage mock without browser runtime dependency.
const createStorageMock = (initialValues: Record<string, string> = {}): MockStorage => {
  const store = new Map<string, string>(Object.entries(initialValues))
  return {
    _store: store,
    get length() {
      return store.size
    },
    clear() {
      store.clear()
    },
    getItem(key: string) {
      return store.has(key) ? store.get(key)! : null
    },
    key(index: number) {
      return Array.from(store.keys())[index] ?? null
    },
    removeItem(key: string) {
      store.delete(key)
    },
    setItem(key: string, value: string) {
      store.set(key, String(value))
    },
  }
}

describe('http client form-data behavior', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('does not force application/json when posting FormData payload', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        code: '0',
        message: 'ok',
        data: {},
      }),
    })
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('window', {
      location: {
        origin: 'http://localhost:3002',
        pathname: '/questions',
        search: '',
      },
      dispatchEvent: vi.fn(),
    })
    vi.stubGlobal('localStorage', createStorageMock({ admin_access_token: 'token-123' }))

    const { http } = await import('../http')
    const formData = new FormData()
    formData.append('file', new Blob(['# demo-md'], { type: 'text/markdown' }), 'demo.md')

    await http.post('/api/admin/questions/1001/documents/upload', formData)

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = (init.headers ?? {}) as Record<string, string>

    expect(init.body).toBe(formData)
    expect(headers.Authorization).toBe('Bearer token-123')
    expect(headers['Content-Type']).toBeUndefined()
  })
})

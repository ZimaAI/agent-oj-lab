export interface ApiResult<T> {
  code: string
  message: string
  data: T
}

export interface RequestOptions extends RequestInit {
  params?: Record<string, string | number | boolean | null | undefined>
}

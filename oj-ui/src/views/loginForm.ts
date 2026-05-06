import type { LoginRequest } from '../types/auth'

export function getExperienceAccountError(username: string) {
  return username.trim() ? '' : '请输入体验账号'
}

export function buildExperienceLoginRequest(username: string): LoginRequest {
  return {
    username: username.trim(),
  }
}

function sanitizeRedirect(target: unknown) {
  // 仅允许站内绝对路径，避免跳转到外部地址或协议相对地址。
  if (typeof target !== 'string') {
    return null
  }

  const normalizedTarget = target.trim()
  if (!normalizedTarget || !normalizedTarget.startsWith('/') || normalizedTarget.startsWith('//')) {
    return null
  }

  return normalizedTarget
}

export function resolvePostLoginRedirect(routeRedirect: unknown, returnUrl: string | null) {
  // 优先使用路由守卫写入的重定向目标。
  const routeRedirectTarget = sanitizeRedirect(routeRedirect)
  if (routeRedirectTarget) {
    return routeRedirectTarget
  }

  // 其次回退到本地缓存的返回地址。
  const storedReturnUrl = sanitizeRedirect(returnUrl)
  if (storedReturnUrl) {
    return storedReturnUrl
  }

  // 最后统一回到题目列表页。
  return '/problems'
}

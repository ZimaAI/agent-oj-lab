import { marked } from 'marked'

const LINK_REL = 'noopener noreferrer nofollow'
const HREF_ATTR_PATTERN = /\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/i
const TAG_PATTERN = /<\/?([a-z0-9-]+)\b[^>]*>/gi
const BLOCKED_ELEMENT_PATTERN =
  /<(script|style|iframe|object|embed|svg|math|video|audio|picture|source|canvas|noscript|template)\b[^>]*>[\s\S]*?<\/\1>/gi
const BLOCKED_SINGLE_TAG_PATTERN =
  /<(img|script|style|iframe|object|embed|svg|math|video|audio|picture|source|canvas|track|meta|link|base)\b[^>]*\/?\s*>/gi

const ALLOWED_TAGS = new Set([
  'p',
  'strong',
  'em',
  'code',
  'pre',
  'ul',
  'ol',
  'li',
  'blockquote',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'a',
  'br',
  'hr',
])

function escapeHtmlAttribute(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function sanitizeAnchorTag(tagHtml: string, isClosingTag: boolean): string {
  if (isClosingTag) {
    return '</a>'
  }

  const hrefMatch = tagHtml.match(HREF_ATTR_PATTERN)
  const href = hrefMatch?.[1] ?? hrefMatch?.[2] ?? hrefMatch?.[3] ?? ''
  const safeHref = isSafeLinkHref(href) ? href.trim() : '#'

  return `<a href="${escapeHtmlAttribute(safeHref)}" target="_blank" rel="${LINK_REL}">`
}

export function normalizeMarkdownInput(value: string): string {
  // 通过转义尖括号保留原文，避免误删泛型或比较表达式。
  return value.replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

export function isSafeLinkHref(href: string): boolean {
  const trimmed = href.trim()
  if (!trimmed) {
    return false
  }

  const lowerHref = trimmed.toLowerCase()

  // 仅允许 http/https/mailto 和站内相对路径。
  if (lowerHref.startsWith('http://') || lowerHref.startsWith('https://') || lowerHref.startsWith('mailto:')) {
    return true
  }

  return lowerHref.startsWith('/') && !lowerHref.startsWith('//')
}

export function sanitizeRenderedLinks(html: string): string {
  // 先移除媒体与嵌入类危险标签，避免其内部内容继续参与渲染。
  const strippedBlockedElements = html
    .replace(BLOCKED_ELEMENT_PATTERN, '')
    .replace(BLOCKED_SINGLE_TAG_PATTERN, '')

  // 再按白名单重建标签，阻断事件属性和多余属性注入。
  return strippedBlockedElements.replace(TAG_PATTERN, (tagHtml: string, tagNameRaw: string) => {
    const tagName = tagNameRaw.toLowerCase()
    const isClosingTag = tagHtml.startsWith('</')

    if (tagName === 'a') {
      return sanitizeAnchorTag(tagHtml, isClosingTag)
    }

    if (!ALLOWED_TAGS.has(tagName)) {
      return ''
    }

    if (isClosingTag) {
      return tagName === 'br' || tagName === 'hr' ? '' : `</${tagName}>`
    }

    return `<${tagName}>`
  })
}

export function renderSafeMarkdown(value: string): string {
  const normalizedInput = normalizeMarkdownInput(value)
  const renderedHtml = marked.parse(normalizedInput, { breaks: true }) as string

  return sanitizeRenderedLinks(renderedHtml)
}

export function buildMarkdownCacheKey(path: string, content: string): string {
  return JSON.stringify([path, content])
}

export function renderSafeMarkdownWithCache(
  cache: Map<string, string>,
  path: string,
  content: string,
): string {
  const cacheKey = buildMarkdownCacheKey(path, content)
  const cachedHtml = cache.get(cacheKey)
  if (cachedHtml !== undefined) {
    return cachedHtml
  }

  const html = renderSafeMarkdown(content)
  cache.set(cacheKey, html)
  return html
}

export type JsonLikeParseResult =
  | { kind: 'empty'; value: null }
  | { kind: 'json'; value: unknown }
  | { kind: 'text'; value: string }

const NESTED_JSON_MAX_DEPTH = 8

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Object.prototype.toString.call(value) === '[object Object]'
}

// 仅对明显为 JSON 容器的字符串执行递归解析，避免误解析普通文本。
function looksLikeJsonContainer(value: string): boolean {
  const trimmed = value.trim()
  if (trimmed.length < 2) {
    return false
  }

  const startsWith = trimmed[0]
  const endsWith = trimmed[trimmed.length - 1]
  return (startsWith === '{' && endsWith === '}') || (startsWith === '[' && endsWith === ']')
}

// 递归解析对象/数组中的内层 JSON 字符串，确保树组件可展开多层结构。
export function normalizeNestedJsonValue(
  value: unknown,
  depth = 0,
  ancestors: WeakSet<object> = new WeakSet<object>(),
): unknown {
  if (depth >= NESTED_JSON_MAX_DEPTH) {
    return value
  }

  if (typeof value === 'string') {
    if (!looksLikeJsonContainer(value)) {
      return value
    }

    try {
      const parsed = JSON.parse(value)
      return normalizeNestedJsonValue(parsed, depth + 1, ancestors)
    } catch {
      return value
    }
  }

  if (Array.isArray(value)) {
    if (ancestors.has(value)) {
      return value
    }
    ancestors.add(value)
    const normalized = value.map((item) => normalizeNestedJsonValue(item, depth + 1, ancestors))
    ancestors.delete(value)
    return normalized
  }

  if (isPlainObject(value)) {
    if (ancestors.has(value)) {
      return value
    }
    ancestors.add(value)
    const normalized: Record<string, unknown> = {}
    Object.entries(value).forEach(([key, item]) => {
      normalized[key] = normalizeNestedJsonValue(item, depth + 1, ancestors)
    })
    ancestors.delete(value)
    return normalized
  }

  return value
}

export function parseJsonLikeValue(
  input: string | object | null | undefined,
): JsonLikeParseResult {
  if (input == null) {
    return { kind: 'empty', value: null }
  }

  if (typeof input === 'string') {
    if (input.trim() === '') {
      return { kind: 'empty', value: null }
    }

    try {
      return { kind: 'json', value: normalizeNestedJsonValue(JSON.parse(input)) }
    } catch {
      return { kind: 'text', value: input }
    }
  }

  return { kind: 'json', value: normalizeNestedJsonValue(input) }
}

function isCollapsible(value: unknown): value is Record<string, unknown> | unknown[] {
  return typeof value === 'object' && value !== null
}

// 对象键统一使用 JSON 字符串下标形式，避免点号等特殊字符导致路径冲突。
function toObjectKeyPath(parentPath: string, key: string): string {
  return `${parentPath}[${JSON.stringify(key)}]`
}

export function buildDefaultExpandedPaths(value: unknown): string[] {
  if (!isCollapsible(value)) {
    return []
  }

  const paths: string[] = []
  const ancestors = new WeakSet<object>()

  const walk = (currentValue: Record<string, unknown> | unknown[], currentPath: string): void => {
    // 仅检测当前 DFS 祖先链上的重复节点，避免循环同时保留共享引用路径。
    if (ancestors.has(currentValue)) {
      return
    }
    ancestors.add(currentValue)
    paths.push(currentPath)

    if (Array.isArray(currentValue)) {
      currentValue.forEach((item, index) => {
        if (isCollapsible(item)) {
          walk(item, `${currentPath}[${index}]`)
        }
      })
      ancestors.delete(currentValue)
      return
    }

    Object.keys(currentValue).forEach((key) => {
      const item = currentValue[key]
      if (isCollapsible(item)) {
        walk(item, toObjectKeyPath(currentPath, key))
      }
    })

    ancestors.delete(currentValue)
  }

  walk(value, '$')
  return paths
}

export function describeCollapsibleValue(value: unknown): string {
  if (Array.isArray(value)) {
    return `[${value.length} items]`
  }

  if (typeof value === 'object' && value !== null) {
    return `{${Object.keys(value).length} keys}`
  }

  return '-'
}

export function toInlineString(value: unknown, maxLength = 10): string {
  const text = String(value)
  if (text.length > maxLength) {
    return `${text.slice(0, maxLength)}...`
  }
  return text
}

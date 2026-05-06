import { describe, expect, it } from 'vitest'

import {
  buildMarkdownCacheKey,
  normalizeMarkdownInput,
  renderSafeMarkdown,
  renderSafeMarkdownWithCache,
  sanitizeRenderedLinks,
} from '../jsonTreeMarkdown'

describe('json tree markdown utilities', () => {
  it('keeps markdown while neutralizing raw html input', () => {
    const normalized = normalizeMarkdownInput('**Hello** <script>alert(1)</script> <b>World</b>')

    expect(normalized).toContain('**Hello**')
    expect(normalized).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
    expect(normalized).toContain('&lt;b&gt;World&lt;/b&gt;')

    const rendered = renderSafeMarkdown('**Hello** <b>World</b>')
    expect(rendered).toContain('<strong>Hello</strong>')
    expect(rendered).not.toContain('<b>')
  })

  it('preserves legitimate angle-bracket text content', () => {
    const rendered = renderSafeMarkdown('Map<String, Integer> and 1 < 2')

    expect(rendered).toContain('Map&lt;String, Integer&gt; and 1 &lt; 2')
  })

  it('blocks markdown image tags and dangerous embedded tags', () => {
    const renderedImage = renderSafeMarkdown('![avatar](https://example.com/a.png)')
    expect(renderedImage).not.toContain('<img')

    const sanitized = sanitizeRenderedLinks(
      '<p onclick="alert(1)">text</p><iframe src="https://evil.test"></iframe>',
    )
    expect(sanitized).toContain('<p>text</p>')
    expect(sanitized).not.toContain('onclick=')
    expect(sanitized).not.toContain('<iframe')
  })

  it('keeps safe https link', () => {
    const html = renderSafeMarkdown('[OpenAI](https://openai.com)')

    expect(html).toContain('href="https://openai.com"')
  })

  it('blocks javascript and data links', () => {
    const sanitized = sanitizeRenderedLinks(
      '<p><a href="javascript:alert(1)">x</a> <a href="data:text/html;base64,aaa">y</a></p>',
    )

    expect(sanitized).toContain('href="#"')
    expect(sanitized).not.toContain('javascript:')
    expect(sanitized).not.toContain('data:text/html')
  })

  it('adds target and rel on sanitized links', () => {
    const sanitized = sanitizeRenderedLinks('<a href="https://openai.com">OpenAI</a>')

    expect(sanitized).toContain('target="_blank"')
    expect(sanitized).toContain('rel="noopener noreferrer nofollow"')
  })

  it('uses cache key from path and content and reuses cached output', () => {
    const cache = new Map<string, string>()
    const path = '$["description"]'
    const content = '[OpenAI](https://openai.com)'
    const key = buildMarkdownCacheKey(path, content)

    const first = renderSafeMarkdownWithCache(cache, path, content)
    const second = renderSafeMarkdownWithCache(cache, path, content)

    expect(first).toBe(second)
    expect(cache.has(key)).toBe(true)
    expect(cache.get(key)).toBe(first)
  })
})
